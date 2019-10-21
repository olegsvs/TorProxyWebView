package ru.tor.client;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Proxy;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Parcelable;
import android.util.ArrayMap;
import android.util.Log;
import android.webkit.WebView;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static ru.tor.client.TorClientApplication.onionProxyManager;

public class TorProgressTask extends AsyncTask<String, String, Boolean> {
    private static final String TAG = "TorProgressTask";
    private ProgressDialog torStartProgress;
    private MainActivity activity;

     TorProgressTask(MainActivity activity) {
        this.activity = activity;
    }


    protected void onPreExecute() {
        Log.d(TAG, "onPreExecute");
        torStartProgress = new ProgressDialog(activity);
        torStartProgress.setMessage("Starting Tor... Please be patient");
        torStartProgress.setIndeterminate(false);
        torStartProgress.setCancelable(false);
        torStartProgress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        torStartProgress.show();
    }

    @Override
    protected void onPostExecute(final Boolean success) {

        if (!success) {
            AlertDialog.Builder builder1 = new AlertDialog.Builder(activity);
            builder1.setMessage("Failed to load Tor. Retry?");
            builder1.setCancelable(true);

            builder1.setPositiveButton(
                    android.R.string.yes,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            new TorProgressTask(activity).execute();
                            dialog.cancel();
                        }
                    });

            builder1.setNegativeButton(
                    android.R.string.no,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                        }
                    });

            AlertDialog alert11 = builder1.create();
            alert11.show();
        }
        TorClientWebView myWebView = activity.findViewById(R.id.myWebView);
        TorClientApplication appState = ((TorClientApplication) activity.getApplicationContext());
        myWebView.loadUrl(appState.currentUrl);
        Log.d(TAG, "Opening: " + appState.currentUrl);

        //for crash http://stackoverflow.com/questions/22924825/view-not-attached-to-window-manager-crash
        // issue #29
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            if (activity.isDestroyed()) {
                return;
            }
        } else if (activity.isFinishing()) {
            return;
        }
        if (torStartProgress != null && torStartProgress.isShowing())
            torStartProgress.dismiss();
    }

    @Override
    protected void onProgressUpdate(String... log) {
        super.onProgressUpdate(log);
        Log.e(TAG, "logging");
        torStartProgress.setMessage("Initializing Tor..." + log[0]);
    }

    @Override
    protected Boolean doInBackground(final String... args) {
        try {
            while (onionProxyManager==null) {
                Thread.sleep(90);
            }
            if (onionProxyManager.isRunning()) {
                return true;
            }
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
        }
        Thread torThread = new Thread() {
            @Override
            public void run() {
                try {
                    //boolean ok = onionProxyManager.startWithRepeat(totalSecondsPerTorStartup, totalTriesPerTorStartup);
                    boolean ok = onionProxyManager.installAndStartTorOp();
                    if (!ok) {
                        Log.e(TAG, "Couldn't start Tor!");
                    }

                } catch (InterruptedException | IOException e) {
                    e.printStackTrace();
                }
            }
        };
        torThread.start();

        Thread torChecker = new Thread() {
            @Override
            public void run() {
                try {
                    while (!onionProxyManager.isRunning()) {
                        Thread.sleep(90);
                    }
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        };
        torChecker.start();

        try {
            int timePassed = 0;
            String log = null;
            while (torChecker.isAlive()) {
                Thread.sleep(100);
                timePassed += 100;
                String logNew = onionProxyManager.getLastLog();
                if (logNew.length() > 1 && !logNew.equals(log)) {
                    publishProgress(logNew);
                    log = logNew;
                }
                if (timePassed > 1000 * 60 * 2) {
                    return false;
                }
            }

            Log.v(TAG, "Tor initialized on port " + onionProxyManager.getIPv4LocalHostSocksPort());
            try {
//                setLollipopWebViewProxy(activity.getApplicationContext(), "localhost", onionProxyManager.getIPv4LocalHostSocksPort());
                //WebkitProxy.setProxy(TorClientApplication.class.getName(), activity.getApplicationContext(), null, "localhost", onionProxyManager.getIPv4LocalHostSocksPort());
            } catch (Exception e) {
                Log.i("IDDQD", "doInBackground: " + e.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return true;
    }


    /**
     * Set Proxy for Android 5.0 and above.
     */
    @SuppressWarnings("all")
    private static boolean setLollipopWebViewProxy(Context appContext, String host, int port) {
        System.setProperty("http.proxyHost", host);
        System.setProperty("http.proxyPort", port + "");
        System.setProperty("https.proxyHost", host);
        System.setProperty("https.proxyPort", port + "");
        try {
            Class applictionCls = Class.forName("android.app.Application");
            Field loadedApkField = applictionCls.getDeclaredField("mLoadedApk");
            loadedApkField.setAccessible(true);
            Object loadedApk = loadedApkField.get(appContext);
            Class loadedApkCls = Class.forName("android.app.LoadedApk");
            Field receiversField = loadedApkCls.getDeclaredField("mReceivers");
            receiversField.setAccessible(true);
            ArrayMap receivers = (ArrayMap) receiversField.get(loadedApk);
            for (Object receiverMap : receivers.values()) {
                for (Object rec : ((ArrayMap) receiverMap).keySet()) {
                    Class clazz = rec.getClass();
                    if (clazz.getName().contains("ProxyChangeListener")) {
                        Method onReceiveMethod = clazz.getDeclaredMethod("onReceive", Context.class, Intent.class);
                        Intent intent = new Intent(Proxy.PROXY_CHANGE_ACTION);
                        /***** In Lollipop, ProxyProperties went public as ProxyInfo *****/
                        final String CLASS_NAME = "android.net.ProxyInfo";
                        Class cls = Class.forName(CLASS_NAME);
                        /***** ProxyInfo lacks constructors, use the static buildDirectProxy method instead *****/
                        Method buildDirectProxyMethod = cls.getMethod("buildDirectProxy", String.class, Integer.TYPE);
                        Object proxyInfo = buildDirectProxyMethod.invoke(cls, host, port);
                        intent.putExtra("proxy", (Parcelable) proxyInfo);
                        onReceiveMethod.invoke(rec, appContext, intent);
                    }
                }
            }
        } catch (Exception e) {
            Log.e("IDDQD", "Setting proxy with >= 5.0 API failed with " + e.getClass().getName() + ": " + e.getMessage());
            return false;
        }
        Log.d("IDDQD", "Setting proxy with >= 5.0 API successful!");
        return true;
    }

    private static Object getFieldValueSafely(Field field, Object classInstance) throws IllegalArgumentException,
            IllegalAccessException {
        boolean oldAccessibleValue = field.isAccessible();
        field.setAccessible(true);
        Object result = field.get(classInstance);
        field.setAccessible(oldAccessibleValue);
        return result;
    }
}
