package io.github.firatnexus.eskidroid;

import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sürümler ekranı: her sürüm bir kart. Kart'a dokunmak indirme başlatmaz;
 * eylem butonları Depo durumuna göre değişir (İndir / Yamalı Kur /
 * Yamasız Kur / Yeniden yamala / Güncel). Aynı indirme iki kez başlamaz;
 * iş sürerken adım göstergesi + ince mavi çubuk + İptal görünür.
 */
public final class VersionsPanel {

    private final MainActivity main;
    private final SearchItem item;
    private final FrameLayout root;
    private final LinearLayout list;
    private final List<VersionInfo> versions = new ArrayList<>();
    private final List<String> screenshots = new ArrayList<>();

    public VersionsPanel(MainActivity main, SearchItem item) {
        this.main = main;
        this.item = item;
        root = new FrameLayout(main);
        root.setBackgroundColor(Ui.CANVAS);

        LinearLayout col = new LinearLayout(main);
        col.setOrientation(LinearLayout.VERTICAL);

        // Üst bar: geri + ikon + ad/kaynak
        LinearLayout bar = new LinearLayout(main);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(12), Ui.dp(10), Ui.dp(16), Ui.dp(10));

        ImageView back = new ImageView(main);
        back.setImageDrawable(Icons.chevronLeft(main, Ui.WHITE));
        back.setContentDescription("Geri");
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Ui.CANVAS);
        bg.setCornerRadius(Ui.dp(99));
        bg.setStroke(Math.max(1, Ui.dp(1)), Ui.MIST);
        back.setBackground(bg);
        back.setPadding(Ui.dp(10), Ui.dp(10), Ui.dp(10), Ui.dp(10));
        back.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(44), Ui.dp(44)));
        back.setOnClickListener(v -> main.closeVersions());
        bar.addView(back);

        ImageView icon = Ui.circularImage(main, 40);
        ImageLoader.get(main).load(item.iconUrl, 40, icon, item.name);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Ui.dp(40), Ui.dp(40));
        ilp.leftMargin = Ui.dp(8);
        icon.setLayoutParams(ilp);
        bar.addView(icon);

        LinearLayout info = new LinearLayout(main);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(Ui.dp(12), 0, 0, 0);
        info.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        TextView title = Ui.text(main, item.name, Ui.WHITE, 18, 1);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(title);
        TextView src = Ui.text(main, item.sourceLabel() + " • " + item.id, Ui.GRAPHITE, 11, 0);
        src.setSingleLine(true);
        src.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(src);
        bar.addView(info);
        col.addView(bar);

        list = new LinearLayout(main);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Ui.dp(12), 0, Ui.dp(12), Ui.dp(120));
        ScrollView scroll = new ScrollView(main);
        scroll.setFillViewport(true);
        scroll.addView(list, new FrameLayout.LayoutParams(-1, -2));
        scroll.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
        col.addView(scroll);

        root.addView(col, new FrameLayout.LayoutParams(-1, -1));
        load();
    }

    public View view() {
        return root;
    }

    private void load() {
        renderSkeleton();
        if ("fdroid".equals(item.source)) loadFdroid();
        else loadArchive();
    }

    private void renderSkeleton() {
        list.removeAllViews();
        for (int i = 0; i < 4; i++) {
            View v = Ui.skeleton(main, 84);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = Ui.dp(8);
            v.setLayoutParams(lp);
            list.addView(v);
        }
    }

    private void renderError(String msg) {
        list.removeAllViews();
        TextView t = Ui.text(main, "Hata: " + msg, Ui.GRAPHITE, 14, 0);
        t.setPadding(Ui.dp(8), Ui.dp(16), Ui.dp(8), Ui.dp(12));
        list.addView(t);
        TextView retry = Ui.ghostPill(main, "Tekrar dene");
        retry.setOnClickListener(v -> load());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = Ui.dp(8);
        retry.setLayoutParams(lp);
        list.addView(retry);
    }

    // ============================ F-Droid ============================

    private void loadFdroid() {
        Net.POOL.execute(() -> {
            try {
                JSONObject o = new JSONObject(Net.get("https://f-droid.org/api/v1/packages/" + item.id));
                JSONArray pkgs = o.optJSONArray("packages");
                if (pkgs == null) throw new Exception("Sürüm listesi alınamadı");
                // Aynı versionName'in en yüksek versionCode'u alınır (URL'lerde kod kullanılır)
                Map<String, Long> best = new LinkedHashMap<>();
                for (int i = 0; i < pkgs.length(); i++) {
                    JSONObject p = pkgs.getJSONObject(i);
                    String vn = p.optString("versionName");
                    long vc = p.optLong("versionCode", -1);
                    if (vn == null || vn.isEmpty() || vc < 0) continue;
                    Long old = best.get(vn);
                    if (old == null || vc > old) best.put(vn, vc);
                }
                List<Map.Entry<String, Long>> es = new ArrayList<>(best.entrySet());
                Collections.sort(es, (a, b) -> Long.compare(b.getValue(), a.getValue()));
                versions.clear();
                int n = 0;
                for (Map.Entry<String, Long> e : es) {
                    if (n++ >= 25) break;
                    VersionInfo v = new VersionInfo();
                    v.label = e.getKey();
                    v.versionCode = e.getValue();
                    v.url = "https://f-droid.org/repo/" + item.id + "_" + e.getValue() + ".apk";
                    versions.add(v);
                }
                main.runOnUiThread(this::render);
                checkFdroidAvailability();
                loadScreenshots();
            } catch (final Exception e) {
                main.runOnUiThread(() -> renderError(e.getMessage()));
            }
        });
    }

    /** Her sürümün varlığını/boyutunu/tarihini HEAD ile doğrular; yoksa arşive bakar. */
    private void checkFdroidAvailability() {
        List<VersionInfo> vs = new ArrayList<>(versions);
        for (final VersionInfo v : vs) {
            Net.POOL.execute(() -> {
                long[] h = Net.head(v.url);
                String url = v.url;
                if (h == null) {
                    String alt = v.url.replace("https://f-droid.org/repo/", "https://f-droid.org/archive/");
                    long[] h2 = Net.head(alt);
                    if (h2 != null) {
                        h = h2;
                        url = alt;
                    }
                }
                if (h == null) v.unavailable = true;
                else {
                    v.size = h[0];
                    v.date = h[1];
                    v.url = url;
                }
                main.runOnUiThread(this::render);
            });
        }
    }

    private void loadScreenshots() {
        Net.POOL.execute(() -> {
            try {
                String html = Net.get("https://f-droid.org/en/packages/" + item.id + "/");
                List<String> urls = new ArrayList<>();
                Matcher m = Pattern.compile("<img[^>]*>").matcher(html);
                while (m.find()) {
                    String tag = m.group();
                    if (!tag.contains("app screenshot")) continue;
                    Matcher s = Pattern.compile("src=\"([^\"]+)\"").matcher(tag);
                    if (s.find()) urls.add(s.group(1));
                }
                if (urls.isEmpty()) return;
                screenshots.clear();
                screenshots.addAll(urls);
                main.runOnUiThread(this::render);
            } catch (Exception ignored) {
            }
        });
    }

    // ============================ archive.org ============================

    private void loadArchive() {
        Net.POOL.execute(() -> {
            try {
                JSONObject files = new JSONObject(Net.get("https://archive.org/metadata/" + item.id));
                JSONArray arr = files.getJSONArray("files");
                versions.clear();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject f = arr.getJSONObject(i);
                    String fn = f.optString("name", "");
                    if (!fn.toLowerCase(java.util.Locale.ROOT).endsWith(".apk")) continue;
                    VersionInfo v = new VersionInfo();
                    String[] nv = NameUtil.clean(fn);
                    v.label = nv[1].isEmpty() ? fn : nv[1];
                    v.url = "https://archive.org/download/" + item.id + "/" +
                            URLEncoder.encode(fn, "UTF-8").replace("+", "%20");
                    v.size = f.optLong("size", -1);
                    v.date = f.optLong("mtime", -1) * 1000L;
                    versions.add(v);
                }
                if (versions.isEmpty()) {
                    main.runOnUiThread(() -> renderError("Bu arşivde APK yok"));
                    return;
                }
                main.runOnUiThread(this::render);
            } catch (final Exception e) {
                main.runOnUiThread(() -> renderError(e.getMessage()));
            }
        });
    }

    // ============================ çizim ============================

    public void render() {
        if (versions.isEmpty() && screenshots.isEmpty()) return; // iskelet/hata zaten çizili
        list.removeAllViews();
        if (!screenshots.isEmpty()) list.addView(screenshotStrip());
        LibraryManager lib = LibraryManager.get(main);
        for (VersionInfo v : versions) list.addView(versionCard(v, lib));
    }

    private View screenshotStrip() {
        LinearLayout box = new LinearLayout(main);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView t = Ui.text(main, "Ekran görüntüleri", Ui.GRAPHITE, 12, 1);
        t.setPadding(Ui.dp(4), 0, Ui.dp(4), Ui.dp(6));
        box.addView(t);
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(main);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(main);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (String url : screenshots) {
            ImageView iv = Ui.roundedImage(main, 96, 160, 16);
            iv.setContentDescription("Ekran görüntüsü");
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(96), Ui.dp(160));
            lp.rightMargin = Ui.dp(8);
            iv.setLayoutParams(lp);
            ImageLoader.get(main).load(url, 160, iv, item.name);
            row.addView(iv);
        }
        hs.addView(row);
        box.addView(hs);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
        blp.bottomMargin = Ui.dp(10);
        box.setLayoutParams(blp);
        return box;
    }

    private View versionCard(final VersionInfo v, LibraryManager lib) {
        LinearLayout card = Ui.card(main);
        card.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));

        LibraryManager.Meta m = lib.find(item.id, v.label);
        LibraryManager.JobState st = lib.state(item.id, v.label);

        // Satır 1: sürüm + Yamalanacak pili
        LinearLayout row1 = new LinearLayout(main);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);
        row1.addView(Ui.text(main, v.label, Ui.WHITE, 15, 1));
        if (v.versionCode > 0) {
            row1.addView(Ui.text(main, "  (" + v.versionCode + ")", Ui.GRAPHITE, 12, 0));
        }
        if (m != null && lib.patchNeeded(m)) {
            TextView p = Ui.miniPill(main, "Yamalanacak → " + lib.patchTarget(), Ui.GREEN, Ui.GREEN);
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-2, -2);
            plp.leftMargin = Ui.dp(8);
            p.setLayoutParams(plp);
            row1.addView(p);
        }
        card.addView(row1);

        // Meta: yalnız gerçekten biliniyorsa
        String meta = "";
        if (v.size > 0) meta = Ui.size(v.size);
        if (v.date > 0) meta += (meta.isEmpty() ? "" : " • ") + Ui.date(v.date);
        if (!meta.isEmpty()) {
            TextView mt = Ui.text(main, meta, Ui.GRAPHITE, 12, 0);
            mt.setPadding(0, Ui.dp(2), 0, 0);
            card.addView(mt);
        }

        // Durum pil'leri
        boolean installed = lib.isInstalled(m);
        if (m != null) {
            LinearLayout pills = new LinearLayout(main);
            pills.setOrientation(LinearLayout.HORIZONTAL);
            pills.setPadding(0, Ui.dp(6), 0, 0);
            pills.addView(margin(Ui.miniPill(main, "İndirildi", Ui.MIST, Ui.SMOKE), 0, 0, 6, 0));
            if (m.patched) {
                pills.addView(margin(Ui.miniPill(main, "Yamalandı", Ui.GREEN, Ui.GREEN), 0, 0, 6, 0));
            }
            if (installed) pills.addView(Ui.miniPill(main, "Kurulu", Ui.GREEN, Ui.GREEN));
            card.addView(pills);
        }

        if (st != null) {
            card.addView(progressBlock(v, st));
        } else if (m == null) {
            if (v.unavailable) {
                TextView na = Ui.text(main, "Mevcut değil", Ui.GRAPHITE, 13, 1);
                na.setPadding(0, Ui.dp(8), 0, 0);
                card.addView(na);
            } else {
                TextView dl = Ui.primaryPill(main, "İndir");
                dl.setOnClickListener(x -> {
                    if (main.ensurePermissions()) LibraryManager.get(main).download(item, v, listener(v));
                });
                card.addView(margin(dl, 0, 8, 0, 0));
            }
        } else {
            card.addView(margin(actions(m, lib), 0, 8, 0, 0));
            if (installed) {
                LinearLayout ex = new LinearLayout(main);
                ex.setOrientation(LinearLayout.HORIZONTAL);
                ex.setPadding(0, Ui.dp(8), 0, 0);
                TextView perms = Ui.ghostPill(main, "İzinleri aç");
                perms.setOnClickListener(x -> main.openPermissions(m.packageName));
                ex.addView(perms);
                TextView crash = Ui.ghostPill(main, "Çöktü mü?");
                crash.setOnClickListener(x -> main.crashPrompt(m));
                ex.addView(margin(crash, 8, 0, 0, 0));
                card.addView(ex);
                TextView hint = Ui.text(main, "Açılışta çöküyorsa depolama/telefon izinlerini ver.", Ui.GRAPHITE, 11, 0);
                hint.setPadding(0, Ui.dp(6), 0, 0);
                card.addView(hint);
            }
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Ui.dp(8);
        card.setLayoutParams(lp);
        return card;
    }

    /** Eylem butonları: Yamalı Kur / Yamasız Kur / Yeniden yamala / Güncel. */
    private View actions(final LibraryManager.Meta m, LibraryManager lib) {
        LinearLayout box = new LinearLayout(main);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);

        boolean primaryIsPatched = m.patched || lib.patchedFirst(m);
        TextView primary;
        if (primaryIsPatched) primary = Ui.primaryPill(main, "Yamalı Kur");
        else primary = Ui.primaryPill(main, "Yamasız Kur");
        final boolean usePatched = primaryIsPatched;
        primary.setOnClickListener(x -> main.startInstall(m, usePatched));
        box.addView(primary);

        TextView second = primaryIsPatched ? Ui.ghostPill(main, "Yamasız Kur") : Ui.ghostPill(main, "Yamalı Kur");
        second.setOnClickListener(x -> main.startInstall(m, !usePatched));
        box.addView(margin(second, Ui.dp(8), 0, 0, 0));

        if (m.patched) {
            TextView re = Ui.ghostPill(main, "Yeniden yamala");
            re.setOnClickListener(x -> LibraryManager.get(main).process(m, true, true, listener(null)));
            box.addView(margin(re, Ui.dp(8), 0, 0, 0));
        }
        return box;
    }

    private View progressBlock(final VersionInfo v, LibraryManager.JobState st) {
        LinearLayout box = new LinearLayout(main);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, Ui.dp(8), 0, 0);

        LinearLayout top = new LinearLayout(main);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = Ui.text(main, st.label, Ui.WHITE, 12, 1);
        label.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        top.addView(label);
        TextView cancel = Ui.ghostPill(main, "İptal");
        cancel.setOnClickListener(x -> LibraryManager.get(main).cancel(item.id, v.label));
        top.addView(cancel);
        box.addView(top);

        LinearLayout steps = Ui.steps(main, st.stage);
        steps.setPadding(0, Ui.dp(6), 0, 0);
        box.addView(steps);

        ProgressBar pb = Ui.thinProgress(main);
        pb.setIndeterminate(st.pct < 0);
        if (st.pct >= 0) pb.setProgress(st.pct);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, Ui.dp(8));
        plp.topMargin = Ui.dp(6);
        pb.setLayoutParams(plp);
        box.addView(pb);
        return box;
    }

    private LibraryManager.JobListener listener(final VersionInfo v) {
        return new LibraryManager.JobListener() {
            @Override
            public void onStage(String stage, int pct, String label) {
                main.notifyJob(v != null ? v.label : "", label, pct, false);
                render();
            }

            @Override
            public void onDone(LibraryManager.Meta meta, String message) {
                main.notifyJob("", message, 100, true);
                Toast.makeText(main, message, Toast.LENGTH_LONG).show();
                render();
            }

            @Override
            public void onError(String message) {
                main.notifyJob("", message, 0, true);
                Toast.makeText(main, message, Toast.LENGTH_LONG).show();
                render();
            }

            @Override
            public void onInstallReady(LibraryManager.Meta meta, File apk, boolean patched) {
                main.notifyJob("", patched ? "Yamalı kurulum hazır" : "Kurulum hazır", 100, true);
                main.confirmInstall(meta, apk, patched);
                render();
            }
        };
    }

    private static View margin(View v, int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(Ui.dp(l), Ui.dp(t), Ui.dp(r), Ui.dp(b));
        v.setLayoutParams(lp);
        return v;
    }
}
