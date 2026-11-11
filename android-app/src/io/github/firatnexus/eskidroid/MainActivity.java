package io.github.firatnexus.eskidroid;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private EditText query;
    private TextView status;
    private ListView results;
    private final List<Object[]> current = new ArrayList<>(); // {label, action(Runnable)}

    private static final String FDROID_INDEX = "https://f-droid.org/repo/index-v1.json";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("EskiDroid — eski Android uygulamaları");
        title.setTextSize(18);
        root.addView(title);

        query = new EditText(this);
        query.setHint("Uygulama ara (F-Droid arşivi)");
        root.addView(query);

        Button fdroidBtn = new Button(this);
        fdroidBtn.setText("F-Droid'de ara");
        fdroidBtn.setOnClickListener(v -> fdroidSearch(query.getText().toString().trim()));
        root.addView(fdroidBtn);

        Button iaBtn = new Button(this);
        iaBtn.setText("Archive.org'da ara");
        iaBtn.setOnClickListener(v -> iaSearch(query.getText().toString().trim()));
        root.addView(iaBtn);

        status = new TextView(this);
        status.setPadding(0, pad, 0, 0);
        status.setText("Hazır. Arşivler: F-Droid (açık kaynak) ve Archive.org.");
        root.addView(status);

        results = new ListView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        results.setLayoutParams(lp);
        results.setOnItemClickListener((parent, view, pos, id) -> {
            Object[] row = current.get(pos);
            ((Runnable) row[1]).run();
        });
        root.addView(results);

        setContentView(root);

        // Imzalama anahtari bu cihazda, ilk kullanimda uretilir (APK icinde tasimiyoruz)
        ApkSignerUtil.setKeyDir(getFilesDir());
    }

    private void post(String msg) {
        runOnUiThread(() -> status.setText(msg));
    }

    private void setResults(List<Object[]> rows) {
        runOnUiThread(() -> {
            current.clear();
            current.addAll(rows);
            List<String> labels = new ArrayList<>();
            for (Object[] r : rows) labels.add((String) r[0]);
            results.setAdapter(new ArrayAdapter<>(this,
                    android.R.layout.simple_list_item_1, labels));
        });
    }

    private void bg(Runnable r) {
        new Thread(r).start();
    }

    // ------------------------------------------------ F-Droid

    private void fdroidSearch(String q) {
        if (q.isEmpty()) {
            toast("Önce bir şey yaz");
            return;
        }
        post("F-Droid dizini hazırlanıyor...");
        bg(() -> {
            try {
                File cache = new File(getCacheDir(), "fdroid-index.json");
                if (!cache.exists() || cache.length() < 1000) {
                    post("F-Droid dizini indiriliyor (bir kere, ~15 MB)...");
                    download(FDROID_INDEX, cache);
                }
                post("Aranıyor: " + q);
                JSONObject index = new JSONObject(readFile(cache));
                JSONArray apps = index.getJSONArray("apps");
                JSONObject packages = index.getJSONObject("packages");
                List<Object[]> rows = new ArrayList<>();
                String lq = q.toLowerCase();
                for (int i = 0; i < apps.length(); i++) {
                    JSONObject app = apps.getJSONObject(i);
                    String pkg = app.optString("packageName", "");
                    String name = app.optString("name", pkg);
                    if (pkg.toLowerCase().contains(lq) || name.toLowerCase().contains(lq)) {
                        String label = name + "\n" + pkg + " — sürümleri görmek için dokun";
                        rows.add(new Object[]{label, (Runnable) () -> showFdroidVersions(pkg, name, packages)});
                    }
                }
                if (rows.isEmpty()) post("Sonuç yok: " + q);
                else post(rows.size() + " uygulama bulundu (F-Droid)");
                setResults(rows);
            } catch (Exception e) {
                post("Hata: " + e.getMessage());
            }
        });
    }

    private void showFdroidVersions(String pkg, String name, JSONObject packages) {
        bg(() -> {
            try {
                JSONArray versions = packages.optJSONArray(pkg);
                if (versions == null) {
                    post("Sürüm bilgisi yok");
                    return;
                }
                List<Object[]> rows = new ArrayList<>();
                for (int i = 0; i < versions.length(); i++) {
                    JSONObject v = versions.getJSONObject(i);
                    String vname = v.optString("versionName", "?");
                    int code = v.optInt("versionCode", 0);
                    int target = v.optInt("targetSdkVersion", 0);
                    String flag = target < 23 ? "  [ESKİ — düzeltilecek]" : "";
                    String label = vname + " (code " + code + ", targetSdk " + target + ")" + flag;
                    String file = pkg + "_" + code + ".apk";
                    rows.add(new Object[]{label, (Runnable) () -> installFlow(
                            "https://f-droid.org/repo/" + file, pkg + "-" + vname + ".apk")});
                }
                post(name + " sürümleri:");
                setResults(rows);
            } catch (Exception e) {
                post("Hata: " + e.getMessage());
            }
        });
    }

    // ------------------------------------------------ Archive.org

    private void iaSearch(String q) {
        if (q.isEmpty()) {
            toast("Önce bir şey yaz");
            return;
        }
        post("Archive.org'da aranıyor: " + q);
        bg(() -> {
            try {
                String u = "https://archive.org/advancedsearch.php?q="
                        + URLEncoder.encode(
                        "collection:(\"apkarchive\") AND title:(" + q + ")", "UTF-8")
                        + "&rows=20&output=json&fl[]=identifier&fl[]=title";
                JSONObject d = new JSONObject(httpGet(u));
                JSONArray docs = d.getJSONObject("response").getJSONArray("docs");
                List<Object[]> rows = new ArrayList<>();
                for (int i = 0; i < docs.length(); i++) {
                    JSONObject doc = docs.getJSONObject(i);
                    String id = doc.getString("identifier");
                    String title = doc.optString("title", id);
                    rows.add(new Object[]{title + "\n" + id,
                            (Runnable) () -> showIaFiles(id)});
                }
                post(docs.length() + " sonuç (Archive.org)");
                setResults(rows);
            } catch (Exception e) {
                post("Hata: " + e.getMessage());
            }
        });
    }

    private void showIaFiles(String id) {
        post("Dosyalar alınıyor: " + id);
        bg(() -> {
            try {
                JSONObject d = new JSONObject(httpGet("https://archive.org/metadata/" + id));
                JSONArray files = d.getJSONArray("files");
                List<Object[]> rows = new ArrayList<>();
                for (int i = 0; i < files.length(); i++) {
                    JSONObject f = files.getJSONObject(i);
                    String n = f.optString("name", "");
                    if (n.endsWith(".apk")) {
                        long size = f.optLong("size", 0);
                        String url = "https://archive.org/download/" + id + "/"
                                + URLEncoder.encode(n, "UTF-8");
                        rows.add(new Object[]{n + " (" + (size / 1048576) + " MB)",
                                (Runnable) () -> installFlow(url, n)});
                    }
                }
                if (rows.isEmpty()) post("Bu öğede APK yok");
                else setResults(rows);
            } catch (Exception e) {
                post("Hata: " + e.getMessage());
            }
        });
    }

    // ------------------------------------------------ İndir + düzelt + kur

    private void installFlow(String url, String fileName) {
        post("İndiriliyor: " + fileName);
        bg(() -> {
            try {
                File dir = new File(getExternalFilesDir(null), "eskidroid");
                dir.mkdirs();
                File downloaded = new File(dir, fileName);
                download(url, downloaded);

                Integer target = AndroidAppPatcher.readTargetSdk(downloaded);
                post("İndirildi. targetSdk = " + target);
                final File[] toInstall = {downloaded};
                if (target != null && target < 23) {
                    post("Eski APK — hedef SDK düzeltiliyor (28)...");
                    File patched = new File(dir, "patched-" + fileName);
                    AndroidAppPatcher.patch(downloaded, patched, 28);
                    File signed = new File(dir, "signed-" + fileName);
                    post("Yeniden imzalanıyor...");
                    ApkSignerUtil.sign(patched, signed);
                    toInstall[0] = signed;
                }

                post("Kurulum başlatılıyor...");
                runOnUiThread(() -> confirmInstall(toInstall[0]));
            } catch (Exception e) {
                post("Hata: " + e.getMessage());
            }
        });
    }

    private void confirmInstall(File apk) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("Kurulumsun mu?");
        b.setMessage(apk.getName() + "\nTelefonun 'bilinmeyen kaynaklardan uygulama yükleme'\niznini bu uygulama için vermeniz gerekebilir.");
        b.setPositiveButton("Kur", (d, w) -> installApk(apk));
        b.setNegativeButton("Vazgeç", null);
        b.show();
    }

    private void installApk(File apk) {
        try {
            PackageInstaller installer = getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            int sessionId = installer.createSession(params);
            PackageInstaller.Session session = installer.openSession(sessionId);
            try (InputStream in = new FileInputStream(apk);
                 OutputStream out = session.openWrite("app", 0, apk.length())) {
                byte[] buf = new byte[8192];
                int r;
                while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
                session.fsync(out);
            }
            IntentSender sender = PendingIntent.getBroadcast(this, sessionId,
                    new Intent("io.github.firatnexus.eskidroid.INSTALL_RESULT"),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE).getIntentSender();
            session.commit(sender);
            session.close();
            post("Kurulum isteği gönderildi; ekrandaki onayı verin.");
        } catch (Exception e) {
            post("Kurulum hatası: " + e.getMessage());
            if (!getPackageManager().canRequestPackageInstalls()) {
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName())));
            }
        }
    }

    // ------------------------------------------------ Yardımcılar

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private String httpGet(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestProperty("User-Agent", "EskiDroid/1.0");
        c.setConnectTimeout(20000);
        c.setReadTimeout(30000);
        return new String(readAll(c.getInputStream()), StandardCharsets.UTF_8);
    }

    private void download(String url, File out) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestProperty("User-Agent", "EskiDroid/1.0");
        c.setConnectTimeout(20000);
        c.setReadTimeout(60000);
        try (InputStream in = c.getInputStream();
             FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) > 0) fos.write(buf, 0, r);
        }
    }

    private String readFile(File f) throws Exception {
        return new String(readAll(new FileInputStream(f)), StandardCharsets.UTF_8);
    }

    private byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int r;
        while ((r = in.read(buf)) > 0) bos.write(buf, 0, r);
        in.close();
        return bos.toByteArray();
    }
}
