package ru.tor.client;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.HttpHeaders;
import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.client.HttpClient;
import cz.msebera.android.httpclient.client.entity.UrlEncodedFormEntity;
import cz.msebera.android.httpclient.client.methods.HttpPost;
import cz.msebera.android.httpclient.client.protocol.HttpClientContext;
import cz.msebera.android.httpclient.config.Registry;
import cz.msebera.android.httpclient.config.RegistryBuilder;
import cz.msebera.android.httpclient.conn.DnsResolver;
import cz.msebera.android.httpclient.conn.socket.ConnectionSocketFactory;
import cz.msebera.android.httpclient.impl.client.HttpClients;
import cz.msebera.android.httpclient.impl.client.LaxRedirectStrategy;
import cz.msebera.android.httpclient.impl.conn.PoolingHttpClientConnectionManager;
import cz.msebera.android.httpclient.ssl.SSLContexts;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

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
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        view.loadUrl(request.getUrl().toString());
        return true;
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        WebResourceResponse response = getWebResourceResponse(request.getUrl(), request.getMethod(), request.getRequestHeaders());
        if (response == null) {
            return super.shouldInterceptRequest(view, request);
        } else {
            return response;
        }
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
                .setRedirectStrategy(new LaxRedirectStrategy())
                .setConnectionManager(cm)
                .build();
    }

    WebResourceResponse getWebResourceResponse(Uri url, String method, Map<String, String> headers) {

        Log.d("IDDQD", "Request for url: " + url + " intercepted method " + method);
        try {
            if (url.getHost() == null) {
                Log.d("IDDQD", "No url or host provided, better let webView deal with it");
                return null;
            }


            HttpResponse response;
            UrlEncodedFormEntity params = null;
            String requestUrl = url.toString();
            if (url.toString().contains("convert_post=1") || method.equalsIgnoreCase("post")) {
                Log.d("IDDQD", "ProxyProcessor getWebResourceResponse: emulate post");
                //we need to emulate POST request
                Log.d("IDDQD", "It is a post request!");
                int queryPart = requestUrl.indexOf("?");
                if (queryPart != -1) {
                    requestUrl = requestUrl.substring(0, queryPart);
                }
                params = Utils.get2post(url);
            }
            response = executeRequest(requestUrl, headers, params, url.getHost());

            int responseCode = response.getStatusLine().getStatusCode();
            String responseMessage = response.getStatusLine().getReasonPhrase();
            Log.i("IDDQD", "getWebResourceResponse: responseCode " + responseCode + " responseMessage " + responseMessage);
            Header[] cookies = response.getHeaders("set-cookie");
            if (cookies.length > 0) {
                String value = cookies[0].getValue();
                value = value.substring(0, value.indexOf(";"));
                String authCookie = value.trim();
                CookieManager cookieManager = CookieManager.getInstance();
                cookieManager.setCookie(url.getHost(), authCookie);
                Log.d("IDDQD", "=== Auth cookie: ==='" + value + "'");
            } else {
                Log.d("IDDQD", "No cookie received!!!");
            }

            InputStream input = response.getEntity().getContent();
//            if (responseCode == 200) {
            String encoding = null;
            if (response.getEntity().getContentEncoding() != null) {
                encoding = response.getEntity().getContentEncoding().getValue();
            }
            Log.d("IDDQD", "data ok");
            InputStream inputStream;

            if ("gzip".equals(encoding)) {
                inputStream = (new GZIPInputStream(input));
            } else {
                inputStream = input;
            }

            Log.d("IDDQD", "connection encoding : " + encoding);
            String mime = null;
            if (response.getEntity() != null && response.getEntity().getContentType() != null && response.getEntity().getContentType().getValue() != null) {
                mime = response.getEntity().getContentType().getValue();
            }
            Log.d("IDDQD", "mime full: " + mime);
            if (mime.contains(";")) {
                String[] arr = mime.split(";");
                mime = arr[0];
                arr = arr[1].split("=");
                encoding = arr[1];
                Log.d("IDDQD", "encoding from mime: " + encoding);
            }

            Log.d("IDDQD", "clean mime: " + mime);
            encoding = ENCODING_UTF_8;

            Log.d("IDDQD", "encoding final: " + encoding);

            //conversions for rutacker

            encoding = ENCODING_UTF_8;
            String data = Utils.convertStreamToString(inputStream, encoding);

            //convert POST data to GET data to be able ro intercept it
            String replace = "<form(.*?)method=\"post\"(.*?)>";
            String replacement = "<form$1method=\"get\"$2><input type=\"hidden\" name=\"convert_post\" value=1>";
            data = data.replaceAll(replace, replacement);

            inputStream = new ByteArrayInputStream(data.getBytes(encoding));


            return createFromString(mime, encoding, inputStream);
//            }
            /*else if (responseCode == 301 || responseCode == 302) {
                String redirect = response.getFirstHeader("Location").getValue();
                String html = String.format("<html><body onload=\"timer=setTimeout(function(){ window.location='%s';}, 300)\">" +
                        "you will be redirected soon" +
                        "</body></html>", redirect);
                return new WebResourceResponse("text/html", Charset.defaultCharset().name(), new ByteArrayInputStream(html.getBytes()));}*/ //else {
//                return createResponseError(responseMessage, url.toString(), String.valueOf(responseCode));
                /*String[] contentTypeParts = response.getEntity().getContentType().getValue().split(";[ ]*");
                String encoding2 = contentTypeParts.length > 1 && contentTypeParts[1].startsWith("charset=") ? contentTypeParts[1].replaceFirst("charset=", "") : Charset.defaultCharset().name();
                return new WebResourceResponse(contentTypeParts[0], encoding2, input);
            }*/
        } catch (Exception e) {
            Log.d("IDDQD", "Error fetching URL " + url + ":");
            e.printStackTrace();
            try {
                return createExceptionError(e, url);
            } catch (UnsupportedEncodingException ex) {
                ex.printStackTrace();
            }
        }
        return null;
    }

    private HttpResponse executeRequest(String url, Map<String, String> headers, UrlEncodedFormEntity params, String host) throws IOException {
        HttpClient httpClient = getNewHttpClient();
        int port = onionProxyManager.getIPv4LocalHostSocksPort();
        InetSocketAddress socketAddress = new InetSocketAddress("127.0.0.1", port);
        HttpClientContext clientContext = HttpClientContext.create();
        clientContext.setAttribute("socks.address", socketAddress);

        HttpPost request = new HttpPost(url);

        if (params != null) {
            request.setEntity(params);
        }
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                request.setHeader(entry.getKey(), entry.getValue());
            }
        }

        request.setHeader(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
        request.setHeader(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate, sdch");
        request.setHeader(HttpHeaders.ACCEPT_LANGUAGE, "ru,en-US;q=0.8,en;q=0.6");
        CookieManager cookieManager = CookieManager.getInstance();
        String authCookie = cookieManager.getCookie(host);
        if (authCookie != null) {
            request.setHeader("Cookie", authCookie);
            Log.d("IDDQD", "cookie sent:" + authCookie);
        }
        //request.setHeader(HttpHeaders.REFERER, "http://rutracker.org/forum/index.php");
        //request.setHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/49.0.2623.87 Safari/537.36");


        return httpClient.execute(request, clientContext);

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