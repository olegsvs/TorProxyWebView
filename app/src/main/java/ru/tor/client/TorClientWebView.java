package ru.tor.client;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ProgressBar;

public class TorClientWebView extends WebView {
    private boolean init = false;

    public TorClientWebView(Context context) {
        super(context);
        setUpWebView();
    }

    public TorClientWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setUpWebView();
    }

    public TorClientWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setUpWebView();
    }

    @SuppressLint("SetJavaScriptEnabled")
    public void setUpWebView() {
        if (this.isInEditMode()) {
            return;
        }

        TorClientWebViewClient webClient;
        webClient = new TorClientWebViewClient(this.getContext());
        this.setWebViewClient(webClient);

        WebSettings webSettings = this.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setDomStorageEnabled(true);
        webSettings.setUserAgentString("Mozilla/5.0 (Linux; <Android Version>; <Build Tag etc.>) AppleWebKit/<WebKit Rev> (KHTML, like Gecko) Chrome/<Chrome Rev> Mobile Safari/<WebKit Rev>");
        android.webkit.CookieManager.getInstance().setAcceptCookie(true);
    }

    private void initProgressBar() {
        if (init) {
            return;
        }
        init = true;
        final ProgressBar progressBar = ((MainActivity) getContext()).findViewById(R.id.progressBar);
        this.setWebChromeClient(new WebChromeClient() {
            public void onProgressChanged(WebView view, int progress) {
                if (progress > 80) {
                    if (progressBar.getVisibility() == View.VISIBLE) {
                        progressBar.setVisibility(View.GONE);
                    }
                } else {
                    if (progressBar.getVisibility() == View.GONE) {
                        progressBar.setVisibility(View.VISIBLE);
                    }
                    progressBar.setProgress(progress);
                }
            }
        });
    }

    @Override
    public void loadUrl(String url) {
        super.loadUrl(url);
        initProgressBar();
    }
}