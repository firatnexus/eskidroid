package io.github.firatnexus.eskidroid;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * EskiDroid — kabuk: ekranlar (Ara / Depo / Ayarlar), alt pil dock,
 * sürümler ekranı, kurulum onay kutusu ve bildirimler.
 * Yamalama/imzalama/kurulum mantığı AndroidAppPatcher, ApkSignerUtil ve
 * EskiDroidProvider'dan yalnızca ÇAĞRILIR; bunlar değiştirilmez.
 */
public class MainActivity extends Activity {

    public static final String CHANNEL_ID = "eskidroid_dl";
    private static final int NOTIF_ID = 1;

    private FrameLayout content;
    private LinearLayout dockWrap;
    private FrameLayout overlayHost;
    private SearchScreen searchScreen;
    private DepoScreen depoScreen;
    private SettingsScreen settingsScreen;
    private VersionsPanel versionsPanel;
    private String tab = "ara";
    private View currentOverlay;
    private final Map<String, View[]> dockViews = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        // İmza anahtarı cihazda kalıcı olsun (yamalı APK'lar yeniden kullanılabilir)
        ApkSignerUtil.setKeyDir(new File(getFilesDir(), "keys"));

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                    "İndirme durumu", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("İndirme ve kurulum ilerlemesi");
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }

        setupWindow();
        setContentView(buildRoot());

        LibraryManager.get(this).setChangeListener(() -> {
            depoScreen.render();
            if (versionsPanel != null) versionsPanel.render();
        });
    }

    /** Kenardan kenaya koyu; sistem çubukları şeffaf, içerik inset'lerle korunur. */
    private void setupWindow() {
        android.view.Window w = getWindow();
        w.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
    }

    private View buildRoot() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.CANVAS);

        content = new FrameLayout(this);
        searchScreen = new SearchScreen(this);
        depoScreen = new DepoScreen(this);
        settingsScreen = new SettingsScreen(this);
        content.addView(searchScreen.view());
        content.addView(depoScreen.view());
        content.addView(settingsScreen.view());
        depoScreen.view().setVisibility(View.GONE);
        settingsScreen.view().setVisibility(View.GONE);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));

        overlayHost = new FrameLayout(this);
        root.addView(overlayHost, new FrameLayout.LayoutParams(-1, -1));

        // Alt dock: yüzen pil (#191919, 99dp, 1px kenarlık, monoline ikonlar)
        dockWrap = new LinearLayout(this);
        dockWrap.setOrientation(LinearLayout.VERTICAL);
        dockWrap.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);

        LinearLayout dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER_VERTICAL);
        dock.setPadding(Ui.dp(8), Ui.dp(8), Ui.dp(8), Ui.dp(8));
        GradientDrawable dg = new GradientDrawable();
        dg.setColor(Ui.CARD);
        dg.setCornerRadius(Ui.dp(99));
        dg.setStroke(Math.max(1, Ui.dp(1)), Ui.MIST);
        dock.setBackground(dg);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-2, -2);
        dl.setMargins(Ui.dp(16), 0, Ui.dp(16), Ui.dp(16));
        dock.setLayoutParams(dl);

        dock.addView(dockItem("ara", "Ara"));
        dock.addView(dockItem("depo", "Depo"));
        dock.addView(dockItem("ayarlar", "Ayarlar"));
        dockWrap.addView(dock);
        root.addView(dockWrap, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            content.setPadding(0, insets.getSystemWindowInsetTop(), 0, 0);
            dockWrap.setPadding(0, 0, 0, insets.getSystemWindowInsetBottom());
            return insets;
        });

        switchTab("ara");
        return root;
    }

    private View dockItem(final String id, String label) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(Ui.dp(14), Ui.dp(6), Ui.dp(14), Ui.dp(6));
        col.setMinimumWidth(Ui.dp(64));
        ImageView iv = new ImageView(this);
        iv.setContentDescription(label);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Ui.dp(24), Ui.dp(24));
        ilp.gravity = Gravity.CENTER_HORIZONTAL;
        iv.setLayoutParams(ilp);
        col.addView(iv);
        TextView t = Ui.text(this, label, Ui.GRAPHITE, 11, 1);
        t.setGravity(Gravity.CENTER);
        col.addView(t);
        col.setOnClickListener(v -> switchTab(id));
        dockViews.put(id, new View[]{iv, t});
        return col;
    }

    private void switchTab(String id) {
        tab = id;
        closeVersions();
        searchScreen.view().setVisibility("ara".equals(id) ? View.VISIBLE : View.GONE);
        depoScreen.view().setVisibility("depo".equals(id) ? View.VISIBLE : View.GONE);
        settingsScreen.view().setVisibility("ayarlar".equals(id) ? View.VISIBLE : View.GONE);
        if ("depo".equals(id)) depoScreen.onShow();
        if ("ayarlar".equals(id)) settingsScreen.onShow();
        for (Map.Entry<String, View[]> e : dockViews.entrySet()) {
            boolean active = e.getKey().equals(id);
            ImageView iv = (ImageView) e.getValue()[0];
            TextView t = (TextView) e.getValue()[1];
            t.setTextColor(active ? Ui.WHITE : Ui.GRAPHITE);
            iv.setImageDrawable(dockIcon(e.getKey(), active ? Ui.WHITE : Ui.GRAPHITE));
        }
    }

    private android.graphics.drawable.Drawable dockIcon(String id, int color) {
        if ("depo".equals(id)) return Icons.box(this, color);
        if ("ayarlar".equals(id)) return Icons.gear(this, color);
        return Icons.search(this, color);
    }

    // ============================ sürümler ekranı ============================

    public void openVersions(SearchItem item) {
        closeVersions();
        versionsPanel = new VersionsPanel(this, item);
        content.addView(versionsPanel.view(), new FrameLayout.LayoutParams(-1, -1));
    }

    public void closeVersions() {
        if (versionsPanel != null) {
            content.removeView(versionsPanel.view());
            versionsPanel = null;
        }
    }

    public void refreshVersions() {
        if (versionsPanel != null) versionsPanel.render();
    }

    @Override
    public void onBackPressed() {
        if (currentOverlay != null) {
            dismissOverlay();
            return;
        }
        if (versionsPanel != null) {
            closeVersions();
            return;
        }
        if (!"ara".equals(tab)) {
            switchTab("ara");
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        depoScreen.render();
        if (versionsPanel != null) versionsPanel.render();
    }

    // ============================ kurulum ============================

    /**
     * Kurulum akışı: yamalı isteniyorsa ve yama yoksa önce yamala+imzala
     * (iş dinleyicisi onInstallReady ile buraya döner); dosya hazırsa
     * doğrudan onay kutusu. Yamasız kurulum orijinal APK ile çalışır.
     */
    public void startInstall(LibraryManager.Meta m, boolean usePatched) {
        LibraryManager lib = LibraryManager.get(this);
        if (usePatched && !m.patched) {
            lib.process(m, true, false, installListener());
        } else {
            final LibraryManager.Meta mm = m;
            new Thread(() -> {
                try {
                    File folder = lib.dirFor(m.itemId, m.version);
                    File apk = usePatched ? new File(folder, "patched.apk") : new File(folder, "original.apk");
                    if (!apk.exists()) {
                        runOnUiThread(() -> Toast.makeText(this,
                                "Dosya bulunamadı — tekrar indir", Toast.LENGTH_LONG).show());
                        return;
                    }
                    runOnUiThread(() -> confirmInstall(mm, apk, usePatched));
                } catch (final Exception e) {
                    runOnUiThread(() -> Toast.makeText(this,
                            "Kurulum hazırlanamadı: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            }).start();
        }
    }

    private LibraryManager.JobListener installListener() {
        return new LibraryManager.JobListener() {
            @Override
            public void onStage(String stage, int pct, String label) {
                notifyJob("EskiDroid", label, pct, false);
                refreshVersions();
                depoScreen.render();
            }

            @Override
            public void onDone(LibraryManager.Meta meta, String message) {
                notifyJob("", message, 100, true);
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                refreshVersions();
                depoScreen.render();
            }

            @Override
            public void onError(String message) {
                notifyJob("", message, 0, true);
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                refreshVersions();
                depoScreen.render();
            }

            @Override
            public void onInstallReady(LibraryManager.Meta meta, File apk, boolean patched) {
                notifyJob("", patched ? "Yamalı kurulum hazır" : "Kurulum hazır", 100, true);
                confirmInstall(meta, apk, patched);
                refreshVersions();
                depoScreen.render();
            }
        };
    }

    /** Kurulum onay kutusu: ikonu APK'dan çıkar, ABI uyumsuzsa ember uyarı göster. */
    public void confirmInstall(final LibraryManager.Meta m, final File apk, final boolean patched) {
        final LibraryManager lib = LibraryManager.get(this);
        new Thread(() -> {
            try {
                final File staged = lib.stageForProvider(apk, m.itemId + "-" + m.version + (patched ? "-p" : "-o"));
                final Bitmap icon = LibraryManager.apkIcon(this, apk);
                final String label = LibraryManager.apkLabel(this, apk);
                final boolean abiBad = LibraryManager.abiMismatch(apk);
                final List<String> abis = LibraryManager.apkAbis(apk);
                runOnUiThread(() -> showInstallConfirm(m, staged, patched, icon, label, abiBad, abis));
            } catch (final Exception e) {
                runOnUiThread(() -> Toast.makeText(this,
                        "Kurulum hazırlanamadı: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void showInstallConfirm(final LibraryManager.Meta m, final File staged, final boolean patched,
                                    Bitmap icon, String label, boolean abiBad, List<String> abis) {
        FrameLayout ov = new FrameLayout(this);
        ov.setBackgroundColor(Ui.OVERLAY);

        LinearLayout card = Ui.card(this);
        card.setGravity(Gravity.CENTER_HORIZONTAL);

        ImageView iv = Ui.circularImage(this, 48);
        if (icon != null) iv.setImageBitmap(icon);
        else iv.setImageBitmap(Ui.letterAvatar(label != null ? label : m.displayName(), Ui.dp(48)));
        LinearLayout.LayoutParams ivlp = new LinearLayout.LayoutParams(Ui.dp(48), Ui.dp(48));
        ivlp.gravity = Gravity.CENTER_HORIZONTAL;
        iv.setLayoutParams(ivlp);
        card.addView(iv);

        card.addView(centered(Ui.text(this, label != null ? label : m.displayName(), Ui.WHITE, 16, 1)));
        card.addView(centered(Ui.text(this, "Sürüm " + m.version + " • " + m.sourceLabel(), Ui.GRAPHITE, 12, 0)));
        if (m.size > 0) card.addView(centered(Ui.text(this, Ui.size(m.size), Ui.GRAPHITE, 12, 0)));
        String sdkLine = null;
        if (m.targetSdkBefore != null) {
            sdkLine = "Hedef SDK: " + m.targetSdkBefore
                    + (patched && m.targetSdkAfter != null ? " → " + m.targetSdkAfter + " (yamalı)" : "");
        } else if (patched) {
            sdkLine = "Hedef SDK: 28'e yamalanacak";
        }
        if (sdkLine != null) card.addView(centered(Ui.text(this, sdkLine, Ui.GRAPHITE, 12, 0)));

        if (abiBad) {
            String deviceAbis = joinCommas(Build.SUPPORTED_ABIS);
            TextView warn = Ui.text(this,
                    "Bu uygulama cihazının mimarisiyle uyumsuz olabilir\n(lib: "
                            + joinCommas(abis.toArray(new String[0])) + ", cihaz: " + deviceAbis + ")",
                    Ui.EMBER, 12, 0);
            warn.setPadding(0, Ui.dp(8), 0, 0);
            warn.setGravity(Gravity.CENTER);
            card.addView(warn);
        }

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.VERTICAL);
        btns.setPadding(0, Ui.dp(12), 0, 0);
        TextView install = Ui.primaryPill(this, abiBad ? "Yine de kur" : "Kur");
        install.setOnClickListener(x -> {
            dismissOverlay();
            LibraryManager.get(this).markInstalled(m, patched ? "patched" : "original");
            promptInstall(staged, m.displayName());
        });
        btns.addView(install);
        TextView cancel = Ui.ghostPill(this, "Vazgeç");
        cancel.setOnClickListener(x -> dismissOverlay());
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        clp.topMargin = Ui.dp(8);
        cancel.setLayoutParams(clp);
        btns.addView(cancel);
        card.addView(btns);

        FrameLayout.LayoutParams clp2 = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        clp2.setMargins(Ui.dp(24), 0, Ui.dp(24), 0);
        ov.addView(card, clp2);
        overlayHost.addView(ov, new FrameLayout.LayoutParams(-1, -1));
        currentOverlay = ov;
    }

    /** "Açılışta çöktü mü?" — diğer modla yeniden kurmayı öner. */
    public void crashPrompt(final LibraryManager.Meta m) {
        boolean wasPatched = "patched".equals(m.installedMode);
        String otherLabel = wasPatched ? "yamasız" : "yamalı";
        confirm("Açılışta çöktü mü?",
                "Uygulama açılışta çöküyorsa diğer kurulum modunu dene.\nSon kurulum: "
                        + (wasPatched ? "yamalı" : "yamasız") + ".",
                new String[]{"Evet — " + otherLabel + " kur", "Hayır, sorun yok"},
                new Runnable[]{() -> startInstall(m, !wasPatched), null});
    }

    public void openPermissions(String pkg) {
        if (pkg == null) {
            Toast.makeText(this, "Paket adı bilinmiyor", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + pkg)));
        } catch (Exception e) {
            Toast.makeText(this, "Açılamadı", Toast.LENGTH_SHORT).show();
        }
    }

    /** Kurulumu sistem kurulumcusuna sunar (mantık değişmedi). */
    private void promptInstall(File apk, String name) {
        try {
            if (!getPackageManager().canRequestPackageInstalls()) {
                Toast.makeText(this, "Kurulum izni vermen gerekiyor — ayarlar açılıyor", Toast.LENGTH_LONG).show();
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
            Toast.makeText(this, "Kurulum başlatılamadı: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ============================ onay kutusu ============================

    public void confirm(String title, String message, String[] labels, Runnable[] actions) {
        FrameLayout ov = new FrameLayout(this);
        ov.setBackgroundColor(Ui.OVERLAY);
        LinearLayout card = Ui.card(this);
        if (title != null) card.addView(Ui.text(this, title, Ui.WHITE, 16, 1));
        if (message != null) {
            TextView m = Ui.text(this, message, Ui.GRAPHITE, 13, 0);
            m.setPadding(0, Ui.dp(6), 0, Ui.dp(8));
            card.addView(m);
        }
        for (int i = 0; i < labels.length; i++) {
            final Runnable a = i < actions.length ? actions[i] : null;
            TextView b = i == 0 ? Ui.primaryPill(this, labels[i]) : Ui.ghostPill(this, labels[i]);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
            blp.topMargin = Ui.dp(8);
            b.setLayoutParams(blp);
            b.setOnClickListener(x -> {
                dismissOverlay();
                if (a != null) a.run();
            });
            card.addView(b);
        }
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        clp.setMargins(Ui.dp(24), 0, Ui.dp(24), 0);
        ov.addView(card, clp);
        overlayHost.addView(ov, new FrameLayout.LayoutParams(-1, -1));
        currentOverlay = ov;
    }

    public void dismissOverlay() {
        if (currentOverlay != null) {
            overlayHost.removeView(currentOverlay);
            currentOverlay = null;
        }
    }

    // ============================ izin / bildirim ============================

    public boolean ensurePermissions() {
        List<String> want = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            want.add("android.permission.POST_NOTIFICATIONS");
        }
        if (want.isEmpty()) return true;
        requestPermissions(want.toArray(new String[0]), 7);
        Toast.makeText(this, "İzin verilince tekrar dokun", Toast.LENGTH_SHORT).show();
        return false;
    }

    public void notifyJob(String title, String text, int pct, boolean done) {
        try {
            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= 26) b = new Notification.Builder(this, CHANNEL_ID);
            else b = new Notification.Builder(this);
            b.setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle(done ? "EskiDroid" : (title == null || title.isEmpty() ? "EskiDroid" : title))
                    .setContentText(text)
                    .setOnlyAlertOnce(true);
            if (done) {
                b.setProgress(0, 0, false);
                b.setAutoCancel(true);
            } else if (pct < 0) {
                b.setProgress(0, 0, true);
                b.setOngoing(true);
            } else {
                b.setProgress(100, pct, false);
                b.setOngoing(true);
            }
            PendingIntent pi = PendingIntent.getActivity(this, 0,
                    new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);
            b.setContentIntent(pi);
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTIF_ID, b.build());
        } catch (Exception ignored) {
        }
    }

    // ============================ yardımcılar ============================

    private TextView centered(TextView t) {
        t.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(4);
        t.setLayoutParams(lp);
        return t;
    }

    private static String joinCommas(String[] arr) {
        StringBuilder sb = new StringBuilder();
        for (String a : arr) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(a);
        }
        return sb.toString();
    }
}
