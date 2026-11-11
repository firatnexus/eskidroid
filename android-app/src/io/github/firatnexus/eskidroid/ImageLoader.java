package io.github.firatnexus.eskidroid;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Uygulama ikonları ve küçük görseller: HttpURLConnection + bellek (LruCache)
 * ve disk önbelleği, inSampleSize ile küçültme, hata durumunda harf avatarı.
 * Liste asla görsel yüklenmesi bekletilmez.
 */
public final class ImageLoader {

    private static ImageLoader s;

    public static synchronized ImageLoader get(Context ctx) {
        if (s == null) s = new ImageLoader(ctx.getApplicationContext());
        return s;
    }

    private final LruCache<String, Bitmap> mem;
    private final File disk;
    private final ExecutorService ex = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());

    private ImageLoader(Context ctx) {
        final int maxKb = Math.max(4 * 1024, (int) (Runtime.getRuntime().maxMemory() / 1024 / 8));
        mem = new LruCache<String, Bitmap>(maxKb) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return Math.max(1, value.getByteCount() / 1024);
            }
        };
        disk = new File(ctx.getCacheDir(), "imgcache");
        if (!disk.exists()) disk.mkdirs();
    }

    public File cacheDir() {
        return disk;
    }

    public void clearCache() {
        File[] fs = disk.listFiles();
        if (fs != null) {
            for (File f : fs) f.delete();
        }
        mem.evictAll();
    }

    public long cacheBytes() {
        long t = 0;
        File[] fs = disk.listFiles();
        if (fs != null) {
            for (File f : fs) t += f.length();
        }
        return t;
    }

    /**
     * Görseli yükler: önce bellek, sonra disk, sonra ağ. Yüklenirken #191919
     * düz dolgu, hata durumunda harf avatarı gösterilir. Çevrimdışı: yalnız önbellek.
     */
    public void load(final String url, final int sizeDp, final ImageView view, final String letterHint) {
        if (view == null) return;
        final String hint = letterHint == null ? "?" : letterHint;
        final String key = (url == null || url.isEmpty()) ? "letter:" + hint : url;
        view.setTag(key);
        Bitmap b = mem.get(key);
        if (b != null) {
            view.setImageBitmap(b);
            return;
        }
        view.setImageDrawable(new ColorDrawable(Ui.CARD));
        if (url == null || url.isEmpty()) {
            Bitmap l = Ui.letterAvatar(hint, Math.max(1, Ui.dp(48)));
            mem.put(key, l);
            view.setImageBitmap(l);
            return;
        }
        ex.execute(() -> {
            Bitmap bmp = mem.get(key);
            if (bmp == null) {
                bmp = fetch(url, Ui.dp(sizeDp));
                if (bmp == null) bmp = Ui.letterAvatar(hint, Math.max(1, Ui.dp(sizeDp)));
                mem.put(key, bmp);
            }
            final Bitmap bb = bmp;
            main.post(() -> {
                if (key.equals(view.getTag())) view.setImageBitmap(bb);
            });
        });
    }

    private Bitmap fetch(String url, int targetPx) {
        File f = new File(disk, md5(url) + ".img");
        if (!f.exists()) download(url, f);
        if (!f.exists()) return null;
        return decode(f, targetPx);
    }

    private void download(String url, File f) {
        File tmp = new File(f.getParentFile(), f.getName() + ".tmp");
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            c.setRequestProperty("User-Agent", "EskiDroid/1.2 (Android)");
            try {
                if (c.getResponseCode() / 100 != 2) return;
                // archive.org'un "bulunamadı" görseline düşerse başarısız say
                if (c.getURL() != null && c.getURL().toString().contains("notfound")) return;
                String ct = c.getContentType();
                if (ct != null && !ct.startsWith("image/")) return;
                InputStream in = c.getInputStream();
                OutputStream os = new FileOutputStream(tmp);
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                os.close();
                in.close();
                tmp.renameTo(f);
            } finally {
                c.disconnect();
            }
        } catch (Exception ignored) {
            tmp.delete();
        }
    }

    private Bitmap decode(File f, int targetPx) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            if (o.outWidth <= 0) return null;
            o.inSampleSize = 1;
            while (o.outWidth / (o.inSampleSize * 2) >= targetPx) o.inSampleSize *= 2;
            o.inJustDecodeBounds = false;
            return BitmapFactory.decodeFile(f.getPath(), o);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String md5(String s) {
        try {
            MessageDigest d = MessageDigest.getInstance("MD5");
            byte[] h = d.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(s.hashCode());
        }
    }
}
