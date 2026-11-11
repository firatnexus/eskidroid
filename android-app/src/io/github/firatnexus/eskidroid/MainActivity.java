package io.github.firatnexus.eskidroid;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * EskiDroid — eski Android uygulamalarını bul, indir, yamala, kur.
 * Arayüz tamamen kod ile çizilir (XML düzen dosyası yok).
 */
public class MainActivity extends Activity {

    private static final String CHANNEL_ID = "eskidroid_dl";
    private static final int NOTIF_ID = 1;

    // Renk paleti (koyu tema)
    private static final int C_BG = 0xFF0E1116;
    private static final int C_SURFACE = 0xFF1A202B;
    private static final int C_SURFACE2 = 0xFF232B38;
    private static final int C_TEXT = 0xFFEDF1F7;
    private static final int C_TEXT_DIM = 0xFF98A2B3;
    private static final int C_ACCENT = 0xFF14B8A6;   // turkuaz
    private static final int C_ACCENT_DARK = 0xFF0E9384;
    private static final int C_BLUE = 0xFF4F8CFF;
    private static final int C_AMBER = 0xFFF5A623;

    private final ExecutorService pool = Executors.newFixedThreadPool(1);
    private final Map<String, View> rowViews = new HashMap<>(); // paketAdi -> satir

    private LinearLayout resultList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Bildirim kanali (API 26+)
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                    "İndirme durumu", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("İndirme ve kurulum ilerlemesi");
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }

        setContentView(buildUi());
    }

    /* ============================ ARAYÜZ ============================ */

    private View buildUi() {
        int pad = dp(16);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(C_BG);
        root.setFitsSystemWindows(true);

        // --- Başlık ---
        TextView title = new TextView(this);
        title.setText("EskiDroid");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        title.setTextColor(C_TEXT);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = pad; lp.topMargin = pad; lp.rightMargin = pad;
        title.setLayoutParams(lp);

        TextView subtitle = new TextView(this);
        subtitle.setText("Eski Android uygulamaları — ara, indir, yamala, kur");
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        subtitle.setTextColor(C_TEXT_DIM);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(-2, -2);
        lp2.leftMargin = pad; lp2.rightMargin = pad; lp2.bottomMargin = dp(12);
        subtitle.setLayoutParams(lp2);

        root.addView(title);
        root.addView(subtitle);

        // --- Arama kutusu (kart) ---
        LinearLayout searchCard = card(C_SURFACE);
        searchCard.setPadding(pad, dp(8), pad, dp(8));
        LinearLayout.LayoutParams scLp = new LinearLayout.LayoutParams(-1, -2);
        scLp.leftMargin = pad; scLp.rightMargin = pad; scLp.bottomMargin = dp(10);
        searchCard.setLayoutParams(scLp);

        final EditText query = new EditText(this);
        query.setHint("Oyun veya uygulama adı…");
        query.setHintTextColor(C_TEXT_DIM);
        query.setTextColor(C_TEXT);
        query.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        query.setInputType(InputType.TYPE_CLASS_TEXT);
        query.setBackground(null);
        LinearLayout.LayoutParams qLp = new LinearLayout.LayoutParams(-1, -2);
        query.setLayoutParams(qLp);
        searchCard.addView(query);
        root.addView(searchCard);

        // --- Arama butonları ---
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams brLp = new LinearLayout.LayoutParams(-1, -2);
        brLp.leftMargin = pad; brLp.rightMargin = pad; brLp.bottomMargin = dp(8);
        btnRow.setLayoutParams(brLp);

        Button fdBtn = styledButton("F-Droid", C_ACCENT_DARK, Color.WHITE);
        fdBtn.setOnClickListener(v -> runSearch(query.getText().toString().trim(), "fdroid"));
        LinearLayout.LayoutParams b1 = new LinearLayout.LayoutParams(0, -2, 1f);
        b1.rightMargin = dp(5);
        fdBtn.setLayoutParams(b1);

        Button iaBtn = styledButton("Archive.org", C_BLUE, Color.WHITE);
        iaBtn.setOnClickListener(v -> runSearch(query.getText().toString().trim(), "archive"));
        LinearLayout.LayoutParams b2 = new LinearLayout.LayoutParams(0, -2, 1f);
        b2.leftMargin = dp(5);
        iaBtn.setLayoutParams(b2);

        btnRow.addView(fdBtn);
        btnRow.addView(iaBtn);
        root.addView(btnRow);

        // --- Sonuç listesi ---
        resultList = new LinearLayout(this);
        resultList.setOrientation(LinearLayout.VERTICAL);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(resultList);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        scroll.setLayoutParams(sLp);
        root.addView(scroll);

        return root;
    }

    private LinearLayout card(int color) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(14));
        c.setBackground(bg);
        return c;
    }

    private Button styledButton(String text, int bgColor, int fgColor) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(fgColor);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        b.setAllCaps(false);
        b.setStateListAnimator(null);
        b.setPadding(dp(14), dp(10), dp(14), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(dp(12));
        b.setBackground(bg);
        return b;
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    /* ============================ ARAMA ============================ */

    private void runSearch(String q, String source) {
        if (q.isEmpty()) {
            toast("Önce aramak istediğin adı yaz");
            return;
        }
        resultList.removeAllViews();
        rowViews.clear();
        setStatus("Aranıyor: " + q + " …");
        pool.execute(() -> {
            try {
                if ("fdroid".equals(source)) {
                    searchFdroid(q);
                } else {
                    searchArchiveOrg(q);
                }
            } catch (Exception e) {
                runOnUiThread(() -> setStatus("Hata: " + e.getMessage()));
            }
        });
    }

    private void searchFdroid(String q) throws Exception {
        String url = "https://f-droid.org/en/search?count=25&fdfilter=" + java.net.URLEncoder.encode(q, "UTF-8");
        String html = httpGetText(url);
        // Basit HTML taraması: paket adı ve başlık
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "href=\"/en/packages/([a-z0-9_.]+)/\"");
        java.util.regex.Matcher m = p.matcher(html);
        final List<String[]> found = new ArrayList<>();
        while (m.find()) {
            String pkg = m.group(1);
            if (pkg.contains("q=")) continue;
            found.add(new String[]{pkg, prettyName(pkg)});
            if (found.size() >= 20) break;
        }
        runOnUiThread(() -> {
            resultList.removeAllViews();
            rowViews.clear();
            if (found.isEmpty()) {
                setStatus("Sonuç bulunamadı.");
                return;
            }
            for (String[] f : found) addResultRow(f[0], f[1], "F-Droid");
        });
    }

    private void searchArchiveOrg(String q) throws Exception {
        String url = "https://archive.org/advancedsearch.php?q=" +
                java.net.URLEncoder.encode("collection:(apkarchive) AND title:(" + q + ")", "UTF-8") +
                "&fl[]=identifier&fl[]=title&fl[]=downloads&rows=25&output=json";
        String json = httpGetText(url);
        JSONObject obj = new JSONObject(json);
        JSONArray docs = obj.getJSONObject("response").getJSONArray("docs");
        final List<String[]> found = new ArrayList<>();
        for (int i = 0; i < docs.length(); i++) {
            JSONObject d = docs.getJSONObject(i);
            found.add(new String[]{d.optString("identifier"), d.optString("title"), "archive"});
        }
        runOnUiThread(() -> {
            resultList.removeAllViews();
            rowViews.clear();
            if (found.isEmpty()) {
                setStatus("Sonuç bulunamadı.");
                return;
            }
            for (String[] f : found) addArchiveRow(f[0], f[1]);
        });
    }

    /* ============================ SONUÇ SATIRLARI ============================ */

    private void addResultRow(String pkg, String name, String source) {
        LinearLayout row = card(C_SURFACE);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(dp(16), dp(6), dp(16), dp(6));
        row.setLayoutParams(lp);

        TextView nameTv = new TextView(this);
        nameTv.setText(name);
        nameTv.setTextColor(C_TEXT);
        nameTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        nameTv.setTypeface(Typeface.DEFAULT_BOLD);

        TextView metaTv = new TextView(this);
        metaTv.setText(pkg);
        metaTv.setTextColor(C_TEXT_DIM);
        metaTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);

        Button getBtn = styledButton("Sürümler", C_SURFACE2, C_ACCENT);
        getBtn.setOnClickListener(v -> pool.execute(() -> loadVersions(pkg, name)));

        LinearLayout line1 = new LinearLayout(this);
        line1.setOrientation(LinearLayout.HORIZONTAL);
        nameTv.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        right.setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
        line1.addView(nameTv);
        line1.addView(right);

        row.addView(line1);
        row.addView(metaTv);

        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(-2, -2);
        btnLp.topMargin = dp(8);
        getBtn.setLayoutParams(btnLp);
        right.addView(getBtn);

        // durum alani
        TextView status = new TextView(this);
        status.setTextColor(C_AMBER);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        status.setVisibility(View.GONE);
        row.addView(status);

        ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pb.setVisibility(View.GONE);
        row.addView(pb);

        resultList.addView(row);
        rowViews.put(pkg, row);
    }

    private void addArchiveRow(String id, String title) {
        LinearLayout row = card(C_SURFACE);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(dp(16), dp(6), dp(16), dp(6));
        row.setLayoutParams(lp);

        TextView nameTv = new TextView(this);
        nameTv.setText(title != null && !title.isEmpty() ? title : id);
        nameTv.setTextColor(C_TEXT);
        nameTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        nameTv.setTypeface(Typeface.DEFAULT_BOLD);

        TextView metaTv = new TextView(this);
        metaTv.setText("archive.org • " + id);
        metaTv.setTextColor(C_TEXT_DIM);
        metaTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);

        Button getBtn = styledButton("Sürümler", C_SURFACE2, C_BLUE);
        getBtn.setOnClickListener(v -> pool.execute(() -> loadArchiveVersions(id, title)));

        row.addView(nameTv);
        row.addView(metaTv);

        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(-2, -2);
        btnLp.topMargin = dp(8);
        getBtn.setLayoutParams(btnLp);
        row.addView(getBtn);

        TextView status = new TextView(this);
        status.setTextColor(C_AMBER);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        status.setVisibility(View.GONE);
        row.addView(status);

        ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pb.setVisibility(View.GONE);
        row.addView(pb);

        resultList.addView(row);
        rowViews.put(id, row);
    }

    /* ============================ SÜRÜM LİSTESİ ============================ */

    private void loadVersions(String pkg, String name) {
        runOnUiThread(() -> rowStatus(pkg, "Sürümler alınıyor…"));
        try {
            JSONObject idx = new JSONObject(httpGetText("https://f-droid.org/repo/index-v1.json"));
            JSONObject apps = idx.getJSONObject("packages");
            if (!apps.has(pkg)) {
                runOnUiThread(() -> rowStatus(pkg, "F-Droid arşivinde bulunamadı."));
                return;
            }
            JSONArray versions = apps.getJSONArray(pkg);
            runOnUiThread(() -> rowStatus(pkg, versions.length() + " sürüm bulundu, en yenisi seçiliyor…"));
            // en son surumu indir
            JSONObject latest = versions.getJSONObject(versions.length() - 1);
            String apkName = pkg + "_" + latest.optString("versionName", "x") + ".apk";
            String url = "https://f-droid.org/repo/" + apkName;
            runOnUiThread(() -> rowStatus(pkg, "İndiriliyor: " + apkName));
            runDownload(pkg, name, url, apkName);
        } catch (Exception e) {
            runOnUiThread(() -> rowStatus(pkg, "Hata: " + e.getMessage()));
        }
    }

    private void loadArchiveVersions(String identifier, String title) {
        runOnUiThread(() -> rowStatus(identifier, "Dosya listesi alınıyor…"));
        try {
            JSONObject files = new JSONObject(httpGetText(
                    "https://archive.org/metadata/" + identifier));
            JSONArray items = files.getJSONArray("files");
            String chosen = null;
            long chosenSize = 0;
            for (int i = 0; i < items.length(); i++) {
                JSONObject f = items.getJSONObject(i);
                String fn = f.optString("name", "");
                if (fn.toLowerCase().endsWith(".apk") && f.optLong("size", 0) > chosenSize) {
                    chosen = fn;
                    chosenSize = f.optLong("size", 0);
                }
            }
            if (chosen == null) {
                runOnUiThread(() -> rowStatus(identifier, "Bu arşivde APK yok."));
                return;
            }
            final String url = "https://archive.org/download/" + identifier + "/" +
                    java.net.URLEncoder.encode(chosen, "UTF-8").replace("+", "%20");
            final String chosenF = chosen;
            final long chosenSzF = chosenSize;
            runOnUiThread(() -> rowStatus(identifier, "İndiriliyor: " + chosenF +
                    " (" + (chosenSzF / 1048576) + " MB)"));
            runDownload(identifier, title != null ? title : identifier, url, chosen);
        } catch (Exception e) {
            runOnUiThread(() -> rowStatus(identifier, "Hata: " + e.getMessage()));
        }
    }

    /* ============================ İNDİRME + YAMA + KURULUM ============================ */

    private void runDownload(String key, String appTitle, String url, String fileName) {
        // izinler
        if (!ensurePermissions()) return;

        File orig = new File(getCacheDir(), "orig.apk");
        File patched = new File(getCacheDir(), "patched.apk");
        File signed = new File(getCacheDir(), "signed.apk");

        try {
            // 1) indir
            notifyProgress(0, fileName, "İndiriliyor…", false);
            downloadFile(url, orig, key);

            // 2) yamala
            runOnUiThread(() -> rowStatus(key, "Yamalanıyor…"));
            notifyProgress(-1, fileName, "Yamalanıyor…", false);
            AndroidAppPatcher.patch(orig, patched, 28);

            // 3) imzala
            runOnUiThread(() -> rowStatus(key, "İmzalanıyor…"));
            notifyProgress(-1, fileName, "İmzalanıyor…", false);
            ApkSignerUtil.sign(patched, signed);

            // 4) İndirilenler klasörüne kopyala
            runOnUiThread(() -> rowStatus(key, "İndirilenler klasörüne kaydediliyor…"));
            Uri savedUri = saveToDownloads(signed, fileName);
            String where = savedUri != null ? "İndirilenler/EskiDroid" : "Uygulama klasörü";

            runOnUiThread(() -> {
                rowStatus(key, "Kaydedildi: " + where + "/" + fileName);
                toast("Kaydedildi: " + where + "/" + fileName);
            });
            notifyProgress(100, fileName, "Tamamlandı: " + where + "/" + fileName, true);

            // 5) kurulumu baslat
            runOnUiThread(() -> promptInstall(signed, fileName));

        } catch (Exception e) {
            String msg = e.getMessage();
            runOnUiThread(() -> {
                rowStatus(key, "Hata: " + msg);
                toast("Hata: " + msg);
            });
            notifyProgress(0, fileName, "Hata: " + msg, true);
        }
    }

    private void downloadFile(String urlStr, File out, String key) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setInstanceFollowRedirects(true);
        c.connect();
        int code = c.getResponseCode();
        if (code >= 300) throw new Exception("Sunucu " + code + " döndü");
        long total = c.getContentLengthLong();
        try (InputStream in = c.getInputStream(); OutputStream os = new FileOutputStream(out)) {
            byte[] buf = new byte[16384];
            long done = 0;
            int n;
            long lastUi = 0;
            while ((n = in.read(buf)) > 0) {
                os.write(buf, 0, n);
                done += n;
                if (total > 0) {
                    int pct = (int) (done * 100 / total);
                    long now = System.currentTimeMillis();
                    if (now - lastUi > 300) { // ui ve bildirimi ~3 kez/sn guncelle
                        lastUi = now;
                        final int p = pct; final long d = done;
                        runOnUiThread(() -> {
                            rowProgress(key, p);
                            rowStatus(key, "İndiriliyor %" + p + " — " + d / 1048576 + "/" + total / 1048576 + " MB");
                        });
                        notifyProgress(p, out.getName(), "İndiriliyor %" + p, false);
                    }
                }
            }
        }
        runOnUiThread(() -> rowProgress(key, 100));
    }

    /** APK'yı herkesin görebileceği İndirilenler klasörüne kaydeder. */
    private Uri saveToDownloads(File src, String name) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                // MediaStore: izin gerekmez
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
                cv.put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive");
                cv.put(MediaStore.Downloads.RELATIVE_PATH, "Download/EskiDroid");
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (uri == null) return null;
                try (OutputStream os = getContentResolver().openOutputStream(uri);
                     InputStream is = new FileInputStream(src)) {
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
                }
                return uri;
            } else {
                // API 21-28: klasik yolla, WRITE_EXTERNAL_STORAGE izniyle
                File dir = new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS), "EskiDroid");
                if (!dir.exists() && !dir.mkdirs()) return null;
                File dst = new File(dir, name);
                try (InputStream is = new FileInputStream(src); OutputStream os = new FileOutputStream(dst)) {
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
                }
                return Uri.fromFile(dst);
            }
        } catch (Exception e) {
            runOnUiThread(() -> toast("İndirilenler'e kaydedilemedi: " + e.getMessage()));
            return null;
        }
    }

    /** Kurulumu PackageInstaller'a sunar. */
    private void promptInstall(File apk, String name) {
        try {
            if (!getPackageManager().canRequestPackageInstalls()) {
                // izin yok: ayarlara yonlendir
                toast("Kurulum izni vermen gerekiyor — ayarlar açılıyor");
                Intent s = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName()));
                startActivity(s);
                return;
            }
            Intent in = new Intent(Intent.ACTION_INSTALL_PACKAGE);
            in.setData(EskiDroidProvider.uriFor(apk));
            in.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(in);
        } catch (Exception e) {
            toast("Kurulum başlatılamadı: " + e.getMessage());
        }
    }

    /* ============================ İZİNLER ============================ */

    private boolean ensurePermissions() {
        List<String> want = new ArrayList<>();
        // API 21-28: indirme icin depolama izni
        if (Build.VERSION.SDK_INT < 29 &&
                checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE") != PackageManager.PERMISSION_GRANTED) {
            want.add("android.permission.WRITE_EXTERNAL_STORAGE");
        }
        // API 33+: bildirim izni
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            want.add("android.permission.POST_NOTIFICATIONS");
        }
        if (want.isEmpty()) return true;
        requestPermissions(want.toArray(new String[0]), 7);
        toast("İzin verilince tekrar dokun");
        return false;
    }

    /* ============================ BİLDİRİM ============================ */

    private void notifyProgress(int pct, String file, String status, boolean done) {
        try {
            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= 26) {
                b = new Notification.Builder(this, CHANNEL_ID);
            } else {
                b = new Notification.Builder(this);
            }
            b.setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle(done ? "EskiDroid" : "İndiriliyor: " + file)
                    .setContentText(status)
                    .setOnlyAlertOnce(true);
            if (done) {
                b.setProgress(0, 0, false);
                b.setAutoCancel(true);
            } else if (pct < 0) {
                b.setProgress(0, 0, true); // belirsiz
                b.setOngoing(true);
            } else {
                b.setProgress(100, pct, false);
                b.setOngoing(true);
            }
            // Bildirime dokununca uygulama acilsin
            PendingIntent pi = PendingIntent.getActivity(this, 0,
                    new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);
            b.setContentIntent(pi);

            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(NOTIF_ID, b.build());
        } catch (Exception ignored) {
        }
    }

    /* ============================ YARDIMCILAR ============================ */

    private void rowStatus(String key, String text) {
        View v = rowViews.get(key);
        if (v == null || !(v instanceof LinearLayout)) return;
        LinearLayout row = (LinearLayout) v;
        for (int i = 0; i < row.getChildCount(); i++) {
            View c = row.getChildAt(i);
            if (c instanceof TextView && c.getVisibility() == View.VISIBLE &&
                    ((TextView) c).getCurrentTextColor() == C_AMBER) {
                ((TextView) c).setText(text);
                return;
            }
        }
    }

    private void rowProgress(String key, int pct) {
        View v = rowViews.get(key);
        if (v == null || !(v instanceof LinearLayout)) return;
        LinearLayout row = (LinearLayout) v;
        for (int i = 0; i < row.getChildCount(); i++) {
            View c = row.getChildAt(i);
            if (c instanceof ProgressBar) {
                c.setVisibility(View.VISIBLE);
                ((ProgressBar) c).setProgress(pct);
                return;
            }
        }
    }

    private void setStatus(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(C_TEXT_DIM);
        t.setPadding(dp(20), dp(12), dp(20), dp(12));
        resultList.addView(t);
    }

    private String httpGetText(String urlStr) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "EskiDroid/1.1 (Android)");
        int code = c.getResponseCode();
        if (code >= 300) throw new Exception("Sunucu " + code + " döndü");
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        try (InputStream in = c.getInputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        }
        return bo.toString("UTF-8");
    }

    private static String prettyName(String pkg) {
        String last = pkg.substring(pkg.lastIndexOf('.') + 1);
        StringBuilder sb = new StringBuilder();
        for (String w : last.split("_")) {
            if (w.isEmpty()) continue;
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    private void toast(String s) {
        runOnUiThread(() -> Toast.makeText(this, s, Toast.LENGTH_LONG).show());
    }

    @Override
    protected void onDestroy() {
        pool.shutdownNow();
        super.onDestroy();
    }
}
