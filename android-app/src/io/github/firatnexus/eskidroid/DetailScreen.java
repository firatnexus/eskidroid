package io.github.firatnexus.eskidroid;

import android.graphics.Bitmap;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Uygulama detay sayfası: başlık (ikon, ad, paket, kaynak rozetleri, özet),
 * ekran görüntüleri (tam ekran + yakınlaştırma), açıklama (devamını oku),
 * bilgi bölümü, sürümler (minSdk/targetSdk/ABI/izin sayısı; Önerilen/Depoda),
 * izinler (açılır), anti-features ve değişiklik notları.
 * Veri yoksa bölüm hiç çizilmez. Eski akış korunur: indir → yamala → kur.
 */
public final class DetailScreen {

    private final MainActivity main;
    private final SearchItem item;
    private final FrameLayout root;
    private final LinearLayout list;

    private final List<VersionInfo> versions = new ArrayList<>();
    private final List<String> screenshots = new ArrayList<>();

    // F-Droid detay
    private Sources.FdroidDetail fd;
    // archive.org meta
    private JSONObject arcMeta;

    public DetailScreen(MainActivity main, SearchItem item) {
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

        TextView back = Ui.ghostButton(main, L.s(R.string.back));
        back.setMinWidth(Ui.dp(44));
        back.setGravity(Gravity.CENTER);
        UiFx.press(back);
        back.setOnClickListener(v -> main.closeVersions());
        bar.addView(back);

        ImageView icon = Ui.appIcon(main, 40);
        ImageLoader.get(main).load(item.iconUrl, 40, icon, item.name);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Ui.dp(40), Ui.dp(40));
        ilp.leftMargin = Ui.dp(8);
        icon.setLayoutParams(ilp);
        bar.addView(icon);

        LinearLayout info = new LinearLayout(main);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(Ui.dp(12), 0, 0, 0);
        info.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        TextView title = Ui.text(main, item.name, Ui.INK, 18, 1);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(title);
        TextView src = Ui.text(main, item.sourceLabel() + " • " + item.id, Ui.FOG, 11, 0);
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
        if (Sources.FDROID.equals(item.source)) loadFdroid();
        else if (Sources.GITHUB.equals(item.source)) loadGithub();
        else loadArchive();
    }

    private void renderSkeleton() {
        list.removeAllViews();
        List<View> sk = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            View v = Ui.skeleton(main, 84);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = Ui.dp(8);
            v.setLayoutParams(lp);
            list.addView(v);
            sk.add(v);
        }
        UiFx.shimmerAll(main, sk);
    }

    private void renderError(String msg) {
        list.removeAllViews();
        TextView t = Ui.text(main, L.s(R.string.error_prefix) + msg, Ui.FOG, 14, 0);
        t.setPadding(Ui.dp(8), Ui.dp(16), Ui.dp(8), Ui.dp(12));
        list.addView(t);
        TextView retry = Ui.ghostButton(main, L.s(R.string.retry));
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
                fd = Sources.fdroidDetail(item.id);
                if (fd.versions.isEmpty()) {
                    // API yedeği: sürüm listesi en azından gelsin
                    for (java.util.Map.Entry<String, Long> e : Sources.fdroidVersions(item.id).entrySet()) {
                        VersionInfo v = new VersionInfo();
                        v.label = e.getKey();
                        v.versionCode = e.getValue();
                        v.url = "https://f-droid.org/repo/" + item.id + "_" + e.getValue() + ".apk";
                        v.sourceTag = Sources.FDROID;
                        fd.versions.add(v);
                    }
                }
                versions.clear();
                versions.addAll(fd.versions);
                screenshots.clear();
                screenshots.addAll(fd.screenshots);
                main.runOnUiThread(this::render);
                checkFdroidAvailability();
            } catch (final Exception e) {
                main.runOnUiThread(() -> renderError(e.getMessage()));
            }
        });
    }

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
                    v.date = v.date > 0 ? v.date : h[1];
                    v.url = url;
                }
                main.runOnUiThread(this::render);
            });
        }
    }

    // ============================ GitHub ============================

    private void loadGithub() {
        Net.POOL.execute(() -> {
            try {
                versions.clear();
                versions.addAll(Sources.githubVersions(item.id));
                if (versions.isEmpty()) {
                    main.runOnUiThread(() -> renderError(L.s(R.string.no_apk_in_repo)));
                    return;
                }
                main.runOnUiThread(this::render);
            } catch (final Exception e) {
                main.runOnUiThread(() -> renderError(e.getMessage()));
            }
        });
    }

    // ============================ archive.org ============================

    private void loadArchive() {
        Net.POOL.execute(() -> {
            try {
                arcMeta = Sources.archiveMeta(item.id);
                versions.clear();
                versions.addAll(Sources.archiveVersions(item.id));
                if (versions.isEmpty()) {
                    main.runOnUiThread(() -> renderError(L.s(R.string.no_apk_in_item)));
                    return;
                }
                screenshots.clear();
                // arşiv öğesinin görselleri: files listesindeki resimler
                JSONArray files = arcMeta.optJSONArray("files");
                if (files != null) {
                    int n = 0;
                    for (int i = 0; i < files.length() && n < 8; i++) {
                        JSONObject f = files.getJSONObject(i);
                        String fn = f.optString("name", "");
                        String low = fn.toLowerCase(java.util.Locale.ROOT);
                        if ((low.endsWith(".png") || low.endsWith(".jpg") || low.endsWith(".jpeg"))
                                && !low.contains("icon")) {
                            screenshots.add("https://archive.org/download/" + item.id + "/" +
                                    java.net.URLEncoder.encode(fn, "UTF-8").replace("+", "%20"));
                            n++;
                        }
                    }
                }
                main.runOnUiThread(this::render);
            } catch (final Exception e) {
                main.runOnUiThread(() -> renderError(e.getMessage()));
            }
        });
    }

    // ============================ çizim ============================

    /** En yeni kullanılabilir sürümü "Önerilen" olarak işaretler (bir kez). */
    private void markRecommended() {
        for (VersionInfo v : versions) if (v.recommended) return;
        for (VersionInfo v : versions) {
            if (!v.unavailable) {
                v.recommended = true;
                return;
            }
        }
    }

    public void render() {
        if (versions.isEmpty() && screenshots.isEmpty() && fd == null && arcMeta == null) return;
        markRecommended();
        list.removeAllViews();
        List<View> cards = new ArrayList<>();

        cards.add(headerCard());
        if (!screenshots.isEmpty()) cards.add(section(L.s(R.string.screenshots), screenshotStrip()));
        String desc = description();
        if (desc != null && !desc.isEmpty()) cards.add(descriptionCard(desc));
        View info = infoCard();
        if (info != null) cards.add(info);
        cards.add(section(L.s(R.string.versions), null));
        LibraryManager lib = LibraryManager.get(main);
        for (VersionInfo v : versions) cards.add(versionCard(v, lib));
        View perms = permissionsCard();
        if (perms != null) cards.add(perms);
        if (fd != null && !fd.antiFeatures.isEmpty()) cards.add(antiFeaturesCard());
        View changelog = changelogCard();
        if (changelog != null) cards.add(changelog);

        for (View c : cards) list.addView(c);
        UiFx.stagger(main, cards);
    }

    /** Üst kart: ikon, ad, paket adı, kaynak rozetleri, özet. */
    private View headerCard() {
        LinearLayout card = Ui.card(main);

        LinearLayout row = new LinearLayout(main);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = Ui.appIcon(main, 56);
        icon.setContentDescription(item.name);
        ImageLoader.get(main).load(item.iconUrl, 56, icon, item.name);
        row.addView(icon);

        LinearLayout mid = new LinearLayout(main);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setPadding(Ui.dp(14), 0, Ui.dp(8), 0);
        mid.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

        TextView name = Ui.text(main, item.name, Ui.INK, 18, 1);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        mid.addView(name);

        TextView pkg = Ui.text(main, item.id, Ui.FOG, 12, 0);
        pkg.setSingleLine(true);
        pkg.setEllipsize(TextUtils.TruncateAt.END);
        mid.addView(pkg);

        LinearLayout badges = new LinearLayout(main);
        badges.setOrientation(LinearLayout.HORIZONTAL);
        badges.setPadding(0, Ui.dp(6), 0, 0);
        for (String src : new String[]{item.source}) {
            badges.addView(margin(Ui.badge(main, SearchItem.sourceLabel(src), Ui.BLUE, Ui.BLUE), 0, 0, 6, 0));
        }
        if (item.downloads > 0) {
            badges.addView(Ui.badge(main, L.s(R.string.downloads_count, L.num(item.downloads)), Ui.FOG, Ui.HAIRLINE));
        } else if (item.stars >= 0) {
            badges.addView(Ui.badge(main, "★ " + L.num(item.stars), Ui.EMBER, Ui.EMBER));
        }
        mid.addView(badges);
        row.addView(mid);
        card.addView(row);

        if (item.summary != null && !item.summary.isEmpty()) {
            TextView sm = Ui.text(main, item.summary, Ui.FOG, 13, 0);
            sm.setPadding(0, Ui.dp(10), 0, 0);
            card.addView(sm);
        }
        return card;
    }

    private String description() {
        if (fd != null && fd.description != null) return fd.description;
        if (arcMeta != null) {
            JSONObject m = arcMeta.optJSONObject("metadata");
            if (m != null) {
                String d = m.optString("description", "");
                if (d.isEmpty()) return null;
                return Sources.Html.strip(d);
            }
        }
        return null;
    }

    /** Açıklama: tam metin, "devamını oku" ile açılıp kapanır. */
    private View descriptionCard(final String desc) {
        LinearLayout card = Ui.card(main);
        card.addView(Ui.text(main, L.s(R.string.description), Ui.INK, 15, 1));
        final TextView body = Ui.text(main, desc, Ui.SMOKE, 14, 0);
        body.setPadding(0, Ui.dp(8), 0, 0);
        body.setMaxLines(5);
        body.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(body);
        if (desc.length() > 220) {
            final boolean[] open = {false};
            TextView more = Ui.link(main, L.s(R.string.read_more));
            more.setOnClickListener(v -> {
                open[0] = !open[0];
                body.setMaxLines(open[0] ? Integer.MAX_VALUE : 5);
                body.setEllipsize(open[0] ? null : TextUtils.TruncateAt.END);
                more.setText(open[0] ? L.s(R.string.read_less) : L.s(R.string.read_more));
            });
            card.addView(more);
        }
        return card;
    }

    /** Bilgi bölümü: yalnızca bilinen alanlar. */
    private View infoCard() {
        LinearLayout rows = new LinearLayout(main);
        rows.setOrientation(LinearLayout.VERTICAL);
        boolean any = false;

        String author = null;
        String lastUpdate = null;
        String website = null;
        String sourceCode = null;
        String issues = null;
        String license = null;
        String licenseUrl = null;

        if (fd != null) {
            license = fd.license;
            licenseUrl = fd.licenseUrl;
            website = fd.website;
            sourceCode = fd.sourceCode;
            issues = fd.issueTracker;
            lastUpdate = fd.lastUpdated;
        }
        if (arcMeta != null) {
            JSONObject m = arcMeta.optJSONObject("metadata");
            if (m != null) {
                author = m.optString("creator", null);
                if (author != null && author.isEmpty()) author = null;
                String lu = m.optString("last_updated", null);
                if (lu != null && lu.length() >= 10) lastUpdate = lu.substring(0, 10);
                String lic = m.optString("licenseurl", null);
                if (lic != null && lic.isEmpty()) lic = null;
                if (lic != null) {
                    licenseUrl = lic;
                    license = lic.substring(lic.lastIndexOf('/') + 1);
                }
            }
        }

        if (author != null) {
            rows.addView(infoRow(L.s(R.string.author), author, null));
            any = true;
        }
        if (license != null && !license.isEmpty()) {
            rows.addView(infoRow(L.s(R.string.license), license, licenseUrl));
            any = true;
        }
        if (lastUpdate != null && !lastUpdate.isEmpty()) {
            rows.addView(infoRow(L.s(R.string.last_update), lastUpdate, null));
            any = true;
        }
        if (website != null) {
            rows.addView(infoRow(L.s(R.string.website), shortUrl(website), website));
            any = true;
        }
        if (sourceCode != null) {
            rows.addView(infoRow(L.s(R.string.source_code), shortUrl(sourceCode), sourceCode));
            any = true;
        }
        if (issues != null) {
            rows.addView(infoRow(L.s(R.string.issue_tracker), shortUrl(issues), issues));
            any = true;
        }
        if (arcMeta != null) {
            long dl = arcMeta.optLong("downloads", -1);
            if (dl > 0) {
                rows.addView(infoRow(L.s(R.string.downloads), L.num(dl), null));
                any = true;
            }
        }
        if (!any) return null;
        return section(L.s(R.string.info), rows);
    }

    private View infoRow(String label, String value, final String url) {
        LinearLayout row = new LinearLayout(main);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView l = Ui.text(main, label, Ui.FOG, 13, 0);
        l.setMinWidth(Ui.dp(96));
        row.addView(l);
        TextView v;
        if (url != null) {
            v = Ui.link(main, value);
            v.setPadding(0, Ui.dp(4), 0, Ui.dp(4));
            v.setOnClickListener(x -> main.openUrl(url));
        } else {
            v = Ui.text(main, value, Ui.SMOKE, 13, 0);
            v.setSingleLine(true);
            v.setEllipsize(TextUtils.TruncateAt.END);
        }
        v.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(v);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(6);
        row.setLayoutParams(lp);
        return row;
    }

    private static String shortUrl(String u) {
        String s = u.replaceFirst("https?://", "");
        return s.length() > 42 ? s.substring(0, 42) + "…" : s;
    }

    /** Ekran görüntüleri yatay şerit; dokununca tam ekran (yakınlaştırmalı). */
    private View screenshotStrip() {
        LinearLayout row = new LinearLayout(main);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (final String url : screenshots) {
            ImageView iv = Ui.roundedImage(main, 96, 160, 18);
            iv.setContentDescription(L.s(R.string.screenshot));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(96), Ui.dp(160));
            lp.rightMargin = Ui.dp(8);
            iv.setLayoutParams(lp);
            ImageLoader.get(main).load(url, 320, iv, item.name);
            iv.setOnClickListener(v -> main.showFullscreenImage(url, item.name));
            row.addView(iv);
        }
        return row;
    }

    /** İzinler: açılır liste (sürüm kartından da girilir). */
    private View permissionsCard() {
        // yerel indirilmiş sürüm varsa onun izinleri
        List<String> perms = null;
        LibraryManager lib = LibraryManager.get(main);
        for (VersionInfo v : versions) {
            LibraryManager.Meta m = lib.find(item.id, v.label);
            if (m != null) {
                File folder = lib.dirFor(item.id, v.label);
                File apk = new File(folder, "original.apk");
                if (!apk.exists()) apk = new File(folder, "patched.apk");
                ApkInspector.Info info = ApkInspector.inspect(main, apk);
                if (!info.permissions.isEmpty()) {
                    perms = info.permissions;
                    break;
                }
            }
        }
        if (perms == null && fd != null) {
            for (VersionInfo v : fd.versions) {
                if (!v.permissions.isEmpty()) {
                    perms = v.permissions;
                    break;
                }
            }
        }
        if (perms == null || perms.isEmpty()) return null;

        LinearLayout card = Ui.card(main);
        LinearLayout head = new LinearLayout(main);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(Ui.text(main, L.s(R.string.permissions), Ui.INK, 15, 1));
        TextView n = Ui.text(main, " (" + perms.size() + ")", Ui.FOG, 13, 0);
        head.addView(n);
        card.addView(head);

        final LinearLayout box = new LinearLayout(main);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setVisibility(View.GONE);
        for (String p : perms) {
            TextView t = Ui.text(main, "• " + p, Ui.FOG, 12, 0);
            t.setPadding(0, Ui.dp(4), 0, 0);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            box.addView(t);
        }
        card.addView(box);
        final boolean[] open = {false};
        TextView toggle = Ui.link(main, L.s(R.string.show_permissions));
        toggle.setOnClickListener(v -> {
            open[0] = !open[0];
            box.setVisibility(open[0] ? View.VISIBLE : View.GONE);
            toggle.setText(open[0] ? L.s(R.string.hide_permissions) : L.s(R.string.show_permissions));
        });
        card.addView(toggle);
        return card;
    }

    private View antiFeaturesCard() {
        LinearLayout card = Ui.card(main);
        card.addView(Ui.text(main, L.s(R.string.anti_features), Ui.EMBER, 15, 1));
        for (String af : fd.antiFeatures) {
            TextView t = Ui.text(main, "• " + af, Ui.SMOKE, 13, 0);
            t.setPadding(0, Ui.dp(6), 0, 0);
            card.addView(t);
        }
        return card;
    }

    /** Değişiklik notları: en yeni sürümün whats-new metni. */
    private View changelogCard() {
        String text = null;
        for (VersionInfo v : versions) {
            if (v.whatsNew != null && !v.whatsNew.isEmpty()) {
                text = v.whatsNew;
                break;
            }
        }
        if (text == null) return null;
        LinearLayout card = Ui.card(main);
        card.addView(Ui.text(main, L.s(R.string.changelog), Ui.INK, 15, 1));
        TextView body = Ui.text(main, text, Ui.SMOKE, 13, 0);
        body.setPadding(0, Ui.dp(8), 0, 0);
        card.addView(body);
        return card;
    }

    // ============================ sürüm kartı ============================

    private View versionCard(final VersionInfo v, LibraryManager lib) {
        LinearLayout card = Ui.card(main);

        LibraryManager.Meta m = lib.find(item.id, v.label);
        LibraryManager.JobState st = lib.state(item.id, v.label);

        // Satır 1: sürüm + rozetler
        LinearLayout row1 = new LinearLayout(main);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);
        row1.addView(Ui.text(main, v.label, Ui.INK, 15, 1));
        if (v.versionCode > 0 && v.versionCode < 1000000000L) {
            row1.addView(Ui.text(main, "  (" + v.versionCode + ")", Ui.FOG, 12, 0));
        }

        if (v.recommended) {
            row1.addView(margin(Ui.badge(main, L.s(R.string.recommended), Ui.GREEN, Ui.GREEN), 8, 0, 0, 0));
        }
        if (m != null) {
            row1.addView(margin(Ui.badge(main, L.s(R.string.in_library), Ui.BLUE, Ui.BLUE), 8, 0, 0, 0));
        }
        if (m != null && lib.patchNeeded(m)) {
            row1.addView(margin(Ui.badge(main, L.s(R.string.will_patch_to, lib.patchTarget()), Ui.GREEN, Ui.GREEN), 8, 0, 0, 0));
        }
        card.addView(row1);

        // Meta satırı: tarih • boyut • ABI • minSdk/targetSdk • izin sayısı
        StringBuilder meta = new StringBuilder();
        if (v.date > 0) meta.append(Ui.date(v.date));
        if (v.size > 0) meta.append(meta.length() > 0 ? " • " : "").append(Ui.size(v.size));
        if (v.abi != null && !v.abi.isEmpty()) meta.append(meta.length() > 0 ? " • " : "").append("ABI: ").append(v.abi);
        // Yerel APK meta (Depo'daysa)
        if (m != null) {
            File folder = lib.dirFor(m.itemId, m.version);
            File apk = new File(folder, "original.apk");
            if (!apk.exists()) apk = new File(folder, "patched.apk");
            ApkInspector.Info info = ApkInspector.inspect(main, apk);
            if (info.minSdk > 0) meta.append(meta.length() > 0 ? " • " : "").append("minSdk ").append(info.minSdk);
            if (info.targetSdk > 0)
                meta.append(meta.length() > 0 ? " • " : "").append("targetSdk ").append(info.targetSdk);
            if (!info.permissions.isEmpty())
                meta.append(meta.length() > 0 ? " • " : "").append(L.s(R.string.perm_count, info.permissions.size()));
        } else if (v.permCount >= 0) {
            meta.append(meta.length() > 0 ? " • " : "").append(L.s(R.string.perm_count, v.permCount));
        }
        if (meta.length() > 0) {
            TextView mt = Ui.text(main, meta.toString(), Ui.FOG, 12, 0);
            mt.setPadding(0, Ui.dp(2), 0, 0);
            card.addView(mt);
        }

        // Durum rozetleri
        boolean installed = lib.isInstalled(m);
        if (m != null) {
            LinearLayout pills = new LinearLayout(main);
            pills.setOrientation(LinearLayout.HORIZONTAL);
            pills.setPadding(0, Ui.dp(6), 0, 0);
            pills.addView(margin(Ui.badge(main, L.s(R.string.downloaded), Ui.FOG, Ui.HAIRLINE), 0, 0, 6, 0));
            if (m.patched) {
                pills.addView(margin(Ui.badge(main, L.s(R.string.patched), Ui.GREEN, Ui.GREEN), 0, 0, 6, 0));
            }
            if (installed) pills.addView(Ui.badge(main, L.s(R.string.installed), Ui.GREEN, Ui.GREEN));
            card.addView(pills);
        }

        if (st != null) {
            card.addView(progressBlock(v, st));
        } else if (m == null) {
            if (v.unavailable) {
                TextView na = Ui.text(main, L.s(R.string.unavailable), Ui.FOG, 13, 1);
                na.setPadding(0, Ui.dp(8), 0, 0);
                card.addView(na);
            } else {
                TextView dl = Ui.primaryButton(main, L.s(R.string.download));
                UiFx.press(dl);
                dl.setOnClickListener(x -> {
                    UiFx.tap(main);
                    if (main.ensurePermissions())
                        LibraryManager.get(main).download(item, v, listener(v));
                });
                card.addView(margin(dl, 0, 8, 0, 0));
            }
        } else {
            card.addView(margin(actions(m, lib), 0, 8, 0, 0));
            if (installed) {
                LinearLayout ex = new LinearLayout(main);
                ex.setOrientation(LinearLayout.HORIZONTAL);
                ex.setPadding(0, Ui.dp(8), 0, 0);
                TextView perms = Ui.ghostButton(main, L.s(R.string.open_permissions));
                UiFx.press(perms);
                perms.setOnClickListener(x -> {
                    UiFx.tap(main);
                    main.openPermissions(m.packageName);
                });
                ex.addView(perms);
                TextView crash = Ui.ghostButton(main, L.s(R.string.crashed));
                UiFx.press(crash);
                crash.setOnClickListener(x -> {
                    UiFx.tap(main);
                    main.crashPrompt(m);
                });
                ex.addView(margin(crash, 8, 0, 0, 0));
                card.addView(ex);
                TextView hint = Ui.text(main, L.s(R.string.crash_hint), Ui.FOG, 11, 0);
                hint.setPadding(0, Ui.dp(6), 0, 0);
                card.addView(hint);
            }
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Ui.dp(8);
        card.setLayoutParams(lp);
        return card;
    }

    /** Eylem butonları: Yamalı Kur / Yamasız Kur / Yeniden yamala. */
    private View actions(final LibraryManager.Meta m, LibraryManager lib) {
        LinearLayout box = new LinearLayout(main);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);

        boolean primaryIsPatched = m.patched || lib.patchedFirst(m);
        TextView primary;
        if (primaryIsPatched) primary = Ui.primaryButton(main, L.s(R.string.install_patched));
        else primary = Ui.primaryButton(main, L.s(R.string.install_original));
        final boolean usePatched = primaryIsPatched;
        UiFx.press(primary);
        primary.setOnClickListener(x -> {
            UiFx.tap(main);
            main.startInstall(m, usePatched);
        });
        box.addView(primary);

        TextView second = primaryIsPatched
                ? Ui.ghostButton(main, L.s(R.string.install_original))
                : Ui.ghostButton(main, L.s(R.string.install_patched));
        UiFx.press(second);
        second.setOnClickListener(x -> {
            UiFx.tap(main);
            main.startInstall(m, !usePatched);
        });
        box.addView(margin(second, Ui.dp(8), 0, 0, 0));

        if (m.patched) {
            TextView re = Ui.ghostButton(main, L.s(R.string.repatch));
            UiFx.press(re);
            re.setOnClickListener(x -> {
                UiFx.tap(main);
                LibraryManager.get(main).process(m, true, true, listener(null));
            });
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
        TextView label = Ui.text(main, st.label, Ui.INK, 12, 1);
        label.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        top.addView(label);
        TextView pct = Ui.text(main, st.pct >= 0 ? st.pct + "%" : "", Ui.BLUE, 12, 1);
        top.addView(margin(pct, 0, 0, 8, 0));
        TextView cancel = Ui.ghostButton(main, L.s(R.string.cancel));
        UiFx.press(cancel);
        cancel.setOnClickListener(x -> {
            UiFx.tap(main);
            LibraryManager.get(main).cancel(item.id, v.label);
        });
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
                UiFx.done(main);
                UiFx.sndDone(main);
                render();
            }

            @Override
            public void onError(String message) {
                main.notifyJob("", message, 0, true);
                Toast.makeText(main, message, Toast.LENGTH_LONG).show();
                UiFx.err(main);
                UiFx.sndError(main);
                render();
            }

            @Override
            public void onInstallReady(LibraryManager.Meta meta, File apk, boolean patched) {
                main.notifyJob("", patched
                        ? L.s(R.string.patched_ready) : L.s(R.string.install_ready), 100, true);
                main.confirmInstall(meta, apk, patched);
                render();
            }
        };
    }

    // ============================ yardımcılar ============================

    private View section(String title, View content) {
        LinearLayout box = new LinearLayout(main);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView t = Ui.text(main, title, Ui.INK, 14, 1);
        t.setPadding(Ui.dp(4), Ui.dp(8), Ui.dp(4), Ui.dp(8));
        box.addView(t);
        if (content != null) box.addView(content);
        return box;
    }

    private static View margin(View v, int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(Ui.dp(l), Ui.dp(t), Ui.dp(r), Ui.dp(b));
        v.setLayoutParams(lp);
        return v;
    }
}
