package ru.tor.client;

import android.content.Context;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;

import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.client.HttpClient;
import cz.msebera.android.httpclient.client.methods.HttpGet;
import cz.msebera.android.httpclient.client.protocol.HttpClientContext;
import cz.msebera.android.httpclient.config.Registry;
import cz.msebera.android.httpclient.config.RegistryBuilder;
import cz.msebera.android.httpclient.conn.socket.ConnectionSocketFactory;
import cz.msebera.android.httpclient.impl.client.HttpClients;
import cz.msebera.android.httpclient.impl.conn.PoolingHttpClientConnectionManager;
import cz.msebera.android.httpclient.ssl.SSLContexts;

import static ru.tor.client.TorClientApplication.onionProxyManager;


class TorClientWebViewClient extends WebViewClient {

    private ProxyProcessor proxy;
    private Context activityContext;
    private static final String ENCODING_UTF_8 = "UTF-8";
    private static final String BOOK_FORMAT = "application/octet-stream";
    private static final String FB2_FORMAT = "application/zip";
    private static final String PDF_FORMAT = "application/pdf";
    private static final String CSS_FORMAT = "text/css";
    private static final String JS_FORMAT = "application/x-javascript";

    TorClientWebViewClient(Context c) {
        activityContext = c;
        proxy = new ProxyProcessor(c);
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        view.loadUrl(url);
        return true;
    }


    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        view.loadUrl(request.getUrl().toString());
        return true;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        /*WebResourceResponse response = proxy.getWebResourceResponse(request.getUrl(), request.getMethod(), request.getRequestHeaders());
        if (response == null) {
            return super.shouldInterceptRequest(view, request);
        } else {
            return response;
        }*/
        String requestString = request.getUrl().toString();
        return handleRequest(view, requestString);
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        TorClientApplication.getInstance().currentUrl = url;
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
        return handleRequest(view, url);
        /*WebResourceResponse response = proxy.getWebResourceResponse(Uri.parse(url), "GET", null);
        if (response == null) {
            return super.shouldInterceptRequest(view, url);
        } else {
            return response;
        }*/
    }


    public void onReceivedSslError(final WebView view, final SslErrorHandler handler, final SslError error) {
        // TODO Auto-generated method stub
        super.onReceivedSslError(view, handler, error);
        handler.proceed();
    }

    private HttpClient getNewHttpClient() {

        Registry<ConnectionSocketFactory> reg = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", new MyConnectionSocketFactory())
                .register("https", new MySSLConnectionSocketFactory(SSLContexts.createSystemDefault()))
                .build();
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(reg, new ProxyProcessor.FakeDnsResolver());
        return HttpClients.custom()
                .setConnectionManager(cm)
                .build();
    }

    private String inputStreamToString(InputStream is) {
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(is));
            StringBuilder total = new StringBuilder();
            for (String line; (line = r.readLine()) != null; ) {
                total.append(line).append('\n');
            }
            return total.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private WebResourceResponse handleRequest(WebView view, String url){
        try {
            HttpClient httpClient = getNewHttpClient();
            int port = onionProxyManager.getIPv4LocalHostSocksPort();
            InetSocketAddress socksaddr = new InetSocketAddress("127.0.0.1", port);
            HttpClientContext context = HttpClientContext.create();
            context.setAttribute("socks.address", socksaddr);
            HttpGet httpGet = new HttpGet(url);
            httpGet.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/72.0.3626.119 Safari/537.36");
            httpGet.setHeader("X-Compress", "null");
            HttpResponse httpResponse = httpClient.execute(httpGet, context);

            InputStream input = httpResponse.getEntity().getContent();
            String encoding = ENCODING_UTF_8;
            String mime = httpResponse.getEntity().getContentType().getValue();
            Log.d("surprise", "MyWebViewClient shouldInterceptRequest: " + mime);
            if (mime.equals(CSS_FORMAT)) {
                Log.d("surprise", "MyWebViewClient shouldInterceptRequest: load CSS");
                InputStream is = httpResponse.getEntity().getContent();
                // подключу нужные CSS простым объединением строк
                String origin = inputStreamToString(is);
                ByteArrayInputStream inputStream = new ByteArrayInputStream(origin.getBytes(encoding));
                return new WebResourceResponse(mime, ENCODING_UTF_8, inputStream);

            } else if (mime.equals(JS_FORMAT)) {
                InputStream is = httpResponse.getEntity().getContent();
                String origin = inputStreamToString(is);
                ByteArrayInputStream inputStream = new ByteArrayInputStream(origin.getBytes(encoding));
                return new WebResourceResponse(mime, ENCODING_UTF_8, inputStream);
            }

            if (mime.contains(";")) {
                String[] arr = mime.split(";");
                mime = arr[0];
                arr = arr[1].split("=");
                encoding = arr[1];
            }

            return new WebResourceResponse(mime, encoding, input);
        } catch (Exception e) {
            e.printStackTrace();
            try {
                return proxy.createExceptionError(e, url);
            } catch (UnsupportedEncodingException ex) {
                ex.printStackTrace();
            }
        }
        return super.shouldInterceptRequest(view, url);
    }
}