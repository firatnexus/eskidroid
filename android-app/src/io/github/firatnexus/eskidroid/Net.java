package io.github.firatnexus.eskidroid;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Basit HTTP yardımcıları. Ağ mantığı değişmedi: HttpURLConnection, koddan çağrılır.
 */
public final class Net {

    /** Arama / sürüm listesi / HEAD istekleri için ortak havuz. */
    public static final ExecutorService POOL = Executors.newFixedThreadPool(3);

    private static final String UA = "EskiDroid/1.2 (Android)";

    private Net() {
    }

    public static String get(String urlStr) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setInstanceFollowRedirects(true);
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", UA);
        try {
            int code = c.getResponseCode();
            if (code >= 300) throw new Exception("Sunucu " + code + " döndü");
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            InputStream in = c.getInputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            return bo.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    /** [uzunluk, sonDeğişiklik(ms)] döndürür; 404/hata olursa null. */
    public static long[] head(String urlStr) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
            c.setRequestMethod("HEAD");
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(10000);
            c.setReadTimeout(10000);
            c.setRequestProperty("User-Agent", UA);
            try {
                int code = c.getResponseCode();
                if (code / 100 != 2) return null;
                return new long[]{c.getContentLengthLong(), c.getLastModified()};
            } finally {
                c.disconnect();
            }
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean online(Context ctx) {
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm != null ? cm.getActiveNetworkInfo() : null;
            return ni != null && ni.isConnected();
        } catch (Exception e) {
            return false;
        }
    }
}
