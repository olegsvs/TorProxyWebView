package ru.tor.client;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.WebView;

import java.io.IOException;

import static ru.tor.client.TorClientApplication.onionProxyManager;


@SuppressLint("SetJavaScriptEnabled")
public class MainActivity extends AppCompatActivity implements SwipeRefreshLayout.OnRefreshListener {
    private static final String TAG = "MainActivity";
    private SwipeRefreshLayout mRefresher;
    private WebView mWebView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        setContentView(R.layout.activity_main);

        super.onCreate(savedInstanceState);
        mRefresher = findViewById(R.id.refreshView);
        mRefresher.setOnRefreshListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        new TorProgressTask(MainActivity.this).execute();
        initWebView();
    }


    @Override
    public void onRefresh() {
        mWebView.reload();
        mRefresher.setRefreshing(false);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_item_home:
                if (mWebView != null)
                    mWebView.loadUrl(TorClient.MAIN_URL);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void initWebView() {
        /*
         * That function looks damn bad. But we need to call onionProxyManager.isRunning from non UI thread
         * and then we need to call myWebView.loadUrl from UI thread...
         * */
        mWebView = MainActivity.this.findViewById(R.id.myWebView);
        final String loaded = mWebView.getOriginalUrl();
        final TorClientApplication appState = ((TorClientApplication) getApplicationContext());

        Thread checkTorThread = new Thread() {
            @Override
            public void run() {

                try {
                    while (onionProxyManager == null || !onionProxyManager.isRunning()) {
                        Thread.sleep(90);
                    }
                } catch (InterruptedException | IOException e) {
                    e.printStackTrace();
                }
                if (loaded == null) {
                    MainActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mWebView.loadUrl(appState.currentUrl);
                        }
                    });
                }

            }
        };
        checkTorThread.start();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                WebView myWebView = findViewById(R.id.myWebView);
                assert myWebView != null;
                if (myWebView.canGoBack()) {
                    myWebView.goBack();
                } else {
                    finish();
                }
                return true;
            }

        }
        return super.onKeyDown(keyCode, event);
    }

}

