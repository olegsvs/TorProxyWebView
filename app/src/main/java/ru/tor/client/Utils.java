package ru.tor.client;

import android.net.Uri;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cz.msebera.android.httpclient.NameValuePair;
import cz.msebera.android.httpclient.client.entity.UrlEncodedFormEntity;
import cz.msebera.android.httpclient.message.BasicNameValuePair;

public class Utils {

    private static final String TAG = "Utils";

    public static String md5(final String s) {
        final String MD5 = "MD5";
        try {
            // Create MD5 Hash
            MessageDigest digest = java.security.MessageDigest
                    .getInstance(MD5);
            digest.update(s.getBytes());
            byte[] messageDigest = digest.digest();

            // Create Hex String
            StringBuilder hexString = new StringBuilder();
            for (byte aMessageDigest : messageDigest) {
                String h = Integer.toHexString(0xFF & aMessageDigest);
                while (h.length() < 2)
                    h = "0" + h;
                hexString.append(h);
            }
            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return "";
    }


    /*public static String[] authHeader() {
        String[] result = new String[2];
        result[0] = "Chrome-Proxy";
        String authValue = "ac4500dd3b7579186c1b0620614fdb1f7d61f944";
        String timestamp = Long.toString(System.currentTimeMillis()).substring(0, 10);
        String[] chromeVersion = {"49", "0", "2623", "87"};

        String sid = (timestamp + authValue + timestamp);

        sid = Utils.md5(sid);
        result[1] = "ps=" + timestamp + "-" + Integer.toString((int) (Math.random() * 1000000000)) +
                "-" + Integer.toString((int) (Math.random() * 1000000000)) +
                "-" + Integer.toString((int) (Math.random() * 1000000000)) +
                ", sid=" + sid + ", b=" + chromeVersion[2] + ", p=" + chromeVersion[3] + ", c=win";
        return result;
    }*/


    static String convertStreamToString(java.io.InputStream is, String encoding) {
        //java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        //return s.hasNext() ? s.next() : "";
        BufferedReader r = null;
        try {
            if (encoding != null)
                r = new BufferedReader(new InputStreamReader(is, encoding));
            else
                r = new BufferedReader(new InputStreamReader(is));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        StringBuilder total = new StringBuilder();
        String line;
        try {
            if (r != null) {
                while ((line = r.readLine()) != null) {
                    total.append("\n").append(line);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return total.toString();
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

        Log.d(TAG, "Getting URL parameters from URL " + url.toString());
        //String urlStr = null;

        Map<String, String> map = getQueryMap(url.toString());
        for (Map.Entry<String, String> entry : map.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();
            try {
                value = URLDecoder.decode(value, "windows-1251");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            Log.d(TAG, "converting parameter " + name + " to post, value " + value);
            paramsArray.add(new BasicNameValuePair(name, value));
        }
        try {
            return new UrlEncodedFormEntity(paramsArray, "windows-1251");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return null;
    }

     static void copyFile(String inputPath, String inputFile, String outputPath) {

        InputStream in = null;
        OutputStream out = null;
        try {

            //create output directory if it doesn't exist
            File dir = new File(outputPath);
            if (!dir.exists()) {
                dir.mkdirs();
            }


            in = new FileInputStream(inputPath + inputFile);
            out = new FileOutputStream(outputPath + inputFile);

            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();
            in = null;

            // write the output file (You have now copied the file)
            out.flush();
            out.close();
            out = null;

        } catch (FileNotFoundException fnfe1) {
            Log.e(TAG, fnfe1.getMessage());
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }

    }
}
