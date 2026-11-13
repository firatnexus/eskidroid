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
import android.graphics.Matrix;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
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
 * EskiDroid — kabuk: ekranlar (Ara / Depo / Ayarlar), alt dock,
 * detay sayfası, kurulum onay kutusu, tam ekran görsel görüntüleyici,
 * tema (Things: Sistem/Aydınlık/Karanlık) ve dil (5 dil) yönetimi.
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
    private DetailScreen detailScreen;
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
                    L.s(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW);
            ch.setDescription(L.s(R.string.notif_channel_desc));
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }

        setupWindow();
        setContentView(buildRoot());

        LibraryManager.get(this).setChangeListener(() -> {
            depoScreen.render();
            refreshVersions();
        });
    }

    /** Kenardan kenara; sistem çubukları şeffaf, içerik inset'lerle korunur. */
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

        // Alt dock: yüzen kart (Things: Paper yüzey, 18dp köşe, hairline kenarlık)
        dockWrap = new LinearLayout(this);
        dockWrap.setOrientation(LinearLayout.VERTICAL);
        dockWrap.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);

        LinearLayout dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER_VERTICAL);
        dock.setPadding(Ui.dp(8), Ui.dp(8), Ui.dp(8), Ui.dp(8));
        GradientDrawable dg = new GradientDrawable();
        dg.setColor(Ui.CARD);
        dg.setCornerRadius(Ui.dp(18));
        dg.setStroke(Math.max(1, Ui.dp(1)), Ui.HAIRLINE);
        dock.setBackground(dg);
        dock.setElevation(Ui.dp(8));
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-2, -2);
        dl.setMargins(Ui.dp(16), 0, Ui.dp(16), Ui.dp(16));
        dock.setLayoutParams(dl);

        dock.addView(dockItem("ara", L.s(R.string.tab_search)));
        dock.addView(dockItem("depo", L.s(R.string.tab_library)));
        dock.addView(dockItem("ayarlar", L.s(R.string.tab_settings)));
        dockWrap.addView(dock);
        root.addView(dockWrap, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            content.setPadding(0, insets.getSystemWindowInsetTop(), 0, 0);
            dockWrap.setPadding(0, 0, 0, insets.getSystemWindowInsetBottom());
            return insets;
        });

        switchTab("ara", false);
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
        TextView t = Ui.text(this, label, Ui.FOG, 11, 1);
        t.setGravity(Gravity.CENTER);
        col.addView(t);
        col.setOnClickListener(v -> switchTab(id, true));
        UiFx.press(col);
        dockViews.put(id, new View[]{iv, t});
        return col;
    }

    private void switchTab(String id, boolean animate) {
        tab = id;
        closeVersions();
        View target = "ara".equals(id) ? searchScreen.view()
                : "depo".equals(id) ? depoScreen.view() : settingsScreen.view();
        searchScreen.view().setVisibility("ara".equals(id) ? View.VISIBLE : View.GONE);
        depoScreen.view().setVisibility("depo".equals(id) ? View.VISIBLE : View.GONE);
        settingsScreen.view().setVisibility("ayarlar".equals(id) ? View.VISIBLE : View.GONE);
        if ("depo".equals(id)) depoScreen.onShow();
        if ("ayarlar".equals(id)) settingsScreen.onShow();
        for (Map.Entry<String, View[]> e : dockViews.entrySet()) {
            boolean active = e.getKey().equals(id);
            ImageView iv = (ImageView) e.getValue()[0];
            TextView t = (TextView) e.getValue()[1];
            t.setTextColor(active ? Ui.INK : Ui.FOG);
            iv.setImageDrawable(dockIcon(e.getKey(), active ? Ui.BLUE : Ui.FOG));
            if (active && animate && UiFx.anims(this)) {
                // Sekme geçiş animasyonu: ikon büyüyerek belirir
                iv.setScaleX(0.7f);
                iv.setScaleY(0.7f);
                iv.animate().scaleX(1f).scaleY(1f)
                        .setDuration(240)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(1.3f))
                        .start();
                UiFx.fadeIn(this, target);
            }
        }
    }

    private android.graphics.drawable.Drawable dockIcon(String id, int color) {
        if ("depo".equals(id)) return Icons.box(this, color);
        if ("ayarlar".equals(id)) return Icons.gear(this, color);
        return Icons.search(this, color);
    }

    // ============================ tema / dil ============================

    /** Tema veya dil değişince tüm arayüz anında yeniden çizilir (restart yok). */
    public void rebuildUi() {
        Ui.applyTheme(Prefs.get(this).dark(this));
        dismissOverlay();
        detailScreen = null;
        setContentView(buildRoot());
    }

    // ============================ detay sayfası ============================

    /** Grup kartından detay: en güvenilir kaynak seçilir (fdroid > archive > github). */
    public void openDetail(AppGroup grp) {
        SearchItem best = null;
        for (SearchItem it : grp.items) {
            if (Sources.FDROID.equals(it.source)) {
                best = it;
                break;
            }
        }
        if (best == null && !grp.items.isEmpty()) best = grp.items.get(0);
        if (best == null) return;
        openVersions(best);
    }

    public void openVersions(SearchItem item) {
        closeVersions();
        detailScreen = new DetailScreen(this, item);
        content.addView(detailScreen.view(), new FrameLayout.LayoutParams(-1, -1));
        UiFx.slideIn(this, detailScreen.view());
    }

    public void closeVersions() {
        if (detailScreen != null) {
            final DetailScreen d = detailScreen;
            detailScreen = null;
            UiFx.slideOut(this, d.view(), () -> {
                try {
                    content.removeView(d.view());
                } catch (Exception ignored) {
                }
            });
        }
    }

    public void refreshVersions() {
        if (detailScreen != null) detailScreen.render();
    }

    @Override
    public void onBackPressed() {
        if (currentOverlay != null) {
            dismissOverlay();
            return;
        }
        if (detailScreen != null) {
            closeVersions();
            return;
        }
        if (!"ara".equals(tab)) {
            switchTab("ara", true);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        depoScreen.render();
        refreshVersions();
    }

    // ============================ tam ekran görsel ============================

    /** Tam ekran görsel: pinch-zoom + sürükleme; dokunarak kapanır. */
    public void showFullscreenImage(String url, String name) {
        FrameLayout ov = new FrameLayout(this);
        ov.setBackgroundColor(0xEE000000);

        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.MATRIX);
        Matrix mat = new Matrix();
        iv.setImageMatrix(mat);
        ImageLoader.get(this).load(url, 1280, iv, name);

        final float[] scale = {1f};
        final ScaleGestureDetector sgd = new ScaleGestureDetector(this,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector d) {
                        float f = d.getScaleFactor();
                        scale[0] = Math.max(0.5f, Math.min(6f, scale[0] * f));
                        mat.setScale(scale[0], scale[0],
                                d.getFocusX(), d.getFocusY());
                        iv.setImageMatrix(mat);
                        return true;
                    }
                });
        final GestureDetector gd = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        scale[0] = scale[0] > 1.5f ? 1f : 2.5f;
                        mat.setScale(scale[0], scale[0], e.getX(), e.getY());
                        iv.setImageMatrix(mat);
                        return true;
                    }
                });
        iv.setOnTouchListener((v, e) -> {
            sgd.onTouchEvent(e);
            gd.onTouchEvent(e);
            return true;
        });
        ov.addView(iv, new FrameLayout.LayoutParams(-1, -1));

        TextView hint = Ui.text(this, L.s(R.string.zoom_hint), 0xB3FFFFFF, 11, 0);
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        hlp.bottomMargin = Ui.dp(32);
        hint.setLayoutParams(hlp);
        ov.addView(hint);

        ov.setOnClickListener(v -> dismissOverlay());
        overlayHost.addView(ov, new FrameLayout.LayoutParams(-1, -1));
        currentOverlay = ov;
        UiFx.tap(this);
    }

    public void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, L.s(R.string.cannot_open), Toast.LENGTH_SHORT).show();
        }
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
                                L.s(R.string.file_missing), Toast.LENGTH_LONG).show());
                        return;
                    }
                    runOnUiThread(() -> confirmInstall(mm, apk, usePatched));
                } catch (final Exception e) {
                    runOnUiThread(() -> Toast.makeText(this,
                            L.s(R.string.install_prep_error) + e.getMessage(), Toast.LENGTH_LONG).show());
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
                UiFx.err(MainActivity.this);
                UiFx.sndError(MainActivity.this);
                refreshVersions();
                depoScreen.render();
            }

            @Override
            public void onInstallReady(LibraryManager.Meta meta, File apk, boolean patched) {
                notifyJob("", patched
                        ? L.s(R.string.patched_ready) : L.s(R.string.install_ready), 100, true);
                UiFx.done(MainActivity.this);
                confirmInstall(meta, apk, patched);
                refreshVersions();
                depoScreen.render();
            }
        };
    }

    /** Kurulum onay kutusu: ikonu APK'dan çıkar, ABI uyumsuzsa uyarı göster. */
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
                        L.s(R.string.install_prep_error) + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void showInstallConfirm(final LibraryManager.Meta m, final File staged, final boolean patched,
                                    Bitmap icon, String label, boolean abiBad, List<String> abis) {
        FrameLayout ov = new FrameLayout(this);
        ov.setBackgroundColor(Ui.OVERLAY);

        LinearLayout card = Ui.card(this);
        card.setGravity(Gravity.CENTER_HORIZONTAL);

        ImageView iv = Ui.appIcon(this, 48);
        if (icon != null) iv.setImageBitmap(icon);
        else iv.setImageBitmap(Ui.letterAvatar(label != null ? label : m.displayName(), Ui.dp(48)));
        LinearLayout.LayoutParams ivlp = new LinearLayout.LayoutParams(Ui.dp(48), Ui.dp(48));
        ivlp.gravity = Gravity.CENTER_HORIZONTAL;
        iv.setLayoutParams(ivlp);
        card.addView(iv);

        card.addView(centered(Ui.text(this, label != null ? label : m.displayName(), Ui.INK, 16, 1)));
        card.addView(centered(Ui.text(this, L.s(R.string.version_line, m.version, m.sourceLabel()), Ui.FOG, 12, 0)));
        if (m.size > 0) card.addView(centered(Ui.text(this, Ui.size(m.size), Ui.FOG, 12, 0)));
        String sdkLine = null;
        if (m.targetSdkBefore != null) {
            sdkLine = L.s(R.string.target_sdk_line, m.targetSdkBefore)
                    + (patched && m.targetSdkAfter != null
                    ? " → " + m.targetSdkAfter + " (" + L.s(R.string.patched_l) + ")" : "");
        } else if (patched) {
            sdkLine = L.s(R.string.target_sdk_will);
        }
        if (sdkLine != null) card.addView(centered(Ui.text(this, sdkLine, Ui.FOG, 12, 0)));

        if (abiBad) {
            String deviceAbis = joinCommas(Build.SUPPORTED_ABIS);
            TextView warn = Ui.text(this,
                    L.s(R.string.abi_warn, joinCommas(abis.toArray(new String[0])), deviceAbis),
                    Ui.EMBER, 12, 0);
            warn.setPadding(0, Ui.dp(8), 0, 0);
            warn.setGravity(Gravity.CENTER);
            card.addView(warn);
        }

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.VERTICAL);
        btns.setPadding(0, Ui.dp(12), 0, 0);
        TextView install = Ui.primaryButton(this, abiBad ? L.s(R.string.install_anyway) : L.s(R.string.install));
        UiFx.press(install);
        install.setOnClickListener(x -> {
            dismissOverlay();
            UiFx.tap(this);
            LibraryManager.get(this).markInstalled(m, patched ? "patched" : "original");
            promptInstall(staged, m.displayName());
        });
        btns.addView(install);
        TextView cancel = Ui.ghostButton(this, L.s(R.string.cancel));
        UiFx.press(cancel);
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
        String otherLabel = wasPatched ? L.s(R.string.install_original) : L.s(R.string.install_patched);
        confirm(L.s(R.string.crash_title),
                L.s(R.string.crash_body, wasPatched ? L.s(R.string.patched_l) : L.s(R.string.original_l)),
                new String[]{L.s(R.string.crash_yes, otherLabel), L.s(R.string.crash_no)},
                new Runnable[]{() -> startInstall(m, !wasPatched), null});
    }

    public void openPermissions(String pkg) {
        if (pkg == null) {
            Toast.makeText(this, L.s(R.string.pkg_unknown), Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + pkg)));
        } catch (Exception e) {
            Toast.makeText(this, L.s(R.string.cannot_open), Toast.LENGTH_SHORT).show();
        }
    }

    /** Kurulumu sistem kurulumcusuna sunar (mantık değişmedi). */
    private void promptInstall(File apk, String name) {
        try {
            if (!getPackageManager().canRequestPackageInstalls()) {
                Toast.makeText(this, L.s(R.string.install_perm_needed), Toast.LENGTH_LONG).show();
                Intent s = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName()));
                startActivity(s);
                return;
            }
            Intent in = new Intent(Intent.ACTION_INSTALL_PACKAGE);
            in.setData(EskiDroidProvider.uriFor(apk));
            in.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(in);
            UiFx.sndInstall(this);
        } catch (Exception e) {
            Toast.makeText(this, L.s(R.string.install_failed) + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ============================ onay kutusu ============================

    public void confirm(String title, String message, String[] labels, Runnable[] actions) {
        FrameLayout ov = new FrameLayout(this);
        ov.setBackgroundColor(Ui.OVERLAY);
        LinearLayout card = Ui.card(this);
        if (title != null) card.addView(Ui.text(this, title, Ui.INK, 16, 1));
        if (message != null) {
            TextView m = Ui.text(this, message, Ui.FOG, 13, 0);
            m.setPadding(0, Ui.dp(6), 0, Ui.dp(8));
            card.addView(m);
        }
        for (int i = 0; i < labels.length; i++) {
            final Runnable a = i < actions.length ? actions[i] : null;
            TextView b = i == 0 ? Ui.primaryButton(this, labels[i])
                    : Ui.ghostButton(this, labels[i]);
            UiFx.press(b);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
            blp.topMargin = Ui.dp(8);
            b.setLayoutParams(blp);
            b.setOnClickListener(x -> {
                dismissOverlay();
                UiFx.tap(this);
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
        Toast.makeText(this, L.s(R.string.perm_retry), Toast.LENGTH_SHORT).show();
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
