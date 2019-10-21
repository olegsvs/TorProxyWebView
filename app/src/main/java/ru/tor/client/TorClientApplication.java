package ru.tor.client;

import android.app.Application;
import android.util.Log;

import com.msopentech.thali.android.toronionproxy.AndroidOnionProxyManager;
import com.msopentech.thali.toronionproxy.OnionProxyManager;

public class TorClientApplication extends Application {
    public static OnionProxyManager onionProxyManager = null;
    public String currentUrl = TorClient.MAIN_URL;
    private static TorClientApplication instance;

    public int getTorPort() {
        Log.i("IDDQD", "getTorPort: " + torPort);
        return torPort;
    }

    public int torPort = -1;
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        final String fileStorageLocation = "torfiles";

        Thread initThread = new Thread() {
            @Override
            public void run() {
                onionProxyManager =
                        new AndroidOnionProxyManager(TorClientApplication.this, fileStorageLocation);
            }
        };
        initThread.start();
    }
    public static TorClientApplication getInstance(){
        return instance;
    }

    public void setTorPort(int iPv4LocalHostSocksPort) {
        this.torPort = iPv4LocalHostSocksPort;
    }
}
