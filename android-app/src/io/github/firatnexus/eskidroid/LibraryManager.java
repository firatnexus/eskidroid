package io.github.firatnexus.eskidroid;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Depo: indirilen her APK kalıcı olarak saklanır ve tekrar indirilmez.
 * Yapı: library/<paket-veya-arşiv-kimliği>/<sürüm>/original.apk +
 * patched.apk (varsa) + meta.json. index() bu dosyaları tarayarak tek liste üretir.
 *
 * Uyumluluk: yama hedefi sabit 28 DEĞİLDİR; cihazın gereksinimine göre seçilir
 * (bkz. patchTarget/requiredMin). Mevcut hedef SDK asla düşürülmez; hedefi
 * zaten yeterli olan APK yamanmaz (yalnız yamalandığında yeniden imzalanır).
 */
public final class LibraryManager {

    public static final String PATCHER_VERSION = "1.5";

    public static final String COMPAT_AUTO = "auto";
    public static final String COMPAT_HIGH = "high";

    public interface JobListener {
        void onStage(String stage, int pct, String label);

        void onDone(Meta meta, String message);

        void onError(String message);

        void onInstallReady(Meta meta, File apk, boolean patched);
    }

    public interface ChangeListener {
        void onLibraryChanged();
    }

    public static final class JobState {
        public String stage = "download";
        public int pct = -1;
        public String label = "";
    }

    public static final class Meta {
        public String itemId;
        public String name;
        public String apkLabel;      // APK'dan okunduysa gerçek ad
        public String version;
        public String source;        // "fdroid" | "archive"
        public String url;
        public long size = -1;
        public String sha256;
        public long downloadedAt;
        public boolean patched;
        public String patcherVersion;
        public Integer targetSdkBefore;
        public Integer targetSdkAfter;
        public String packageName;   // APK'dan okunur; "Kurulu" denetiminde kullanılır
        public String installedMode;  // "original" | "patched"
        public long installedAt;

        public String displayName() {
            return apkLabel != null && !apkLabel.isEmpty() ? apkLabel : name;
        }

        public String sourceLabel() {
            if ("fdroid".equals(source)) return "F-Droid";
            if ("github".equals(source)) return "GitHub";
            if ("local".equals(source)) return L.s(R.string.local_source);
            return "archive.org";
        }
    }

    private static LibraryManager s;

    public static synchronized LibraryManager get(Context ctx) {
        if (s == null) s = new LibraryManager(ctx.getApplicationContext());
        return s;
    }

    private final Context appCtx;
    private final File root;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Object lock = new Object();
    private final Map<String, List<JobListener>> listeners = new HashMap<>();
    private final Map<String, JobState> states = new HashMap<>();
    private final Map<String, Boolean> cancelled = new HashMap<>();
    private final Map<String, HttpURLConnection> conns = new HashMap<>();
    private ChangeListener changeListener;

    private LibraryManager(Context ctx) {
        appCtx = ctx;
        File ext = ctx.getExternalFilesDir(null);
        File base = (ext != null && ext.canWrite()) ? ext : ctx.getFilesDir();
        root = new File(base, "library");
        if (!root.exists()) root.mkdirs();
    }

    public void setChangeListener(ChangeListener l) {
        changeListener = l;
    }

    // ============================ uyumluluk ============================

    public String compatMode() {
        return prefs().getString("compat", COMPAT_AUTO);
    }

    public void setCompatMode(String mode) {
        prefs().edit().putString("compat", mode).apply();
    }

    private SharedPreferences prefs() {
        return appCtx.getSharedPreferences("eskidroid", Context.MODE_PRIVATE);
    }

    /**
     * Yamalanacak hedef SDK (mevcut hedef asla düşürülmez):
     * Yüksek mod → 28 (eski davranış). Otomatik modda Android 15+ → 24,
     * Android 14 → 23; daha eski cihazlarda yamaya gerek kalmaz (yamasız
     * kurulur); zorunlu hallerde 23'e yamalanır.
     */
    public int patchTarget() {
        if (COMPAT_HIGH.equals(compatMode())) return 28;
        if (Build.VERSION.SDK_INT >= 35) return 24;
        if (Build.VERSION.SDK_INT == 34) return 23;
        return 23;
    }

    /** Cihazın kabul ettiği en düşük hedef SDK (0 → kısıt yok). */
    public int requiredMin() {
        if (COMPAT_HIGH.equals(compatMode())) return 28;
        if (Build.VERSION.SDK_INT >= 35) return 24;
        if (Build.VERSION.SDK_INT == 34) return 23;
        return 0;
    }

    /** Bu meta yamalanacak mı? (hedef düşürülmez; bilg yoksa yamalanabilir sayılır) */
    public boolean patchNeeded(Meta m) {
        if (m == null || m.targetSdkBefore == null) return false;
        return m.targetSdkBefore < patchTarget();
    }

    /** Önce hangi kurulum denensin? true → yamalı önce. */
    public boolean patchedFirst(Meta m) {
        if (m != null && m.targetSdkBefore != null && m.targetSdkBefore >= patchTarget()) return false;
        if (COMPAT_HIGH.equals(compatMode())) return true;
        return Build.VERSION.SDK_INT >= 34;
    }

    // ============================ depo ============================

    public File dirFor(String itemId, String version) {
        return new File(new File(root, sanitize(itemId)), sanitize(version));
    }

    private static String sanitize(String s) {
        return s == null || s.isEmpty() ? "x" : s.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /** Tek indeks: diskteki tüm meta.json'ları tarar, en yeni önce. */
    public synchronized List<Meta> index() {
        List<Meta> out = new ArrayList<>();
        File[] ids = root.listFiles();
        if (ids == null) return out;
        for (File idDir : ids) {
            if (!idDir.isDirectory()) continue;
            File[] vers = idDir.listFiles();
            if (vers == null) continue;
            for (File vDir : vers) {
                if (!vDir.isDirectory()) continue;
                File mf = new File(vDir, "meta.json");
                if (!mf.exists()) continue;
                try {
                    out.add(fromJson(new JSONObject(readFile(mf))));
                } catch (Exception ignored) {
                }
            }
        }
        java.util.Collections.sort(out, (a, b) -> Long.compare(b.downloadedAt, a.downloadedAt));
        return out;
    }

    public synchronized Meta find(String itemId, String version) {
        for (Meta m : index()) {
            if (m.itemId.equals(itemId) && m.version.equals(version)) return m;
        }
        return null;
    }

    public synchronized void save(Meta m) {
        try {
            File f = new File(dirFor(m.itemId, m.version), "meta.json");
            JSONObject o = new JSONObject();
            o.put("itemId", m.itemId);
            o.put("name", m.name);
            o.put("apkLabel", m.apkLabel);
            o.put("version", m.version);
            o.put("source", m.source);
            o.put("url", m.url);
            o.put("size", m.size);
            o.put("sha256", m.sha256);
            o.put("downloadedAt", m.downloadedAt);
            o.put("patched", m.patched);
            o.put("patcherVersion", m.patcherVersion);
            o.put("packageName", m.packageName);
            o.put("installedMode", m.installedMode);
            o.put("installedAt", m.installedAt);
            o.put("targetSdkBefore", m.targetSdkBefore);
            o.put("targetSdkAfter", m.targetSdkAfter);
            writeString(f, o.toString());
        } catch (Exception ignored) {
        }
        changed();
    }

    private static Meta fromJson(JSONObject o) {
        Meta m = new Meta();
        m.itemId = o.optString("itemId");
        m.name = o.optString("name");
        String al = o.optString("apkLabel");
        m.apkLabel = al.isEmpty() ? null : al;
        m.version = o.optString("version");
        m.source = o.optString("source");
        m.url = o.optString("url");
        m.size = o.optLong("size", -1);
        String sh = o.optString("sha256");
        m.sha256 = sh.isEmpty() ? null : sh;
        m.downloadedAt = o.optLong("downloadedAt", 0);
        m.patched = o.optBoolean("patched");
        String pv = o.optString("patcherVersion");
        m.patcherVersion = pv.isEmpty() ? null : pv;
        if (o.has("targetSdkBefore") && !o.isNull("targetSdkBefore")) {
            m.targetSdkBefore = o.optInt("targetSdkBefore");
        }
        if (o.has("targetSdkAfter") && !o.isNull("targetSdkAfter")) {
            m.targetSdkAfter = o.optInt("targetSdkAfter");
        }
        String pn = o.optString("packageName");
        m.packageName = pn.isEmpty() ? null : pn;
        String im = o.optString("installedMode");
        m.installedMode = im.isEmpty() ? null : im;
        m.installedAt = o.optLong("installedAt", 0);
        return m;
    }

    // ============================ indirme ============================

    /** Aynı anahtarla iş çalışıyorsa YENİ indirme başlatmaz; dinleyiciyi ekler. */
    public void download(final SearchItem item, final VersionInfo v, final JobListener listener) {
        final String key = jobKey(item.id, v.label);
        synchronized (lock) {
            List<JobListener> ls = listeners.get(key);
            if (ls != null) {
                ls.add(listener);
                fireCurrent(key, listener);
                return;
            }
            ls = new ArrayList<>();
            ls.add(listener);
            listeners.put(key, ls);
        }
        io.execute(() -> {
            try {
                runDownload(item, v, key);
            } catch (Exception e) {
                fail(key, msg(e));
            }
        });
    }

    private static String jobKey(String itemId, String version) {
        return itemId + "/" + version;
    }

    private void runDownload(SearchItem item, VersionInfo v, String key) throws Exception {
        // Önbellek öncelikli: Depo'da geçerli kopya varsa asla yeniden indirilmez.
        Meta existing = find(item.id, v.label);
        if (existing != null) {
            if (validate(existing)) {
                done(key, existing, L.s(R.string.ready_in_library));
                return;
            }
            removeTree(dirFor(item.id, v.label)); // bozuk/eksik dosya: otomatik temizle
        }
        File folder = dirFor(item.id, v.label);
        if (!folder.exists() && !folder.mkdirs()) throw new Exception(L.s(R.string.mkdir_failed));
        File orig = new File(folder, "original.apk");
        File part = new File(folder, "original.apk.part");
        stage(key, "download", 0, L.s(R.string.step_download));

        // Güvenlik: indirme yalnızca HTTPS üzerinden yapılır (şema doğrulaması)
        URL url = new URL(v.url);
        if (!"https".equalsIgnoreCase(url.getProtocol())) {
            throw new Exception("Insecure URL blocked: " + url.getProtocol());
        }

        long doneBytes = part.exists() ? part.length() : 0;
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setInstanceFollowRedirects(true);
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", "EskiDroid/1.5 (Android)");
        if (doneBytes > 0) c.setRequestProperty("Range", "bytes=" + doneBytes + "-");
        synchronized (lock) {
            conns.put(key, c);
        }
        boolean append = false;
        try {
            int code = c.getResponseCode();
            if (code == 206) {
                append = true;
            } else if (code == 200) {
                doneBytes = 0;
                part.delete();
            } else {
                throw new Exception(L.s(R.string.server_returned, code));
            }
            long total = c.getContentLengthLong();
            if (append) total += doneBytes;
            long sofar = doneBytes;
            InputStream in = c.getInputStream();
            OutputStream os = new FileOutputStream(part, append);
            byte[] buf = new byte[16384];
            int n;
            long lastUi = 0;
            while ((n = in.read(buf)) > 0) {
                if (isCancelled(key)) throw new Cancelled();
                os.write(buf, 0, n);
                sofar += n;
                long now = System.currentTimeMillis();
                if (now - lastUi > 300) {
                    lastUi = now;
                    stage(key, "download", total > 0 ? (int) (sofar * 100 / total) : -1, L.s(R.string.step_download));
                }
            }
            os.close();
            in.close();
        } finally {
            synchronized (lock) {
                conns.remove(key);
            }
            c.disconnect();
        }
        if (v.size > 0 && part.length() != v.size) {
            part.delete();
            throw new Exception(L.s(R.string.size_mismatch));
        }
        if (!part.renameTo(orig)) {
            part.delete();
            throw new Exception(L.s(R.string.move_failed));
        }
        Meta m = new Meta();
        m.itemId = item.id;
        m.name = item.name;
        m.version = v.label;
        m.source = item.source;
        m.url = v.url;
        m.size = orig.length();
        m.downloadedAt = System.currentTimeMillis();
        m.sha256 = sha256(orig);
        Integer ts = null;
        try {
            ts = AndroidAppPatcher.readTargetSdk(orig);
        } catch (Exception ignored) {
        }
        m.targetSdkBefore = ts;
        readPkgInfo(m, orig);
        save(m);
        done(key, m, L.s(R.string.downloaded_done));
    }

    // ============================ yama + imza ============================

    /**
     * Yamala + imzala (kurulum istenirse onay kutusuna hazırlar).
     * force=true → "Yeniden yamala". patched.apk ve patcher sürümü aynıysa
     * yama yeniden çalıştırılmaz (yalnız bir kez yamalanır).
     */
    public void process(final Meta meta, final boolean install, final boolean force, final JobListener listener) {
        final String key = jobKey(meta.itemId, meta.version);
        synchronized (lock) {
            List<JobListener> ls = listeners.get(key);
            if (ls != null) {
                ls.add(listener);
                fireCurrent(key, listener);
                return;
            }
            ls = new ArrayList<>();
            ls.add(listener);
            listeners.put(key, ls);
        }
        io.execute(() -> {
            try {
                runProcess(meta, install, force, key);
            } catch (Exception e) {
                fail(key, msg(e));
            }
        });
    }

    private void runProcess(Meta meta, boolean install, boolean force, String key) throws Exception {
        File folder = dirFor(meta.itemId, meta.version);
        File orig = new File(folder, "original.apk");
        if (!orig.exists()) throw new Exception(L.s(R.string.original_missing));
        Integer ts = meta.targetSdkBefore;
        if (ts == null) {
            try {
                ts = AndroidAppPatcher.readTargetSdk(orig);
            } catch (Exception ignored) {
            }
            meta.targetSdkBefore = ts;
        }
        int target = patchTarget();
        File patched = new File(folder, "patched.apk");
        if (ts != null && ts >= target) {
            // Hedef SDK asla düşürülmez; yamaya gerek yok.
            meta.patched = false;
            save(meta);
            if (install) {
                stage(key, "install", -1, L.s(R.string.step_install));
                installReady(key, meta, orig, false);
            } else {
                done(key, meta, "Yamalama gerekmiyor (hedef SDK " + ts + " ≥ " + target + ")");
            }
            return;
        }
        boolean reuse = patched.exists() && !force && PATCHER_VERSION.equals(meta.patcherVersion);
        if (!reuse) {
            if (patched.exists()) patched.delete();
            File tmp = new File(folder, "patched.tmp.apk");
            stage(key, "patch", -1, L.s(R.string.step_patch));
            AndroidAppPatcher.patch(orig, tmp, target);
            stage(key, "sign", -1, L.s(R.string.step_sign));
            ApkSignerUtil.sign(tmp, patched);
            tmp.delete();
        }
        meta.patched = true;
        meta.patcherVersion = PATCHER_VERSION;
        meta.targetSdkAfter = target;
        save(meta);
        if (install) {
            stage(key, "install", -1, L.s(R.string.step_install));
            installReady(key, meta, patched, true);
        } else {
            done(key, meta, reuse ? L.s(R.string.patched_ready) : L.s(R.string.patched_done, target));
        }
    }

    // ============================ iş durumu ============================

    public JobState state(String itemId, String version) {
        synchronized (lock) {
            JobState st = states.get(jobKey(itemId, version));
            if (st == null) return null;
            JobState copy = new JobState();
            copy.stage = st.stage;
            copy.pct = st.pct;
            copy.label = st.label;
            return copy;
        }
    }

    public void cancel(String itemId, String version) {
        String key = jobKey(itemId, version);
        synchronized (lock) {
            cancelled.put(key, Boolean.TRUE);
            HttpURLConnection c = conns.get(key);
            if (c != null) {
                try {
                    c.disconnect();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private boolean isCancelled(String key) {
        synchronized (lock) {
            return cancelled.containsKey(key);
        }
    }

    private void stage(String key, String stageName, int pct, String label) {
        JobState st = new JobState();
        st.stage = stageName;
        st.pct = pct;
        st.label = label;
        final List<JobListener> ls;
        synchronized (lock) {
            states.put(key, st);
            List<JobListener> cur = listeners.get(key);
            ls = cur == null ? null : new ArrayList<>(cur);
        }
        if (ls == null) return;
        main.post(() -> {
            for (JobListener l : ls) l.onStage(stageName, pct, label);
        });
    }

    private void fireCurrent(String key, JobListener l) {
        JobState st;
        synchronized (lock) {
            st = states.get(key);
        }
        if (st == null) return;
        final JobState s = st;
        main.post(() -> l.onStage(s.stage, s.pct, s.label));
    }

    private void done(String key, final Meta meta, final String message) {
        final List<JobListener> ls;
        synchronized (lock) {
            ls = listeners.remove(key);
            states.remove(key);
            cancelled.remove(key);
        }
        if (ls == null) return;
        main.post(() -> {
            for (JobListener l : ls) l.onDone(meta, message);
        });
    }

    private void fail(String key, final String message) {
        final List<JobListener> ls;
        synchronized (lock) {
            ls = listeners.remove(key);
            states.remove(key);
            cancelled.remove(key);
        }
        if (ls == null) return;
        main.post(() -> {
            for (JobListener l : ls) l.onError(message);
        });
    }

    private void installReady(String key, final Meta meta, final File apk, final boolean patched) {
        final List<JobListener> ls;
        synchronized (lock) {
            ls = listeners.remove(key);
            states.remove(key);
            cancelled.remove(key);
        }
        if (ls == null) return;
        main.post(() -> {
            for (JobListener l : ls) l.onInstallReady(meta, apk, patched);
        });
    }

    // ============================ silme / duruma bakma ============================

    public void delete(final Meta m, final boolean delOrig, final boolean delPatched) {
        io.execute(() -> {
            File folder = dirFor(m.itemId, m.version);
            if (delOrig) new File(folder, "original.apk").delete();
            if (delPatched) {
                new File(folder, "patched.apk").delete();
                new File(folder, "patched.tmp.apk").delete();
            }
            File[] left = folder.listFiles();
            if (left == null || left.length == 0) {
                removeTree(folder);
                changed();
                return;
            }
            if (delPatched && m.patched) {
                m.patched = false;
                m.patcherVersion = null;
                m.targetSdkAfter = null;
                save(m);
            }
            changed();
        });
    }

    public void clearAll() {
        io.execute(() -> {
            removeTree(root);
            root.mkdirs();
            changed();
        });
    }

    public boolean isInstalled(Meta m) {
        if (m == null || m.packageName == null) return false;
        try {
            appCtx.getPackageManager().getPackageInfo(m.packageName, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void markInstalled(Meta m, String mode) {
        m.installedMode = mode;
        m.installedAt = System.currentTimeMillis();
        save(m);
    }

    public long totalSize() {
        return dirSize(root);
    }

    private static long dirSize(File dir) {
        long t = 0;
        File[] fs = dir.listFiles();
        if (fs != null) {
            for (File f : fs) t += f.isDirectory() ? dirSize(f) : f.length();
        }
        return t;
    }

    /** Dosya var mı + boyut/SHA-256 doğrulaması (önbellek-öncelikli indirme). */
    private boolean validate(Meta m) {
        File orig = new File(dirFor(m.itemId, m.version), "original.apk");
        if (!orig.exists() || orig.length() == 0) return false;
        if (m.size > 0 && orig.length() != m.size) return false;
        if (m.sha256 != null && !m.sha256.equals(sha256(orig))) return false;
        return true;
    }

    /** Kurulum/paylaşım için APK'yı önbelleğe kopyalar (EskiDroidProvider buradan sunar). */
    public File stageForProvider(File apk, String tag) throws Exception {
        File out = new File(appCtx.getCacheDir(), "install-" + sanitize(tag) + ".apk");
        FileInputStream in = new FileInputStream(apk);
        FileOutputStream os = new FileOutputStream(out);
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
        in.close();
        os.close();
        return out;
    }

    // ============================ yerel APK içe aktarma ============================

    /**
     * Cihazdan seçilen APK'yı Depo'ya ekler (kaynak "local"): orijinal dosya
     * original.apk olarak kopyalanır, paket adı/sürüm/hedef SDK okunur ve
     * normal indirme akışındaki gibi yamalanıp kurulabilir hale gelir.
     * Aynı paket+sürüm zaten varsa üzerine yazılır (güncelleme).
     */
    public Meta importLocal(final File srcApk, final JobListener listener) {
        final String key = "import/" + System.currentTimeMillis();
        synchronized (lock) {
            List<JobListener> ls = new ArrayList<>();
            ls.add(listener);
            listeners.put(key, ls);
        }
        io.execute(() -> {
            try {
                stage(key, "download", -1, L.s(R.string.importing));
                PackageInfo pi = apkInfo(appCtx, srcApk);
                if (pi == null || pi.applicationInfo == null) {
                    throw new Exception(L.s(R.string.import_not_apk));
                }
                String pkg = pi.packageName;
                String ver = pi.versionName == null || pi.versionName.isEmpty()
                        ? String.valueOf(pi.versionCode == 0 ? System.currentTimeMillis() : pi.versionCode)
                        : pi.versionName;
                File folder = dirFor(pkg, ver);
                if (!folder.exists() && !folder.mkdirs()) throw new Exception(L.s(R.string.mkdir_failed));
                File orig = new File(folder, "original.apk");
                File part = new File(folder, "original.apk.part");
                copyFile(srcApk, part);
                if (!part.renameTo(orig)) {
                    part.delete();
                    throw new Exception(L.s(R.string.move_failed));
                }
                // Eski yamalı kopya artık geçersiz: yeni orijinale göre yeniden üretilir
                File oldPatched = new File(folder, "patched.apk");
                if (oldPatched.exists()) oldPatched.delete();

                Meta m = new Meta();
                m.itemId = pkg;
                m.name = apkLabel(appCtx, orig);
                if (m.name == null || m.name.isEmpty()) m.name = pkg;
                m.version = ver;
                m.source = "local";
                m.url = null;
                m.size = orig.length();
                m.downloadedAt = System.currentTimeMillis();
                m.sha256 = sha256(orig);
                try {
                    m.targetSdkBefore = AndroidAppPatcher.readTargetSdk(orig);
                } catch (Exception ignored) {
                }
                readPkgInfo(m, orig);
                save(m);
                done(key, m, L.s(R.string.import_done, m.displayName()));
            } catch (Exception e) {
                fail(key, msg(e));
            }
        });
        return null;
    }

    private static void copyFile(File src, File dst) throws Exception {
        FileInputStream in = new FileInputStream(src);
        FileOutputStream os = new FileOutputStream(dst);
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
        in.close();
        os.close();
    }

    // ============================ APK incelemesi ============================

    /** APK lib/ klasöründeki ABI'ler (native kütüphane yoksa boş liste). */
    public static List<String> apkAbis(File apk) {
        List<String> abis = new ArrayList<>();
        try (ZipFile zip = new ZipFile(apk)) {
            Enumeration<? extends ZipEntry> e = zip.entries();
            while (e.hasMoreElements()) {
                ZipEntry en = e.nextElement();
                String n = en.getName();
                if (n.startsWith("lib/") && n.length() > 4) {
                    String rest = n.substring(4);
                    int slash = rest.indexOf('/');
                    if (slash > 0) {
                        String abi = rest.substring(0, slash);
                        if (!abis.contains(abi)) abis.add(abi);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return abis;
    }

    /** Native kütüphane var ama cihaz ABI'lerinden hiçbiriyle uyuşmuyorsa true. */
    public static boolean abiMismatch(File apk) {
        List<String> abis = apkAbis(apk);
        if (abis.isEmpty()) return false;
        String[] supported = Build.SUPPORTED_ABIS;
        if (supported == null) return false;
        for (String want : abis) {
            for (String have : supported) {
                if (have.equals(want)) return false;
            }
        }
        return true;
    }

    public static PackageInfo apkInfo(Context ctx, File apk) {
        try {
            PackageManager pm = ctx.getPackageManager();
            PackageInfo pi = pm.getPackageArchiveInfo(apk.getAbsolutePath(), 0);
            if (pi != null && pi.applicationInfo != null) {
                pi.applicationInfo.sourceDir = apk.getAbsolutePath();
                pi.applicationInfo.publicSourceDir = apk.getAbsolutePath();
            }
            return pi;
        } catch (Throwable t) {
            return null;
        }
    }

    public static Bitmap apkIcon(Context ctx, File apk) {
        try {
            PackageInfo pi = apkInfo(ctx, apk);
            if (pi != null && pi.applicationInfo != null) {
                Drawable d = ctx.getPackageManager().getApplicationIcon(pi.applicationInfo);
                return drawableToBitmap(d, Ui.dp(96));
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static String apkLabel(Context ctx, File apk) {
        try {
            PackageInfo pi = apkInfo(ctx, apk);
            if (pi != null && pi.applicationInfo != null) {
                CharSequence cs = ctx.getPackageManager().getApplicationLabel(pi.applicationInfo);
                if (cs != null && cs.length() > 0) return cs.toString();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Bitmap drawableToBitmap(Drawable d, int px) {
        try {
            if (d instanceof BitmapDrawable) {
                Bitmap src = ((BitmapDrawable) d).getBitmap();
                if (src != null) return Bitmap.createScaledBitmap(src, px, px, true);
            }
            Bitmap b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            d.setBounds(0, 0, px, px);
            d.draw(c);
            return b;
        } catch (Throwable t) {
            return null;
        }
    }

    private void readPkgInfo(Meta m, File apk) {
        PackageInfo pi = apkInfo(appCtx, apk);
        if (pi != null) {
            m.packageName = pi.packageName;
            String lbl = apkLabel(appCtx, apk);
            if (lbl != null) m.apkLabel = lbl;
        }
    }

    // ============================ yardımcılar ============================

    public static String sha256(File f) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            FileInputStream in = new FileInputStream(f);
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            in.close();
            byte[] h = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private void changed() {
        main.post(() -> {
            ChangeListener l = changeListener;
            if (l != null) l.onLibraryChanged();
        });
    }

    private static void removeTree(File f) {
        if (f == null || !f.exists()) return;
        File[] ch = f.listFiles();
        if (ch != null) {
            for (File c : ch) removeTree(c);
        }
        f.delete();
    }

    private static String readFile(File f) throws Exception {
        FileInputStream in = new FileInputStream(f);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        return bo.toString("UTF-8");
    }

    private static void writeString(File f, String s) throws Exception {
        FileOutputStream os = new FileOutputStream(f);
        os.write(s.getBytes("UTF-8"));
        os.close();
    }

    private static String msg(Exception e) {
        String m = e.getMessage();
        return m == null || m.isEmpty() ? e.getClass().getSimpleName() : m;
    }

    private static class Cancelled extends Exception {
        Cancelled() {
            super(L.s(R.string.download_cancelled));
        }
    }
}
