package io.github.firatnexus.eskidroid;

import android.content.Intent;
import android.graphics.Bitmap;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Depo ekranı: kalıcı kütüphane. Üstte toplam boyut, depo içi arama,
 * filtre çipleri (Tümü / Yamalanmış / Yamasız) ve "Tümünü temizle".
 * Listeyi tek indeks (LibraryManager.index) sürer; tamamen çevrimdışı çalışır.
 */
public final class DepoScreen {

    private final MainActivity main;
    private final LinearLayout root;
    private final LinearLayout list;
    private final EditText query;
    private final TextView stats;
    private String filter = "all";
    private final List<TextView> filterLabels = new ArrayList<>();
    private final List<View> filterLines = new ArrayList<>();
    private final Map<String, Bitmap> icons = new HashMap<>();

    public DepoScreen(MainActivity main) {
        this.main = main;
        root = new LinearLayout(main);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.CANVAS);

        TextView title = Ui.bigTitle(main, L.s(R.string.tab_library));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.setMargins(Ui.dp(20), Ui.dp(16), Ui.dp(20), 0);
        title.setLayoutParams(tlp);
        root.addView(title);

        TextView sub = Ui.text(main, L.s(R.string.depo_subtitle), Ui.GRAPHITE, 13, 0);
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.setMargins(Ui.dp(20), Ui.dp(4), Ui.dp(20), Ui.dp(10));
        sub.setLayoutParams(slp);
        root.addView(sub);

        // Toplam boyut satırı
        stats = Ui.text(main, "", Ui.GRAPHITE, 12, 0);
        stats.setPadding(Ui.dp(20), 0, Ui.dp(20), 0);
        root.addView(stats);

        // Depo içi arama (Obsidian dolgu, 8dp köşe, hairline kenarlık)
        LinearLayout box = new LinearLayout(main);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Ui.ELEVATED);
        g.setCornerRadius(Ui.dp(8));
        g.setStroke(Math.max(1, Ui.dp(1)), Ui.HAIRLINE);
        box.setBackground(g);
        box.setPadding(Ui.dp(14), Ui.dp(2), Ui.dp(8), Ui.dp(2));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, Ui.dp(40));
        blp.setMargins(Ui.dp(16), Ui.dp(8), Ui.dp(16), Ui.dp(8));
        box.setLayoutParams(blp);

        query = new EditText(main);
        query.setHint(L.s(R.string.depo_search_hint));
        query.setHintTextColor(Ui.GRAPHITE);
        query.setTextColor(Ui.WHITE);
        query.setTextSize(14);
        query.setBackground(null);
        query.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        query.setLayoutParams(new LinearLayout.LayoutParams(0, -1, 1f));
        query.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                render();
            }
        });
        box.addView(query);
        root.addView(box);

        // Cihazdan APK ekle (mağaza GET tarzı birincil buton)
        TextView importBtn = Ui.primaryButton(main, L.s(R.string.import_apk));
        UiFx.press(importBtn);
        importBtn.setOnClickListener(v -> {
            UiFx.tap(main);
            main.pickLocalApk();
        });
        LinearLayout.LayoutParams iblp = new LinearLayout.LayoutParams(-1, -2);
        iblp.setMargins(Ui.dp(16), Ui.dp(4), Ui.dp(16), Ui.dp(4));
        importBtn.setLayoutParams(iblp);
        root.addView(importBtn);
        TextView importHint = Ui.text(main, L.s(R.string.import_apk_desc), Ui.FOG, 11, 0);
        importHint.setPadding(Ui.dp(20), 0, Ui.dp(20), Ui.dp(6));
        root.addView(importHint);

        // Filtre çipleri + temizle
        HorizontalScrollView hs = new HorizontalScrollView(main);
        hs.setFillViewport(true);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(main);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setGravity(Gravity.CENTER_VERTICAL);
        chips.setPadding(Ui.dp(12), 0, Ui.dp(12), 0);
        hs.addView(chips);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2);
        hlp.bottomMargin = Ui.dp(4);
        hs.setLayoutParams(hlp);

        addFilterChip(chips, L.s(R.string.filter_all), "all");
        addFilterChip(chips, L.s(R.string.filter_patched), "patched");
        addFilterChip(chips, L.s(R.string.filter_unpatched), "unpatched");

        TextView clearAll = Ui.dangerButton(main, L.s(R.string.clear_all));
        clearAll.setMinHeight(Ui.dp(36));
        clearAll.setTextSize(12);
        clearAll.setOnClickListener(v -> main.confirm(
                L.s(R.string.clear_all),
                L.s(R.string.clear_all_confirm),
                new String[]{L.s(R.string.delete), L.s(R.string.cancel)},
                new Runnable[]{() -> LibraryManager.get(main).clearAll(), null}));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
        clp.leftMargin = Ui.dp(8);
        clearAll.setLayoutParams(clp);
        chips.addView(clearAll);
        root.addView(hs);
        updateFilterChips();

        // Liste
        list = new LinearLayout(main);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Ui.dp(12), Ui.dp(4), Ui.dp(12), Ui.dp(120));
        ScrollView scroll = new ScrollView(main);
        scroll.setFillViewport(true);
        scroll.addView(list, new FrameLayout.LayoutParams(-1, -2));
        scroll.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(scroll);

        render();
    }

    public View view() {
        return root;
    }

    public void onShow() {
        render();
    }

    private void addFilterChip(LinearLayout parent, String label, final String value) {
        TextView t = Ui.text(main, label, Ui.INK, 13, 1);
        t.setPadding(Ui.dp(16), Ui.dp(6), Ui.dp(16), Ui.dp(7));
        LinearLayout col = new LinearLayout(main);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.addView(t);
        col.setOnClickListener(v -> {
            filter = value;
            updateFilterChips();
            render();
        });
        parent.addView(col);
        filterLabels.add(t);
        filterLines.add(null);
    }

    private void updateFilterChips() {
        String[] vals = {"all", "patched", "unpatched"};
        for (int i = 0; i < filterLabels.size(); i++) {
            boolean active = vals[i].equals(filter);
            TextView t = filterLabels.get(i);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(Ui.dp(60));
            if (active) {
                g.setColor(Ui.isDark() ? 0xFFFFFFFF : 0xFF24292F);
                t.setTextColor(Ui.isDark() ? 0xFF0D1117 : 0xFFFFFFFF);
            } else {
                g.setColor(0x00000000);
                g.setStroke(Math.max(1, Ui.dp(1)), Ui.PILL_BORDER);
                t.setTextColor(Ui.INK);
            }
            t.setBackground(g);
        }
    }

    // ============================ çizim ============================

    public void render() {
        LibraryManager lib = LibraryManager.get(main);
        List<LibraryManager.Meta> all = lib.index();
        if (all.isEmpty()) {
            stats.setText("");
        } else {
            stats.setText(L.s(R.string.depo_stats, all.size(), Ui.size(lib.totalSize())));
        }
        list.removeAllViews();
        if (all.isEmpty()) {
            TextView t = Ui.text(main, L.s(R.string.depo_empty), Ui.WHITE, 16, 1);
            t.setPadding(Ui.dp(8), Ui.dp(16), Ui.dp(8), Ui.dp(6));
            list.addView(t);
            TextView h = Ui.text(main,
                    L.s(R.string.depo_empty_hint),
                    Ui.GRAPHITE, 13, 0);
            h.setPadding(Ui.dp(8), 0, Ui.dp(8), 0);
            list.addView(h);
            return;
        }
        String q = query.getText().toString().trim().toLowerCase(Locale.ROOT);
        // Uygulamaya göre grupla, en yeni indirme önce
        Map<String, List<LibraryManager.Meta>> groups = new LinkedHashMap<>();
        for (LibraryManager.Meta m : all) {
            if ("patched".equals(filter) && !m.patched) continue;
            if ("unpatched".equals(filter) && m.patched) continue;
            if (!q.isEmpty()) {
                String hay = (m.displayName() + " " + m.version + " " + m.itemId).toLowerCase(Locale.ROOT);
                if (!hay.contains(q)) continue;
            }
            List<LibraryManager.Meta> g = groups.get(m.itemId);
            if (g == null) {
                g = new ArrayList<>();
                groups.put(m.itemId, g);
            }
            g.add(m);
        }
        if (groups.isEmpty()) {
            TextView t = Ui.text(main, L.s(R.string.depo_no_match), Ui.GRAPHITE, 14, 0);
            t.setPadding(Ui.dp(8), Ui.dp(16), Ui.dp(8), 0);
            list.addView(t);
            return;
        }
        for (Map.Entry<String, List<LibraryManager.Meta>> e : groups.entrySet()) {
            list.addView(groupCard(e.getKey(), e.getValue()));
        }
    }

    private View groupCard(String itemId, List<LibraryManager.Meta> metas) {
        LinearLayout card = Ui.card(main);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Ui.dp(10);
        card.setLayoutParams(lp);

        LibraryManager.Meta first = metas.get(0);

        LinearLayout head = new LinearLayout(main);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = Ui.circularImage(main, 48);
        icon.setContentDescription(first.displayName());
        Bitmap cached = icons.get(itemId);
        if (cached != null) {
            icon.setImageBitmap(cached);
        } else {
            icon.setImageBitmap(Ui.letterAvatar(first.displayName(), Ui.dp(48)));
            loadIcon(itemId, first);
        }
        head.addView(icon);

        LinearLayout info = new LinearLayout(main);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(Ui.dp(12), 0, Ui.dp(8), 0);
        info.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        TextView name = Ui.text(main, first.displayName(), Ui.WHITE, 15, 1);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(name);
        info.addView(Ui.text(main, first.sourceLabel() + " • " + L.s(R.string.version_count, metas.size()), Ui.GRAPHITE, 12, 0));
        head.addView(info);
        card.addView(head);

        for (LibraryManager.Meta m : metas) card.addView(row(m));
        return card;
    }

    /** Sürüm satırı: sürüm pili + durum pil'leri + boyut + eylemler. */
    private View row(final LibraryManager.Meta m) {
        LinearLayout box = new LinearLayout(main);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, Ui.dp(10), 0, 0);

        View div = new View(main);
        div.setBackgroundColor(Ui.SMOKE);
        div.setLayoutParams(new LinearLayout.LayoutParams(-1, 1));
        box.addView(div);

        LinearLayout l1 = new LinearLayout(main);
        l1.setOrientation(LinearLayout.HORIZONTAL);
        l1.setGravity(Gravity.CENTER_VERTICAL);
        l1.setPadding(0, Ui.dp(8), 0, 0);
        TextView vp = Ui.badge(main, m.version, Ui.FOG, Ui.HAIRLINE);
        l1.addView(vp);
        LibraryManager lib = LibraryManager.get(main);
        if (m.patched) {
            TextView p = Ui.badge(main, L.s(R.string.patched), Ui.GREEN, Ui.GREEN);
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-2, -2);
            plp.leftMargin = Ui.dp(6);
            p.setLayoutParams(plp);
            l1.addView(p);
        }
        if (lib.isInstalled(m)) {
            TextView p = Ui.badge(main, L.s(R.string.installed), Ui.GREEN, Ui.GREEN);
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-2, -2);
            plp.leftMargin = Ui.dp(6);
            p.setLayoutParams(plp);
            l1.addView(p);
        }
        if (m.installedMode != null) {
            TextView im = Ui.text(main, L.s(R.string.last_install, "patched".equals(m.installedMode) ? L.s(R.string.patched_l) : L.s(R.string.original_l)),
                    Ui.GRAPHITE, 10, 0);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, -2, 1f);
            ilp.leftMargin = Ui.dp(8);
            im.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
            im.setLayoutParams(ilp);
            l1.addView(im);
        }
        box.addView(l1);

        String meta = "";
        if (m.size > 0) meta = Ui.size(m.size);
        if (m.downloadedAt > 0) {
            meta += (meta.isEmpty() ? "" : " • ") + Ui.date(m.downloadedAt);
        }
        if (!meta.isEmpty()) {
            TextView mt = Ui.text(main, meta, Ui.GRAPHITE, 12, 0);
            box.addView(mt);
        }

        LinearLayout btns = new LinearLayout(main);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setGravity(Gravity.CENTER_VERTICAL);
        btns.setPadding(0, Ui.dp(8), 0, 0);

        boolean primaryIsPatched = m.patched || lib.patchedFirst(m);
        TextView primary = primaryIsPatched ? Ui.primaryButton(main, L.s(R.string.install_patched)) : Ui.primaryButton(main, L.s(R.string.install_original));
        primary.setTextSize(12);
        primary.setMinHeight(Ui.dp(36));
        primary.setOnClickListener(x -> main.startInstall(m, primaryIsPatched));
        btns.addView(primary);

        TextView share = Ui.ghostButton(main, L.s(R.string.share));
        share.setTextSize(12);
        share.setMinHeight(Ui.dp(36));
        share.setOnClickListener(x -> share(m));
        LinearLayout.LayoutParams shlp = new LinearLayout.LayoutParams(-2, -2);
        shlp.leftMargin = Ui.dp(8);
        share.setLayoutParams(shlp);
        btns.addView(share);

        TextView del = Ui.ghostButton(main, L.s(R.string.delete));
        del.setTextSize(12);
        del.setMinHeight(Ui.dp(36));
        del.setOnClickListener(x -> confirmDelete(m));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-2, -2);
        dlp.leftMargin = Ui.dp(8);
        del.setLayoutParams(dlp);
        btns.addView(del);
        box.addView(btns);

        if (lib.isInstalled(m)) {
            LinearLayout ex = new LinearLayout(main);
            ex.setOrientation(LinearLayout.HORIZONTAL);
            ex.setPadding(0, Ui.dp(6), 0, 0);
            TextView perms = Ui.ghostButton(main, L.s(R.string.open_permissions));
            perms.setTextSize(12);
            perms.setMinHeight(Ui.dp(36));
            perms.setOnClickListener(x -> main.openPermissions(m.packageName));
            ex.addView(perms);
            TextView crash = Ui.ghostButton(main, L.s(R.string.crashed));
            crash.setTextSize(12);
            crash.setMinHeight(Ui.dp(36));
            crash.setOnClickListener(x -> main.crashPrompt(m));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
            clp.leftMargin = Ui.dp(8);
            crash.setLayoutParams(clp);
            ex.addView(crash);
            box.addView(ex);
        }
        return box;
    }

    private void loadIcon(final String itemId, LibraryManager.Meta m) {
        final File apk = new File(LibraryManager.get(main).dirFor(m.itemId, m.version), "original.apk");
        if (!apk.exists()) return;
        new Thread(() -> {
            Bitmap b = LibraryManager.apkIcon(main, apk);
            if (b == null) return;
            icons.put(itemId, b);
            main.runOnUiThread(this::render);
        }).start();
    }

    private void share(final LibraryManager.Meta m) {
        new Thread(() -> {
            try {
                LibraryManager lib = LibraryManager.get(main);
                File folder = lib.dirFor(m.itemId, m.version);
                File apk = m.patched ? new File(folder, "patched.apk") : new File(folder, "original.apk");
                if (!apk.exists()) apk = new File(folder, "original.apk");
                if (!apk.exists()) {
                    main.runOnUiThread(() -> Toast.makeText(main, L.s(R.string.file_missing), Toast.LENGTH_SHORT).show());
                    return;
                }
                File staged = lib.stageForProvider(apk, m.itemId + "-" + m.version);
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("application/vnd.android.package-archive");
                send.putExtra(Intent.EXTRA_STREAM, EskiDroidProvider.uriFor(staged));
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                Intent chooser = Intent.createChooser(send, L.s(R.string.share));
                chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                main.runOnUiThread(() -> main.startActivity(chooser));
            } catch (final Exception e) {
                main.runOnUiThread(() -> Toast.makeText(main, L.s(R.string.share_failed) + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void confirmDelete(final LibraryManager.Meta m) {
        LibraryManager lib = LibraryManager.get(main);
        File folder = lib.dirFor(m.itemId, m.version);
        boolean hasO = new File(folder, "original.apk").exists();
        boolean hasP = new File(folder, "patched.apk").exists();
        List<String> labels = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        if (hasO) {
            labels.add(L.s(R.string.delete_original));
            acts.add(() -> lib.delete(m, true, false));
        }
        if (hasP) {
            labels.add(L.s(R.string.delete_patched));
            acts.add(() -> lib.delete(m, false, true));
        }
        labels.add(L.s(R.string.delete_both));
        acts.add(() -> lib.delete(m, true, true));
        labels.add(L.s(R.string.cancel));
        acts.add(null);
        main.confirm(L.s(R.string.delete_confirm), m.displayName() + " " + m.version,
                labels.toArray(new String[0]), acts.toArray(new Runnable[0]));
    }
}
