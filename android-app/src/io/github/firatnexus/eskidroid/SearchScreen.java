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
import android.text.InputType;
import android.util.TypedValue;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ara ekranı: başlık, arama pili (✕ temizleme, IME arama), kaynak çipleri
 * (Tümü / F-Droid / Archive.org), sonuç kartları (ikon, temiz ad, sürüm pili,
 * kaynak, güvenilmez-kaynak işareti).
 */
public final class SearchScreen {

    private final MainActivity main;
    private final LinearLayout root;
    private final LinearLayout results;
    private final EditText query;
    private String source = "all";
    private final List<TextView> chipLabels = new ArrayList<>();
    private final List<View> chipLines = new ArrayList<>();

    public SearchScreen(MainActivity main) {
        this.main = main;
        root = new LinearLayout(main);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.CANVAS);

        // Başlık
        TextView title = Ui.text(main, "EskiDroid", Ui.WHITE, 26, 2);
        title.setLetterSpacing(-0.04f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(Ui.dp(20), Ui.dp(16), Ui.dp(20), 0);
        title.setLayoutParams(lp);
        root.addView(title);

        TextView sub = Ui.text(main, "Eski Android uygulamaları — ara, indir, yamala, kur", Ui.GRAPHITE, 13, 0);
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(-1, -2);
        lp2.setMargins(Ui.dp(20), Ui.dp(4), Ui.dp(20), Ui.dp(14));
        sub.setLayoutParams(lp2);
        root.addView(sub);

        // Arama kutusu: #131313 pil, ikon + alan + temizle
        LinearLayout box = new LinearLayout(main);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Ui.ELEVATED);
        g.setCornerRadius(Ui.dp(99));
        box.setBackground(g);
        box.setPadding(Ui.dp(16), Ui.dp(4), Ui.dp(8), Ui.dp(4));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, Ui.dp(48));
        blp.setMargins(Ui.dp(16), 0, Ui.dp(16), Ui.dp(12));
        box.setLayoutParams(blp);

        ImageView searchIcon = new ImageView(main);
        searchIcon.setImageDrawable(Icons.search(main, Ui.GRAPHITE));
        searchIcon.setContentDescription("Ara");
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Ui.dp(24), Ui.dp(24));
        ilp.rightMargin = Ui.dp(10);
        searchIcon.setLayoutParams(ilp);
        box.addView(searchIcon);

        query = new EditText(main);
        query.setHint("Oyun veya uygulama adı…");
        query.setHintTextColor(Ui.GRAPHITE);
        query.setTextColor(Ui.WHITE);
        query.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        query.setInputType(InputType.TYPE_CLASS_TEXT);
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
        clear.setImageDrawable(Icons.close(main, Ui.GRAPHITE));
        clear.setContentDescription("Aramayı temizle");
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

        // Kaynak çipleri (ghost pil; etkin = beyaz yazı + 1dp mavi alt çizgi)
        HorizontalScrollView hs = new HorizontalScrollView(main);
        hs.setFillViewport(true);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(main);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(Ui.dp(12), 0, Ui.dp(12), Ui.dp(6));
        hs.addView(chips);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2);
        hs.setLayoutParams(hlp);
        addChip(chips, "Tümü", "all");
        addChip(chips, "F-Droid", "fdroid");
        addChip(chips, "Archive.org", "archive");
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
        TextView t = Ui.text(main, label, Ui.GRAPHITE, 13, 1);
        col.addView(t);
        View line = new View(main);
        line.setLayoutParams(new LinearLayout.LayoutParams(-1, Ui.dp(2)));
        line.setBackgroundColor(Ui.BLUE);
        line.setVisibility(View.INVISIBLE);
        col.addView(line);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        col.setLayoutParams(lp);
        col.setOnClickListener(v -> {
            source = value;
            updateChips();
            runSearch();
        });
        parent.addView(col);
        chipLabels.add(t);
        chipLines.add(line);
    }

    private void updateChips() {
        String[] vals = {"all", "fdroid", "archive"};
        for (int i = 0; i < chipLabels.size(); i++) {
            boolean active = vals[i].equals(source);
            chipLabels.get(i).setTextColor(active ? Ui.WHITE : Ui.GRAPHITE);
            chipLines.get(i).setVisibility(active ? View.VISIBLE : View.INVISIBLE);
        }
    }

    // ============================ arama ============================

    private void runSearch() {
        final String q = query.getText().toString().trim();
        if (q.isEmpty()) {
            Toast.makeText(main, "Önce aramak istediğin adı yaz", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!Net.online(main)) {
            renderOffline();
            return;
        }
        renderSkeleton();
        final String src = source;
        Net.POOL.execute(() -> {
            List<SearchItem> found = new ArrayList<>();
            if (!"archive".equals(src)) {
                try {
                    found.addAll(searchFdroid(q));
                } catch (Exception ignored) {
                }
            }
            if (!"fdroid".equals(src)) {
                try {
                    found.addAll(searchArchive(q));
                } catch (Exception ignored) {
                }
            }
            final List<SearchItem> f = found;
            main.runOnUiThread(() -> renderResults(f, q));
        });
    }

    private static List<SearchItem> searchFdroid(String q) throws Exception {
        String url = "https://f-droid.org/en/search?count=25&fdfilter=" + URLEncoder.encode(q, "UTF-8");
        String html = Net.get(url);
        Pattern block = Pattern.compile(
                "<a class=\"package-header\" href=\"/en/packages/([a-zA-Z0-9_.]+)/\">(.*?)</a>",
                Pattern.DOTALL);
        Matcher m = block.matcher(html);
        List<SearchItem> out = new ArrayList<>();
        while (m.find() && out.size() < 20) {
            SearchItem it = new SearchItem();
            it.id = m.group(1);
            it.source = "fdroid";
            String inner = m.group(2);
            Matcher img = Pattern.compile("src=\"([^\"]+)\"").matcher(inner);
            if (img.find()) it.iconUrl = img.group(1);
            Matcher nm = Pattern.compile("<h4 class=\"package-name\">(.*?)</h4>", Pattern.DOTALL).matcher(inner);
            if (nm.find()) it.name = stripTags(nm.group(1));
            Matcher sm = Pattern.compile("<span class=\"package-summary\">(.*?)</span>", Pattern.DOTALL).matcher(inner);
            if (sm.find()) it.summary = stripTags(sm.group(1));
            if (it.name == null || it.name.isEmpty()) it.name = NameUtil.prettyPkg(it.id);
            out.add(it);
        }
        return out;
    }

    private static String stripTags(String s) {
        String r = s.replaceAll("<[^>]+>", "");
        r = r.replace("&amp;", "&")
                .replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&nbsp;", " ");
        r = r.trim();
        return r.replaceAll("\\s+", " ");
    }

    private static List<SearchItem> searchArchive(String q) throws Exception {
        String url = "https://archive.org/advancedsearch.php?q=" +
                URLEncoder.encode("collection:(apkarchive) AND title:(" + q + ")", "UTF-8") +
                "&fl[]=identifier&fl[]=title&fl[]=downloads&rows=25&sort[]=downloads+desc&output=json";
        JSONObject obj = new JSONObject(Net.get(url));
        JSONArray docs = obj.getJSONObject("response").getJSONArray("docs");
        List<SearchItem> out = new ArrayList<>();
        for (int i = 0; i < docs.length(); i++) {
            JSONObject d = docs.getJSONObject(i);
            SearchItem it = new SearchItem();
            it.id = d.optString("identifier");
            String title = d.optString("title");
            String[] nv = NameUtil.clean(title.isEmpty() ? it.id : title);
            it.name = nv[0];
            it.version = nv[1];
            it.source = "archive";
            it.spam = NameUtil.spam(it.id, title);
            it.iconUrl = "https://archive.org/services/img/" + it.id;
            out.add(it);
        }
        // Spam görünümlü kaynaklar alta atılır
        Collections.sort(out, (a, b) -> Boolean.compare(a.spam, b.spam));
        return out;
    }

    // ============================ durumlar ============================

    private void renderEmpty() {
        results.removeAllViews();
        TextView t = Ui.text(main, "Bir oyun ya da uygulama adı yaz ve ara.\nSonuçlar F-Droid ve archive.org'tan gelir.",
                Ui.GRAPHITE, 14, 0);
        t.setPadding(Ui.dp(20), Ui.dp(24), Ui.dp(20), 0);
        results.addView(t);
    }

    private void renderSkeleton() {
        results.removeAllViews();
        for (int i = 0; i < 5; i++) {
            View v = Ui.skeleton(main, 72);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(Ui.dp(12), Ui.dp(6), Ui.dp(12), Ui.dp(6));
            v.setLayoutParams(lp);
            results.addView(v);
        }
        TextView st = Ui.text(main, "Aranıyor…", Ui.GRAPHITE, 12, 0);
        st.setPadding(Ui.dp(20), Ui.dp(8), Ui.dp(20), 0);
        results.addView(st);
    }

    private void renderOffline() {
        results.removeAllViews();
        TextView t = Ui.text(main, "İnternet bağlantısı yok.\nDepo sekmesi çevrimdışı çalışır.",
                Ui.GRAPHITE, 14, 0);
        t.setPadding(Ui.dp(20), Ui.dp(24), Ui.dp(20), Ui.dp(12));
        results.addView(t);
        addRetry();
    }

    private void renderError(String msg) {
        results.removeAllViews();
        TextView t = Ui.text(main, "Hata: " + msg, Ui.GRAPHITE, 14, 0);
        t.setPadding(Ui.dp(20), Ui.dp(24), Ui.dp(20), Ui.dp(12));
        results.addView(t);
        addRetry();
    }

    private void addRetry() {
        TextView retry = Ui.ghostPill(main, "Tekrar dene");
        retry.setOnClickListener(v -> runSearch());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = Ui.dp(20);
        retry.setLayoutParams(lp);
        results.addView(retry);
    }

    private void renderResults(List<SearchItem> found, String q) {
        results.removeAllViews();
        if (found.isEmpty()) {
            TextView t = Ui.text(main, "Sonuç bulunamadı: “" + q + "”", Ui.GRAPHITE, 14, 0);
            t.setPadding(Ui.dp(20), Ui.dp(24), Ui.dp(20), 0);
            results.addView(t);
            return;
        }
        TextView count = Ui.text(main, found.size() + " sonuç", Ui.GRAPHITE, 12, 0);
        count.setPadding(Ui.dp(20), Ui.dp(4), Ui.dp(20), Ui.dp(4));
        results.addView(count);
        for (SearchItem it : found) results.addView(card(it));
    }

    // ============================ sonuç kartı ============================

    private View card(final SearchItem it) {
        LinearLayout card = Ui.card(main);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(Ui.dp(12), Ui.dp(6), Ui.dp(12), Ui.dp(6));
        card.setLayoutParams(lp);

        LinearLayout row = new LinearLayout(main);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = Ui.circularImage(main, 48);
        icon.setContentDescription(it.name);
        ImageLoader.get(main).load(it.iconUrl, 48, icon, it.name);
        row.addView(icon);

        LinearLayout mid = new LinearLayout(main);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setPadding(Ui.dp(12), 0, Ui.dp(8), 0);
        mid.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout nameRow = new LinearLayout(main);
        nameRow.setOrientation(LinearLayout.HORIZONTAL);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = Ui.text(main, it.name, Ui.WHITE, 15, 1);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        nameRow.addView(name);
        if (it.version != null && !it.version.isEmpty()) {
            TextView vp = Ui.miniPill(main, it.version, Ui.MIST, Ui.SMOKE);
            LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(-2, -2);
            vlp.leftMargin = Ui.dp(8);
            vp.setLayoutParams(vlp);
            nameRow.addView(vp);
        }
        mid.addView(nameRow);

        TextView src = Ui.text(main, it.sourceLabel(), Ui.GRAPHITE, 12, 0);
        src.setSingleLine(true);
        src.setEllipsize(TextUtils.TruncateAt.END);
        mid.addView(src);

        if (it.summary != null && !it.summary.isEmpty()) {
            TextView sm = Ui.text(main, it.summary, Ui.MIST, 12, 0);
            sm.setMaxLines(2);
            sm.setEllipsize(TextUtils.TruncateAt.END);
            mid.addView(sm);
        }
        row.addView(mid);

        ImageView chev = new ImageView(main);
        chev.setImageDrawable(Icons.chevronRight(main, Ui.MIST));
        chev.setContentDescription("Sürümler");
        row.addView(chev);

        card.addView(row);
        if (it.spam) {
            TextView warn = Ui.miniPill(main, "Güvenilmez kaynak", Ui.EMBER, Ui.EMBER);
            LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(-2, -2);
            wlp.topMargin = Ui.dp(8);
            warn.setLayoutParams(wlp);
            card.addView(warn);
        }
        card.setOnClickListener(v -> main.openVersions(it));
        return card;
    }
}
