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
 * Ara ekranı: başlık, arama kutusu, kaynak çipleri (Tümü / F-Droid /
 * Internet Archive / GitHub — seçili çipte sonuç sayısı), kaynak başına ayrı
 * yükleniyor göstergesi, hata mesajı (diğer kaynaklar sürer), sonuç kartları
 * (uygulama adına göre gruplu, kaynak rozetli, alaka sıralı) ve
 * archive.org için "daha fazla yükle" sayfalama.
 */
public final class SearchScreen {

    private final MainActivity main;
    private final LinearLayout root;
    private final LinearLayout results;
    private final EditText query;
    private String source = "all";
    private final List<TextView> chipLabels = new ArrayList<>();
    private final List<View> chipLines = new ArrayList<>();
    private final List<String> chipKeys = new ArrayList<>();

    // kaynak durumu
    private final Map<String, Integer> counts = new LinkedHashMap<>();
    private final Map<String, Boolean> loading = new LinkedHashMap<>();
    private final Map<String, String> errors = new LinkedHashMap<>();

    // birleşik gruplar (isim → grup)
    private final Map<String, AppGroup> groups = new LinkedHashMap<>();
    private String lastQuery = "";
    private int archivePage = 0;

    public SearchScreen(MainActivity main) {
        this.main = main;
        root = new LinearLayout(main);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.CANVAS);

        // Başlık
        TextView title = Ui.text(main, "EskiDroid", Ui.INK, 26, 2);
        title.setLetterSpacing(-0.04f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(Ui.dp(20), Ui.dp(16), Ui.dp(20), 0);
        title.setLayoutParams(lp);
        root.addView(title);

        TextView sub = Ui.text(main, L.s(R.string.search_subtitle), Ui.FOG, 13, 0);
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(-1, -2);
        lp2.setMargins(Ui.dp(20), Ui.dp(4), Ui.dp(20), Ui.dp(14));
        sub.setLayoutParams(lp2);
        root.addView(sub);

        // Arama kutusu: Paper yüzey, 6dp köşe (Things giriş alanı)
        LinearLayout box = new LinearLayout(main);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Ui.ELEVATED);
        g.setCornerRadius(Ui.dp(6));
        g.setStroke(Math.max(1, Ui.dp(1)), Ui.HAIRLINE);
        box.setBackground(g);
        box.setPadding(Ui.dp(14), Ui.dp(4), Ui.dp(8), Ui.dp(4));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, Ui.dp(48));
        blp.setMargins(Ui.dp(16), 0, Ui.dp(16), Ui.dp(12));
        box.setLayoutParams(blp);

        ImageView searchIcon = new ImageView(main);
        searchIcon.setImageDrawable(Icons.search(main, Ui.ASH));
        searchIcon.setContentDescription(L.s(R.string.tab_search));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Ui.dp(24), Ui.dp(24));
        ilp.rightMargin = Ui.dp(10);
        searchIcon.setLayoutParams(ilp);
        box.addView(searchIcon);

        query = new EditText(main);
        query.setHint(L.s(R.string.search_hint));
        query.setHintTextColor(Ui.FOG);
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
        clear.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(40), Ui.dp(40)));
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

        // Kaynak çipleri: Tümü / F-Droid / Internet Archive / GitHub
        HorizontalScrollView hs = new HorizontalScrollView(main);
        hs.setFillViewport(true);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(main);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(Ui.dp(12), 0, Ui.dp(12), Ui.dp(6));
        hs.addView(chips);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2);
        hs.setLayoutParams(hlp);
        addChip(chips, L.s(R.string.filter_all), "all");
        addChip(chips, "F-Droid", "fdroid");
        addChip(chips, L.s(R.string.filter_archive), "archive");
        addChip(chips, "GitHub", "github");
        root.addView(hs);
        updateChips();

        // Sonuç listesi
        results = new LinearLayout(main);
        results.setOrientation(LinearLayout.VERTICAL);
        results.setPadding(Ui.dp(4), Ui.dp(4), Ui.dp(4), Ui.dp(120));
        ScrollView scroll = new ScrollView(main);
        scroll.setFillViewport(true);
        scroll.addView(results, new FrameLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, 0, 1f);
        scroll.setLayoutParams(slp);
        root.addView(scroll);

        renderEmpty();
    }

    public View view() {
        return root;
    }

    public void onShow() {
    }

    private void addChip(LinearLayout parent, String label, final String value) {
        LinearLayout col = new LinearLayout(main);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(Ui.dp(12), Ui.dp(6), Ui.dp(12), Ui.dp(2));
        TextView t = Ui.text(main, label, Ui.FOG, 13, 1);
        col.addView(t);
        View line = new View(main);
        line.setLayoutParams(new LinearLayout.LayoutParams(-1, Ui.dp(2)));
        line.setBackgroundColor(Ui.BLUE);
        line.setVisibility(View.INVISIBLE);
        col.addView(line);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        col.setLayoutParams(lp);
        col.setOnClickListener(v -> {
            UiFx.tap(main);
            source = value;
            updateChips();
            runSearch();
        });
        parent.addView(col);
        chipLabels.add(t);
        chipLines.add(line);
        chipKeys.add(value);
    }

    private void updateChips() {
        for (int i = 0; i < chipLabels.size(); i++) {
            String key = chipKeys.get(i);
            boolean active = key.equals(source);
            Integer c = counts.get(key);
            String label = baseChipLabel(key);
            chipLabels.get(i).setText(active && c != null ? label + " (" + c + ")" : label);
            chipLabels.get(i).setTextColor(active ? Ui.INK : Ui.FOG);
            chipLines.get(i).setVisibility(active ? View.VISIBLE : View.INVISIBLE);
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
        if (!Net.online(main)) {
            renderOffline();
            return;
        }
        lastQuery = q;
        archivePage = 0;
        groups.clear();
        counts.clear();
        errors.clear();
        loading.put(Sources.FDROID, !"archive".equals(source) && !"github".equals(source));
        loading.put(Sources.ARCHIVE, !"fdroid".equals(source) && !"github".equals(source));
        loading.put(Sources.GITHUB, !"fdroid".equals(source) && !"archive".equals(source));
        renderLoading();
        final String src = source;

        if (loading.get(Sources.FDROID)) Net.POOL.execute(() -> {
            Sources.SourceResult r = Sources.searchFdroid(q);
            main.runOnUiThread(() -> onSourceDone(r));
        });
        if (loading.get(Sources.ARCHIVE)) Net.POOL.execute(() -> {
            Sources.SourceResult r = Sources.searchArchive(q, 0);
            main.runOnUiThread(() -> onSourceDone(r));
        });
        if (loading.get(Sources.GITHUB)) Net.POOL.execute(() -> {
            Sources.SourceResult r = Sources.searchGithub(q);
            main.runOnUiThread(() -> onSourceDone(r));
        });
    }

    /** Bir kaynağın sonucu geldi: diğerlerinden bağımsız işlenir. */
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
        if (!loading.containsKey(Sources.ARCHIVE) || Boolean.TRUE.equals(loading.get(Sources.ARCHIVE))) {
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
                        // aynı öğe zaten var mı?
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

    private void renderEmpty() {
        results.removeAllViews();
        TextView t = Ui.text(main, L.s(R.string.search_empty), Ui.FOG, 14, 0);
        t.setPadding(Ui.dp(20), Ui.dp(24), Ui.dp(20), 0);
        results.addView(t);
    }

    private void renderLoading() {
        results.removeAllViews();
        updateChips();
        // kaynak başına ayrı yükleniyor göstergesi
        List<View> sk = new ArrayList<>();
        for (Map.Entry<String, Boolean> e : loading.entrySet()) {
            if (!Boolean.TRUE.equals(e.getValue())) continue;
            LinearLayout row = new LinearLayout(main);
            row.setOrientation(LinearLayout.VERTICAL);
            TextView label = Ui.text(main, baseChipLabel(e.getKey()) + " — " + L.s(R.string.searching), Ui.FOG, 12, 1);
            row.addView(label);
            View bar = Ui.skeleton(main, 14);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, Ui.dp(8));
            blp.topMargin = Ui.dp(6);
            bar.setLayoutParams(blp);
            row.addView(bar);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(Ui.dp(16), Ui.dp(8), Ui.dp(16), Ui.dp(4));
            row.setLayoutParams(lp);
            results.addView(row);
            sk.add(bar);
        }
        for (int i = 0; i < 3 && sk.size() < 6; i++) {
            View v = Ui.skeleton(main, 72);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(Ui.dp(12), Ui.dp(6), Ui.dp(12), Ui.dp(6));
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

    /** Sonuçlar: gruplar alaka sırasıyla; kaynak hataları küçük mesajla. */
    private void renderResults() {
        results.removeAllViews();
        updateChips();

        // hata mesajları (diğer kaynaklar sürer)
        for (Map.Entry<String, String> e : errors.entrySet()) {
            if (Boolean.TRUE.equals(loading.get(e.getKey()))) continue;
            TextView err = Ui.text(main,
                    baseChipLabel(e.getKey()) + ": " + L.s(R.string.source_error), Ui.EMBER, 11, 0);
            err.setPadding(Ui.dp(20), Ui.dp(4), Ui.dp(20), 0);
            results.addView(err);
        }

        // seçili kaynağa göre filtrele
        List<AppGroup> shown = new ArrayList<>();
        for (AppGroup grp : groups.values()) {
            if (!"all".equals(source)) {
                boolean has = false;
                for (SearchItem it : grp.items) if (it.source.equals(source)) has = true;
                if (!has) continue;
            }
            shown.add(grp);
        }

        // alaka sıralaması
        final String q = lastQuery;
        java.util.Collections.sort(shown, (a, b) -> Double.compare(b.score(q), a.score(q)));

        if (shown.isEmpty() && allDone()) {
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
        if (("all".equals(source) || "archive".equals(source))
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
            mlp.setMargins(Ui.dp(12), Ui.dp(6), Ui.dp(12), Ui.dp(6));
            more.setLayoutParams(mlp);
            results.addView(more);
        }
        UiFx.stagger(main, cards);
    }

    private boolean allDone() {
        for (Boolean b : loading.values()) if (Boolean.TRUE.equals(b)) return false;
        return true;
    }

    // ============================ sonuç kartı ============================

    /** Grup kartı: ikon, ad, kaynak rozetleri (F-Droid / Archive / GitHub), özet. */
    private View card(final AppGroup grp) {
        LinearLayout card = Ui.card(main);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(Ui.dp(12), Ui.dp(6), Ui.dp(12), Ui.dp(6));
        card.setLayoutParams(lp);

        LinearLayout row = new LinearLayout(main);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = Ui.appIcon(main, 48);
        icon.setContentDescription(grp.name);
        ImageLoader.get(main).load(grp.iconUrl, 48, icon, grp.name);
        row.addView(icon);

        LinearLayout mid = new LinearLayout(main);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setPadding(Ui.dp(12), 0, Ui.dp(8), 0);
        mid.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

        TextView name = Ui.text(main, grp.name, Ui.INK, 15, 1);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        mid.addView(name);

        // kaynak rozetleri
        LinearLayout badges = new LinearLayout(main);
        badges.setOrientation(LinearLayout.HORIZONTAL);
        badges.setPadding(0, Ui.dp(4), 0, 0);
        for (String src : grp.sources()) {
            badges.addView(margin(Ui.badge(main, SearchItem.sourceLabel(src), Ui.BLUE, Ui.BLUE), 0, 0, 6, 0));
        }
        mid.addView(badges);

        if (grp.summary != null && !grp.summary.isEmpty()) {
            TextView sm = Ui.text(main, grp.summary, Ui.FOG, 12, 0);
            sm.setMaxLines(2);
            sm.setEllipsize(TextUtils.TruncateAt.END);
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
            TextView warn = Ui.badge(main, L.s(R.string.untrusted_source), Ui.EMBER, Ui.EMBER);
            LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(-2, -2);
            wlp.topMargin = Ui.dp(8);
            warn.setLayoutParams(wlp);
            card.addView(warn);
        }

        // grup içindeki arşiv sürümü (varsa) küçük pil
        String ver = null;
        for (SearchItem it : grp.items) {
            if (it.version != null && !it.version.isEmpty()) {
                ver = it.version;
                break;
            }
        }
        if (ver != null) {
            TextView vp = Ui.badge(main, ver, Ui.FOG, Ui.HAIRLINE);
            LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(-2, -2);
            vlp.topMargin = Ui.dp(8);
            vp.setLayoutParams(vlp);
            card.addView(vp);
        }

        card.setOnClickListener(v -> {
            UiFx.tap(main);
            main.openDetail(grp);
        });
        UiFx.press(card);
        return card;
    }

    private static View margin(View v, int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(Ui.dp(l), Ui.dp(t), Ui.dp(r), Ui.dp(b));
        v.setLayoutParams(lp);
        return v;
    }
}
