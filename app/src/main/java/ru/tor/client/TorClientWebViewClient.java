package ru.tor.client;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.NameValuePair;
import cz.msebera.android.httpclient.client.HttpClient;
import cz.msebera.android.httpclient.client.entity.UrlEncodedFormEntity;
import cz.msebera.android.httpclient.client.methods.HttpPost;
import cz.msebera.android.httpclient.client.protocol.HttpClientContext;
import cz.msebera.android.httpclient.config.Registry;
import cz.msebera.android.httpclient.config.RegistryBuilder;
import cz.msebera.android.httpclient.conn.DnsResolver;
import cz.msebera.android.httpclient.conn.socket.ConnectionSocketFactory;
import cz.msebera.android.httpclient.impl.client.HttpClients;
import cz.msebera.android.httpclient.impl.conn.PoolingHttpClientConnectionManager;
import cz.msebera.android.httpclient.message.BasicNameValuePair;
import cz.msebera.android.httpclient.ssl.SSLContexts;

import static ru.tor.client.TorClientApplication.onionProxyManager;


class TorClientWebViewClient extends WebViewClient {

    private Context activityContext;
    private java.net.CookieManager cookieManager;
    private static final String MIME_TEXT_HTML = "text/html";
    private static final String ENCODING_UTF_8 = "UTF-8";

    TorClientWebViewClient(Context c) {
        activityContext = c;
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        return handleRequest(request);
//        super.shouldInterceptRequest()
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        TorClientApplication.getInstance().currentUrl = url;
    }

    static class FakeDnsResolver implements DnsResolver {
        @Override
        public InetAddress[] resolve(String host) throws UnknownHostException {
            // Return some fake DNS record for every request, we won't be using it
            return new InetAddress[]{InetAddress.getByAddress(new byte[]{1, 1, 1, 1})};
        }
    }

    private HttpClient getNewHttpClient() {

        Registry<ConnectionSocketFactory> reg = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", new MyConnectionSocketFactory())
                .register("https", new MySSLConnectionSocketFactory(SSLContexts.createSystemDefault()))
                .build();
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(reg, new FakeDnsResolver());
        return HttpClients.custom()
                .setConnectionManager(cm)
                .build();
    }


    private static Map<String, String> getQueryMap(String query) {
        String[] params = query.split("&");
        Map<String, String> map = new HashMap<>();
        for (String param : params) {
            String name = param.split("=")[0];
            String value = param.split("=")[1];
            map.put(name, value);
        }
        return map;
    }

    public static UrlEncodedFormEntity get2post(Uri url) {
        Set<String> params = url.getQueryParameterNames();
        if (params.isEmpty()) {
            return null;
        }

        List<NameValuePair> paramsArray = new ArrayList<>();

        Log.d("IDDQD", "Getting URL parameters from URL " + url.toString());
        //String urlStr = null;

        Map<String, String> map = getQueryMap(url.toString());
        for (Map.Entry<String, String> entry : map.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();
            try {
                value = URLDecoder.decode(value, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            Log.d("IDDQD", "converting parameter " + name + " to post, value " + value);
            paramsArray.add(new BasicNameValuePair(name, value));
        }
        try {
            return new UrlEncodedFormEntity(paramsArray, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return null;
    }

    private WebResourceResponse handleRequest(WebResourceRequest request) {
        Log.i("IDDQD", "####");
        Log.i("IDDQD", "####");
        Log.i("IDDQD", "####");
        Log.i("IDDQD", "####");
        String url = request.getUrl().toString().split("#")[0];
        Log.i("IDDQD3", "handleRequest: URL " + url + " METHOD " + request.getMethod());
        try {

            HttpClient httpClient = getNewHttpClient();
            int port = onionProxyManager.getIPv4LocalHostSocksPort();
            InetSocketAddress socksaddr = new InetSocketAddress("127.0.0.1", port);
            HttpClientContext context = HttpClientContext.create();
            context.setAttribute("socks.address", socksaddr);
            HttpResponse httpResponse = null;


            UrlEncodedFormEntity params = null;
            String requestUrl = url.toString();

            if (request.getMethod().equals("post")) {
                Log.d("IDDQD", "ProxyProcessor getWebResourceResponse: emulate post");
                //we need to emulate POST request
                int queryPart = requestUrl.indexOf("?");
                if (queryPart != -1) {
                    requestUrl = requestUrl.substring(0, queryPart);
                }
                params = get2post(request.getUrl());
            }

            HttpPost httpPost = new HttpPost(requestUrl);
//            httpPost.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 6.1; rv:60.0) Gecko/20100101 Firefox/60.0");
//            httpPost.setHeader("X-Compress", "null");
            if (params != null) {
                httpPost.setEntity(params);
            }

            if (request.getRequestHeaders() != null) {
                for (Map.Entry<String, String> entry : request.getRequestHeaders().entrySet()) {
                    httpPost.setHeader(entry.getKey(), entry.getValue());
                    Log.i("IDDQD2", "handleRequest: " + entry.getKey() + " = " + entry.getValue());
                }
            }
            httpResponse = httpClient.execute(httpPost, context);

            Header[] all = httpResponse.getAllHeaders();
            for (Header header1 : all) {
                Log.d("IDDQD", "LOGIN HEADER: " + header1.getName() + " : " + header1.getValue());
            }

            int responseCode = httpResponse.getStatusLine().getStatusCode();
            String responseMessage = httpResponse.getStatusLine().getReasonPhrase();
            Log.i("IDDQD", "handleRequest: first responseCode " + responseCode);
            Log.i("IDDQD", "handleRequest: first responseMessage " + responseMessage);

            InputStream input = httpResponse.getEntity().getContent();
            String encoding = ENCODING_UTF_8;
            String mime = httpResponse.getEntity().getContentType().getValue();
            Log.i("IDDQD", "handleRequest: first MIME " + mime);
            String mimeType = "text/plain";
            if (mime != null && !mime.isEmpty()) {
                mimeType = mime.split("; ")[0];
            }
            Map<String, String> responseHeaders = new HashMap<>();
            for (Header key : httpResponse.getAllHeaders()) {
                responseHeaders.put(key.getName(), key.getValue());
            }
            Log.i("IDDQD", "handleRequest: first HEADERS " + responseHeaders.toString());
            return new WebResourceResponse(mimeType, encoding, responseCode, responseMessage, responseHeaders, input);
        } catch (Exception e) {
            e.printStackTrace();
            try {
                Log.i("IDDQD", "handleRequest: " + e.toString() + " URL: " + url);
                return createExceptionError(e, url);
            } catch (UnsupportedEncodingException ex) {
                ex.printStackTrace();
            }
        }
        return new WebResourceResponse("text/plain", "UTF-8", 204, "No Content", new HashMap<String, String>(), new ByteArrayInputStream(new byte[]{}));
    }


    private WebResourceResponse createExceptionError(Exception e, Uri url) throws UnsupportedEncodingException {
        return createExceptionError(e, url.toString());
    }

    private WebResourceResponse createExceptionError(Exception e, String url) throws UnsupportedEncodingException {
        String msgText = makeError(e, url);
        return createFromString(msgText);
    }

    private WebResourceResponse createResponseError(String errorMessage, String url, String errorCode) throws UnsupportedEncodingException {
        String msgText = makeError(errorMessage, url, errorCode);
        return createFromString(msgText);
    }

    private WebResourceResponse createFromString(String buf) throws UnsupportedEncodingException {
        return createFromString(buf, ENCODING_UTF_8);
    }

    private WebResourceResponse createFromString(String buf, String encoding) throws UnsupportedEncodingException {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(buf.getBytes(encoding));
        return createFromString(MIME_TEXT_HTML, encoding, inputStream);
    }

    private WebResourceResponse createFromString(String mime, String encoding, InputStream inputStream) {
        return new WebResourceResponse(mime, encoding, inputStream);
    }

    private String makeError(Exception e, String url) {
        return makeError(e.getMessage(), url, String.valueOf(e.hashCode()));
    }

    private String makeError(String errorMessage, String url, String errorCode) {
        Log.d("IDDQD", "Url: " + url);
        Log.d("IDDQD", "Response code: " + errorCode);
        Log.d("IDDQD", "Response message: " + errorMessage);

        return "Что-то пошло не так:<br>" + "Адрес: " + url + "<br><br>" +
                "Сообщение: " + errorMessage + "<br><br>" +
                "Код: " + errorCode + "<br><br>" +
                "Вы можете <a href=\"javascript:location.reload(true)\">Обновить страницу</a>" +
                "или <a href=\"" + TorClient.MAIN_URL + "\">вернуться на главную</a>";
    }
}