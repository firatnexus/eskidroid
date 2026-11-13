package io.github.firatnexus.eskidroid;

import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ara ekranı — mağaza havası (App Store):
 * Arama kutusunun altında KAYNAK SEÇİMİ (F-Droid / Internet Archive / GitHub).
 * Kullanıcı bir kaynak seçene kadar karışık sonuç YOK; seçilen kaynağın
 * sonucu tek başına listelenir. Sonuçlar mağaza satırı (büyük yuvarlak
 * ikon, ad, geliştirici/kaynak satırı, sağda ok) biçimindedir.
 * archive.org için "daha fazla yükle" sayfalama sürer.
 */
public final class SearchScreen {

    private final MainActivity main;
    private final LinearLayout root;
    private final LinearLayout results;
    private final EditText query;
    private String source = null; // seçilene kadar null → kaynak seçici gösterilir
    private final List<TextView> chipLabels = new ArrayList<>();
    private final List<LinearLayout> chipBgs = new ArrayList<>();
    private final List<String> chipKeys = new ArrayList<>();

    // kaynak durumu
    private final Map<String, Integer> counts = new LinkedHashMap<>();
    private final Map<String, Boolean> loading = new LinkedHashMap<>();
    private final Map<String, String> errors = new LinkedHashMap<>();

    // birleşik gruplar (isim → grup) — yalnız seçili kaynağın sonuçları
    private final Map<String, AppGroup> groups = new LinkedHashMap<>();
    private String lastQuery = "";
    private int archivePage = 0;

    public SearchScreen(MainActivity main) {
        this.main = main;
        root = new LinearLayout(main);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.CANVAS);

        // Büyük başlık (mağaza)
        TextView title = Ui.bigTitle(main, "EskiDroid");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(Ui.dp(20), Ui.dp(14), Ui.dp(20), 0);
        title.setLayoutParams(lp);
        root.addView(title);

        TextView sub = Ui.text(main, L.s(R.string.search_subtitle), Ui.FOG, 13, 0);
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(-1, -2);
        lp2.setMargins(Ui.dp(20), Ui.dp(4), Ui.dp(20), Ui.dp(14));
        sub.setLayoutParams(lp2);
        root.addView(sub);

        // Arama kutusu: pil (App Store arama alanı)
        LinearLayout box = new LinearLayout(main);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Ui.ELEVATED);
        g.setCornerRadius(Ui.dp(12));
        box.setBackground(g);
        box.setPadding(Ui.dp(12), Ui.dp(2), Ui.dp(8), Ui.dp(2));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, Ui.dp(46));
        blp.setMargins(Ui.dp(16), 0, Ui.dp(16), Ui.dp(12));
        box.setLayoutParams(blp);

        ImageView searchIcon = new ImageView(main);
        searchIcon.setImageDrawable(Icons.search(main, Ui.ASH));
        searchIcon.setContentDescription(L.s(R.string.tab_search));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Ui.dp(22), Ui.dp(22));
        ilp.rightMargin = Ui.dp(8);
        searchIcon.setLayoutParams(ilp);
        box.addView(searchIcon);

        query = new EditText(main);
        query.setHint(L.s(R.string.search_hint));
        query.setHintTextColor(Ui.ASH);
        query.setTextColor(Ui.INK);
        query.setTextSize(16);
        query.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        query.setBackground(null);
        query.setLayoutParams(new LinearLayout.LayoutParams(0, -1, 1f));
        query.setOnEditorActionListener((v, actionId, ev) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                runSearch();
                return true;
            }
            return false;
        });
        box.addView(query);

        final ImageView clear = new ImageView(main);
        clear.setImageDrawable(Icons.close(main, Ui.FOG));
        clear.setContentDescription(L.s(R.string.clear_search));
        clear.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(38), Ui.dp(38)));
        clear.setVisibility(View.GONE);
        clear.setOnClickListener(v -> query.setText(""));
        box.addView(clear);
        query.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                clear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
            }
        });
        root.addView(box);

        // Sonuç listesi
        results = new LinearLayout(main);
        results.setOrientation(LinearLayout.VERTICAL);
        results.setPadding(0, Ui.dp(4), 0, Ui.dp(120));
        ScrollView scroll = new ScrollView(main);
        scroll.setFillViewport(true);
        scroll.addView(results, new FrameLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, 0, 1f);
        scroll.setLayoutParams(slp);
        root.addView(scroll);

        render();
    }

    public View view() {
        return root;
    }

    public void onShow() {
    }

    // ============================ kaynak seçici ============================

    /** Kaynak seçimi yoksa: üç büyük mağaza kategorisi kartı. */
    private void renderSourcePicker() {
        results.removeAllViews();

        TextView ask = Ui.text(main, L.s(R.string.pick_source), Ui.INK, 17, 1);
        ask.setPadding(Ui.dp(20), Ui.dp(10), Ui.dp(20), 0);
        results.addView(ask);

        TextView hint = Ui.text(main, L.s(R.string.pick_source_hint), Ui.FOG, 12, 0);
        hint.setPadding(Ui.dp(20), Ui.dp(4), Ui.dp(20), Ui.dp(12));
        results.addView(hint);

        List<View> cards = new ArrayList<>();
        cards.add(sourceCard("fdroid", "F-Droid", L.s(R.string.src_fdroid_desc)));
        cards.add(sourceCard("archive", L.s(R.string.filter_archive), L.s(R.string.src_archive_desc)));
        cards.add(sourceCard("github", "GitHub", L.s(R.string.src_github_desc)));
        for (View c : cards) results.addView(c);
        UiFx.stagger(main, cards);
    }

    /** Büyük kaynak kartı: renkli ikon + ad + açıklama + ok. */
    private View sourceCard(final String value, String name, String desc) {
        LinearLayout card = Ui.card(main);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(Ui.dp(16), Ui.dp(8), Ui.dp(16), Ui.dp(0));
        card.setLayoutParams(lp);

        LinearLayout row = new LinearLayout(main);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = Ui.appIcon(main, 48);
        icon.setImageBitmap(sourceAvatar(value, Ui.dp(48)));
        row.addView(icon);

        LinearLayout mid = new LinearLayout(main);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setPadding(Ui.dp(14), 0, Ui.dp(8), 0);
        mid.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        mid.addView(Ui.text(main, name, Ui.INK, 16, 1));
        TextView d = Ui.text(main, desc, Ui.FOG, 12, 0);
        d.setMaxLines(2);
        d.setEllipsize(TextUtils.TruncateAt.END);
        mid.addView(d);
        row.addView(mid);

        ImageView chev = new ImageView(main);
        chev.setImageDrawable(Icons.chevronRight(main, Ui.ASH));
        chev.setContentDescription(name);
        row.addView(chev);

        card.addView(row);
        card.setOnClickListener(v -> {
            UiFx.tap(main);
            source = value;
            buildChips();
            updateChips();
            if (!query.getText().toString().trim().isEmpty()) runSearch();
            else render();
        });
        UiFx.press(card);
        return card;
    }

    /** Kaynak için renkli harf ikonu (kategorileştirme). */
    private android.graphics.Bitmap sourceAvatar(String value, int px) {
        int color = Ui.BLUE;
        char ch = '?';
        if ("fdroid".equals(value)) {
            color = Ui.GREEN;
            ch = 'F';
        } else if ("archive".equals(value)) {
            color = Ui.EMBER;
            ch = 'A';
        } else if ("github".equals(value)) {
            color = Ui.SMOKE;
            ch = 'G';
        }
        android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas cv = new android.graphics.Canvas(b);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        p.setColor(Ui.tint(color, Ui.isDark() ? 0x33 : 0x24));
        float r = px * 0.22f;
        cv.drawRoundRect(0, 0, px, px, r, r, p);
        p.setColor(color);
        p.setTextSize(px * 0.5f);
        p.setTextAlign(android.graphics.Paint.Align.CENTER);
        p.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
        float ty = px / 2f - (p.descent() + p.ascent()) / 2f;
        cv.drawText(String.valueOf(ch), px / 2f, ty, p);
        return b;
    }

    // ============================ kaynak çipleri ============================

    /** Kaynak seçildikten sonra üstte geçiş çipleri. */
    private void buildChips() {
        // çipler root'a bir kez eklenir (arama kutusunun altına)
        if (chipKeys.isEmpty()) {
            HorizontalScrollView hs = new HorizontalScrollView(main);
            hs.setFillViewport(true);
            hs.setHorizontalScrollBarEnabled(false);
            LinearLayout chips = new LinearLayout(main);
            chips.setOrientation(LinearLayout.HORIZONTAL);
            chips.setPadding(Ui.dp(12), 0, Ui.dp(12), Ui.dp(4));
            hs.addView(chips);
            LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2);
            hlp.bottomMargin = Ui.dp(4);
            hs.setLayoutParams(hlp);
            addChip(chips, "F-Droid", "fdroid");
            addChip(chips, L.s(R.string.filter_archive), "archive");
            addChip(chips, "GitHub", "github");
            // arama kutusundan hemen sonra (index 2: başlık, alt başlık, kutu)
            root.addView(hs, 3);
        }
    }

    private void addChip(LinearLayout parent, String label, final String value) {
        TextView t = Ui.text(main, label, Ui.FOG, 13, 1);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Ui.ELEVATED);
        g.setCornerRadius(Ui.dp(99));
        t.setBackground(g);
        t.setPadding(Ui.dp(16), Ui.dp(7), Ui.dp(16), Ui.dp(8));
        t.setOnClickListener(v -> {
            UiFx.tap(main);
            source = value;
            updateChips();
            runSearch();
        });
        parent.addView(t);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = Ui.dp(8);
        t.setLayoutParams(lp);
        chipLabels.add(t);
        chipBgs.add(null);
        chipKeys.add(value);
    }

    private void updateChips() {
        for (int i = 0; i < chipLabels.size(); i++) {
            String key = chipKeys.get(i);
            boolean active = key.equals(source);
            Integer c = counts.get(key);
            String label = baseChipLabel(key);
            chipLabels.get(i).setText(active && c != null ? label + " · " + L.num(c) : label);
            chipLabels.get(i).setTextColor(active ? 0xFFFFFFFF : Ui.FOG);
            GradientDrawable g = new GradientDrawable();
            g.setColor(active ? Ui.BLUE : Ui.ELEVATED);
            g.setCornerRadius(Ui.dp(99));
            chipLabels.get(i).setBackground(g);
        }
    }

    private String baseChipLabel(String key) {
        switch (key) {
            case "fdroid":
                return "F-Droid";
            case "archive":
                return L.s(R.string.filter_archive);
            case "github":
                return "GitHub";
            default:
                return L.s(R.string.filter_all);
        }
    }

    // ============================ arama ============================

    private void runSearch() {
        final String q = query.getText().toString().trim();
        if (q.isEmpty()) {
            Toast.makeText(main, L.s(R.string.type_first), Toast.LENGTH_SHORT).show();
            return;
        }
        if (source == null) {
            // kaynak seçilmedi → seçiciyi göster, kullanıcıya haber ver
            renderSourcePicker();
            Toast.makeText(main, L.s(R.string.select_source), Toast.LENGTH_SHORT).show();
            return;
        }
        if (!Net.online(main)) {
            renderOffline();
            return;
        }
        lastQuery = q;
        archivePage = 0;
        groups.clear();
        counts.clear();
        errors.clear();
        loading.clear();
        loading.put(source, true);
        renderLoading();
        final String src = source;

        Net.POOL.execute(() -> {
            Sources.SourceResult r;
            if ("fdroid".equals(src)) r = Sources.searchFdroid(q);
            else if ("github".equals(src)) r = Sources.searchGithub(q);
            else r = Sources.searchArchive(q, 0);
            Sources.SourceResult rr = r;
            main.runOnUiThread(() -> onSourceDone(rr));
        });
    }

    /** Seçili kaynağın sonucu geldi. */
    private void onSourceDone(Sources.SourceResult r) {
        loading.put(r.source, false);
        if (r.error != null) errors.put(r.source, r.error);
        int n = 0;
        for (SearchItem it : r.items) {
            if (it.spam && !"archive".equals(r.source)) continue;
            AppGroup grp = groups.get(it.key());
            if (grp == null) {
                grp = new AppGroup(it.key(), it.name);
                groups.put(it.key(), grp);
            }
            // aynı kaynak içinde aynı kimlik zaten var mı?
            boolean dup = false;
            for (SearchItem x : grp.items) if (x.id.equals(it.id)) dup = true;
            if (dup) continue;
            grp.items.add(it);
            if (grp.summary == null && it.summary != null) grp.summary = it.summary;
            if (grp.iconUrl == null && it.iconUrl != null) grp.iconUrl = it.iconUrl;
            n++;
        }
        counts.put(r.source, n);
        renderResults();
    }

    /** archive.org: kaydırınca daha fazla yükle. */
    private void loadMoreArchive() {
        if (!"archive".equals(source) || Boolean.TRUE.equals(loading.get(Sources.ARCHIVE))) {
            return;
        }
        loading.put(Sources.ARCHIVE, true);
        TextView more = findMoreButton();
        if (more != null) more.setText(L.s(R.string.loading_more));
        final int page = ++archivePage;
        Net.POOL.execute(() -> {
            Sources.SourceResult r = Sources.searchArchive(lastQuery, page);
            main.runOnUiThread(() -> {
                loading.put(Sources.ARCHIVE, false);
                if (r.error != null) errors.put(Sources.ARCHIVE, r.error);
                for (SearchItem it : r.items) {
                    AppGroup grp = groups.get(it.key());
                    if (grp == null) {
                        grp = new AppGroup(it.key(), it.name);
                        groups.put(it.key(), grp);
                    } else {
                        boolean dup = false;
                        for (SearchItem x : grp.items) if (x.id.equals(it.id)) dup = true;
                        if (dup) continue;
                    }
                    grp.items.add(it);
                    if (grp.iconUrl == null && it.iconUrl != null) grp.iconUrl = it.iconUrl;
                }
                renderResults();
            });
        });
    }

    private TextView findMoreButton() {
        for (int i = results.getChildCount() - 1; i >= 0; i--) {
            View v = results.getChildAt(i);
            if (v instanceof TextView && "more".equals(v.getTag())) return (TextView) v;
        }
        return null;
    }

    // ============================ durumlar ============================

    private void render() {
        results.removeAllViews();
        if (source == null) {
            renderSourcePicker();
        } else if (groups.isEmpty() && !loading.containsValue(true)) {
            if (errors.containsKey(source)) renderError(errors.get(source));
            else renderEmpty();
        }
    }

    private void renderEmpty() {
        results.removeAllViews();
        TextView t = Ui.text(main, L.s(R.string.search_empty), Ui.FOG, 14, 0);
        t.setPadding(Ui.dp(20), Ui.dp(24), Ui.dp(20), 0);
        results.addView(t);
    }

    private void renderError(String msg) {
        results.removeAllViews();
        TextView t = Ui.text(main, L.s(R.string.error_prefix) + msg, Ui.FOG, 14, 0);
        t.setPadding(Ui.dp(20), Ui.dp(24), Ui.dp(20), Ui.dp(12));
        results.addView(t);
        addRetry();
    }

    private void renderLoading() {
        results.removeAllViews();
        updateChips();
        List<View> sk = new ArrayList<>();
        TextView label = Ui.text(main, baseChipLabel(source) + " — " + L.s(R.string.searching), Ui.FOG, 12, 1);
        label.setPadding(Ui.dp(20), Ui.dp(8), Ui.dp(20), 0);
        results.addView(label);
        View bar = Ui.skeleton(main, 10);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, Ui.dp(6));
        blp.topMargin = Ui.dp(6);
        bar.setLayoutParams(blp);
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(-1, -2);
        ml.setMargins(Ui.dp(16), Ui.dp(4), Ui.dp(16), Ui.dp(4));
        bar.setLayoutParams(ml);
        results.addView(bar);
        sk.add(bar);
        for (int i = 0; i < 5; i++) {
            View v = Ui.skeleton(main, 76);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(Ui.dp(16), Ui.dp(6), Ui.dp(16), Ui.dp(6));
            v.setLayoutParams(lp);
            results.addView(v);
            sk.add(v);
        }
        UiFx.shimmerAll(main, sk);
    }

    private void renderOffline() {
        results.removeAllViews();
        TextView t = Ui.text(main, L.s(R.string.offline), Ui.FOG, 14, 0);
        t.setPadding(Ui.dp(20), Ui.dp(24), Ui.dp(20), Ui.dp(12));
        results.addView(t);
        addRetry();
    }

    private void addRetry() {
        TextView retry = Ui.ghostButton(main, L.s(R.string.retry));
        retry.setOnClickListener(v -> runSearch());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = Ui.dp(20);
        retry.setLayoutParams(lp);
        results.addView(retry);
    }

    /** Sonuçlar: mağaza satırları; alaka sırasıyla. */
    private void renderResults() {
        results.removeAllViews();
        updateChips();

        // kaynak hatası (yalnız seçili kaynaktansa gösterilir)
        if (errors.containsKey(source) && !Boolean.TRUE.equals(loading.get(source))) {
            TextView err = Ui.text(main,
                    baseChipLabel(source) + ": " + L.s(R.string.source_error), Ui.EMBER, 11, 0);
            err.setPadding(Ui.dp(20), Ui.dp(4), Ui.dp(20), 0);
            results.addView(err);
        }

        List<AppGroup> shown = new ArrayList<>(groups.values());

        // alaka sıralaması
        final String q = lastQuery;
        java.util.Collections.sort(shown, (a, b) -> Double.compare(b.score(q), a.score(q)));

        if (shown.isEmpty()) {
            TextView t = Ui.text(main, L.s(R.string.no_results, lastQuery), Ui.FOG, 14, 0);
            t.setPadding(Ui.dp(20), Ui.dp(24), Ui.dp(20), 0);
            results.addView(t);
            return;
        }

        TextView count = Ui.text(main, L.s(R.string.result_count, shown.size()), Ui.FOG, 12, 0);
        count.setPadding(Ui.dp(20), Ui.dp(4), Ui.dp(20), Ui.dp(4));
        results.addView(count);

        List<View> cards = new ArrayList<>();
        for (AppGroup grp : shown) {
            View c = card(grp);
            results.addView(c);
            cards.add(c);
        }

        // archive.org sayfalama butonu
        if ("archive".equals(source)
                && !Boolean.TRUE.equals(loading.get(Sources.ARCHIVE))
                && counts.containsKey(Sources.ARCHIVE)) {
            TextView more = Ui.ghostButton(main, L.s(R.string.load_more_archive));
            more.setTag("more");
            UiFx.press(more);
            more.setOnClickListener(v -> {
                UiFx.tap(main);
                loadMoreArchive();
            });
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-1, -2);
            mlp.setMargins(Ui.dp(16), Ui.dp(8), Ui.dp(16), Ui.dp(8));
            more.setLayoutParams(mlp);
            results.addView(more);
        }
        UiFx.stagger(main, cards);
    }

    // ============================ sonuç kartı (mağaza satırı) ============================

    /** Mağaza satırı: 64dp yuvarlak ikon, ad, ikinci satır, özet, sağda ok. */
    private View card(final AppGroup grp) {
        LinearLayout card = Ui.card(main);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(Ui.dp(16), Ui.dp(6), Ui.dp(16), Ui.dp(6));
        card.setLayoutParams(lp);

        LinearLayout row = new LinearLayout(main);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = Ui.appIcon(main, 64);
        icon.setContentDescription(grp.name);
        ImageLoader.get(main).load(grp.iconUrl, 64, icon, grp.name);
        row.addView(icon);

        LinearLayout mid = new LinearLayout(main);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setPadding(Ui.dp(14), 0, Ui.dp(8), 0);
        mid.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

        TextView name = Ui.text(main, grp.name, Ui.INK, 16, 1);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        mid.addView(name);

        // ikinci satır: kaynak + popülerlik
        String line2 = SearchItem.sourceLabel(grp.items.get(0).source);
        for (SearchItem it : grp.items) {
            if (it.downloads > 0) {
                line2 += " · " + L.s(R.string.downloads_count, L.num(it.downloads));
                break;
            }
            if (it.stars >= 0) {
                line2 += " · ★ " + L.num(it.stars);
                break;
            }
        }
        TextView l2 = Ui.text(main, line2, Ui.FOG, 12, 0);
        l2.setSingleLine(true);
        l2.setEllipsize(TextUtils.TruncateAt.END);
        mid.addView(l2);

        if (grp.summary != null && !grp.summary.isEmpty()) {
            TextView sm = Ui.text(main, grp.summary, Ui.FOG, 12, 0);
            sm.setMaxLines(2);
            sm.setEllipsize(TextUtils.TruncateAt.END);
            sm.setPadding(0, Ui.dp(4), 0, 0);
            mid.addView(sm);
        }
        row.addView(mid);

        ImageView chev = new ImageView(main);
        chev.setImageDrawable(Icons.chevronRight(main, Ui.ASH));
        chev.setContentDescription(L.s(R.string.versions));
        row.addView(chev);

        card.addView(row);

        // Spam işaretli arşiv öğeleri küçük uyarı
        boolean anySpam = false;
        for (SearchItem it : grp.items) if (it.spam) anySpam = true;
        if (anySpam) {
            TextView warn = Ui.badgeFill(main, L.s(R.string.untrusted_source), Ui.EMBER);
            warn.setTextColor(Ui.EMBER);
            LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(-2, -2);
            wlp.topMargin = Ui.dp(8);
            warn.setLayoutParams(wlp);
            card.addView(warn);
        }

        card.setOnClickListener(v -> {
            UiFx.tap(main);
            main.openDetail(grp);
        });
        UiFx.press(card);
        return card;
    }
}
