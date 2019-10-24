package ru.tor.client;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.webkit.CookieSyncManager;
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

    @Override
    public void postUrl(String url, byte[] postData) {
        Log.i("IDDQD", "postUrl: " + url);
        super.postUrl(url, postData);
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
        webSettings.setAppCacheEnabled(true);
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setLoadsImagesAutomatically(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
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