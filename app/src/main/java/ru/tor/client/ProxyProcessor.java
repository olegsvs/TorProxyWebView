package ru.tor.client;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.client.HttpClient;
import cz.msebera.android.httpclient.client.entity.UrlEncodedFormEntity;
import cz.msebera.android.httpclient.client.methods.HttpGet;
import cz.msebera.android.httpclient.client.methods.HttpPost;
import cz.msebera.android.httpclient.client.protocol.HttpClientContext;
import cz.msebera.android.httpclient.config.Registry;
import cz.msebera.android.httpclient.config.RegistryBuilder;
import cz.msebera.android.httpclient.conn.DnsResolver;
import cz.msebera.android.httpclient.conn.socket.ConnectionSocketFactory;
import cz.msebera.android.httpclient.impl.client.HttpClients;
import cz.msebera.android.httpclient.impl.conn.PoolingHttpClientConnectionManager;
import cz.msebera.android.httpclient.ssl.SSLContexts;

import static ru.tor.client.TorClientApplication.onionProxyManager;

class ProxyProcessor {

    private static final String VIEW_TAG = "TorClientWebView";
    private static final String MIME_TEXT_HTML = "text/html";
    private static final String MIME_TEXT_CSS = "text/css";
    private static final String ENCODING_UTF_8 = "UTF-8";
    private static final String ENCODING_WINDOWS_1251 = "WINDOWS-1251";
    private static final String DL_LINK = "dl.php?t=";
    private final Context context;

    ProxyProcessor(Context context) {
        this.context = context;
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

    WebResourceResponse getWebResourceResponse(Uri url, String method, Map<String, String> headers) {

        Log.d(VIEW_TAG, "Request for url: " + url + " intercepted");

        if (url.getHost() == null) {
            Log.d(VIEW_TAG, "No url or host provided, better let webView deal with it");
            return null;
        }


        try {

            HttpResponse response;
            UrlEncodedFormEntity params = null;
            String requestUrl = url.toString();

            if (url.toString().contains("convert_post=1") || method.equals("post")) {
                Log.d("surprise", "ProxyProcessor getWebResourceResponse: emulate post");
                //we need to emulate POST request
                Log.d(VIEW_TAG, "It is a post request!");
                int queryPart = requestUrl.indexOf("?");
                if (queryPart != -1) {
                    requestUrl = requestUrl.substring(0, queryPart);
                }
                params = Utils.get2post(url);
            }

            try {
                response = executeRequest(requestUrl, headers, params);
                Log.i("IDDQD", "getWebResourceResponse: RESPONSE " + response.toString());
            } catch (Exception e) {
                Log.i("IDDQD", "getWebResourceResponse: ERROR " + e.toString());
                return createExceptionError(e, url);
            }

            int responseCode = response.getStatusLine().getStatusCode();
            String responseMessage = response.getStatusLine().getReasonPhrase();

            if (responseCode == 200) {
                InputStream input = response.getEntity().getContent();
                String encoding = null;
                if (response.getEntity().getContentEncoding() != null) {
                    encoding = response.getEntity().getContentEncoding().getValue();
                }
                Log.d(VIEW_TAG, "data ok");
                InputStream inputStream;

                if ("gzip".equals(encoding)) {
                    inputStream = (new GZIPInputStream(input));
                } else {
                    inputStream = input;
                }

                Log.d(VIEW_TAG, "connection encoding : " + encoding);
                String mime = response.getEntity().getContentType().getValue();
                Log.d(VIEW_TAG, "mime full: " + mime);
                if (mime.contains(";")) {
                    String[] arr = mime.split(";");
                    mime = arr[0];
                    arr = arr[1].split("=");
                    encoding = arr[1];
                    Log.d(VIEW_TAG, "encoding from mime: " + encoding);
                }
                if (encoding == null || encoding.equals("gzip")) {
                    encoding = ENCODING_UTF_8;
                }

                Log.d(VIEW_TAG, "clean mime: " + mime);


                Log.d(VIEW_TAG, "encoding final: " + encoding);

                return createFromString(mime, encoding, inputStream);
            } else if (responseCode == 302) {
                // перенаправлю на нужный адрес
                try {
                    Log.d("surprise", "ProxyProcessor getWebResourceResponse: " + headers.get("Location"));
                } catch (Exception ignored) {}
            } else {
                return createResponseError(responseMessage, url.toString(), String.valueOf(responseCode));
            }
        } catch (Exception e) {
            Log.d(VIEW_TAG, "Error fetching URL " + url + ":");
            e.printStackTrace();
        }
        return null;
    }

    private String makeError(Exception e, String url) {
        return makeError(e.getMessage(), url, String.valueOf(e.hashCode()));
    }

    private String makeError(String errorMessage, String url, String errorCode) {
        Log.d(VIEW_TAG, "Url: " + url);
        Log.d(VIEW_TAG, "Response code: " + errorCode);
        Log.d(VIEW_TAG, "Response message: " + errorMessage);

        return "Что-то пошло не так:<br>" + "Адрес: " + url + "<br><br>" +
                "Сообщение: " + errorMessage + "<br><br>" +
                "Код: " + errorCode + "<br><br>" +
                "Вы можете <a href=\"javascript:location.reload(true)\">Обновить страницу</a>" +
                "или <a href=\"" + TorClient.MAIN_URL + "\">вернуться на главную</a>";
    }


    private HttpResponse executeRequest(String url, Map<String, String> headers, UrlEncodedFormEntity params) throws IOException {
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

        return httpClient.execute(request, clientContext);

    }

    private WebResourceResponse createExceptionError(Exception e, Uri url) throws UnsupportedEncodingException {
        return createExceptionError(e, url.toString());
    }

    WebResourceResponse createExceptionError(Exception e, String url) throws UnsupportedEncodingException {
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
        return createFromString(ProxyProcessor.MIME_TEXT_HTML, encoding, inputStream);
    }

    private WebResourceResponse createFromString(String mime, String encoding, InputStream inputStream) {
        return new WebResourceResponse(mime, encoding, inputStream);
    }
}