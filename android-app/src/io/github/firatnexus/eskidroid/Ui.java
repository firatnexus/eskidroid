package io.github.firatnexus.eskidroid;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * GitHub tasarım dili (v1.6): "cosmic command deck" — near-black zemin
 * (#0d1117), yarı saydam cam kartlar (24dp köşe, hairline #21262d kenarlık,
 * gölge YOK — yükseklik translukansla), tek sıcak aksan Terminal Green
 * (#08872b) yalnız birincil eylemde, Sky (#8dd6ff) bağlantı/aksan, menekşe
 * (#8c93fb) seçili kart kenarı. İki biçim sözlüğü: 6dp köşeli dolgulu
 * butonlar + 60dp pil etiketler. Mikro-etiketler monospace + BÜYÜK harf.
 * Mona Sans yoksa sistem sans-serif (yaklaşık Inter) kullanılır.
 * Tema değişimi Ui.applyTheme() ile: statik alanlar güncellenir.
 */
public final class Ui {

    // ---- GitHub koyu tema (varsayılan) ----
    public static int CANVAS = 0xFF0D1117;     // Deep Void: sayfa zemini
    public static int CARD = 0x0FFFFFFF;       // Glass: rgba(255,255,255,0.06)
    public static int ELEVATED = 0xFF151A22;   // Obsidian: giriş alanları
    public static int OVERLAY = 0xB3000000;    // %70 siyah
    public static int INK = 0xFFFFFFFF;        // Snow: ana metin
    public static int FOG = 0xFFA4AEA6;        // Pearl: ikincil metin
    public static int ASH = 0xFF9EA0A2;        // Ash: ipucu/placeholder
    public static int SMOKE = 0xFFE6EDF3;      // parlak gövde metni
    public static int HAIRLINE = 0xFF21262D;   // Slate Edge: ayraçlar
    public static int BLUE = 0xFF8DD6FF;       // Sky: bağlantı/aksan
    public static int SKY = 0xFF8DD6FF;
    public static int GREEN = 0xFF3FB950;      // başarı durumu (rozet)
    public static int EMBER = 0xFFD29922;      // uyarı
    public static int RED = 0xFFF85149;        // tehlike
    // yeni tokenlar
    public static int TERMINAL = 0xFF08872B;   // Terminal Green: TEK birincil CTA
    public static int ULTRAVIOLET = 0xFF8C93FB; // seçili/öne çıkan kart kenarı
    public static int PHOSPHOR = 0xFF5FED83;   // yeşil vurgu dolgusu
    public static int CARD_BORDER = 0x1AFFFFFF; // rgba(255,255,255,0.10)
    public static int PILL_BORDER = 0x4DFFFFFF; // rgba(255,255,255,0.30) — pil kenarı
    // eski uyumluluk adları
    public static int WHITE = INK;
    public static int GRAPHITE = FOG;
    public static int MIST = FOG;
    public static int PALE = INK;

    private static DisplayMetrics metrics;
    private static boolean dark = true;

    private Ui() {
    }

    public static void init(Context ctx) {
        metrics = ctx.getResources().getDisplayMetrics();
        L.init(ctx);
        applyTheme(Prefs.get(ctx).dark(ctx));
    }

    public static boolean isDark() {
        return dark;
    }

    /** Tema uygulaması: statik token alanları yeniden atanır. */
    public static void applyTheme(boolean d) {
        dark = d;
        if (d) {
            // GitHub Dark
            CANVAS = 0xFF0D1117;
            CARD = 0x0FFFFFFF;
            ELEVATED = 0xFF151A22;
            OVERLAY = 0xB3000000;
            INK = 0xFFFFFFFF;
            FOG = 0xFFA4AEA6;
            ASH = 0xFF9EA0A2;
            SMOKE = 0xFFE6EDF3;
            HAIRLINE = 0xFF21262D;
            BLUE = 0xFF8DD6FF;
            SKY = 0xFF8DD6FF;
            GREEN = 0xFF3FB950;
            EMBER = 0xFFD29922;
            RED = 0xFFF85149;
            TERMINAL = 0xFF08872B;
            ULTRAVIOLET = 0xFF8C93FB;
            PHOSPHOR = 0xFF5FED83;
            CARD_BORDER = 0x1AFFFFFF;
            PILL_BORDER = 0x4DFFFFFF;
        } else {
            // GitHub Light (aynı yapı: beyaz zemin, kenarlıkla yükseklik)
            CANVAS = 0xFFFFFFFF;
            CARD = 0xFFFFFFFF;
            ELEVATED = 0xFFF6F8FA;
            OVERLAY = 0x66000000;
            INK = 0xFF1F2328;
            FOG = 0xFF656D76;
            ASH = 0xFF8C959F;
            SMOKE = 0xFF24292F;
            HAIRLINE = 0xFFD0D7DE;
            BLUE = 0xFF0969DA;
            SKY = 0xFF0969DA;
            GREEN = 0xFF1A7F37;
            EMBER = 0xFF9A6700;
            RED = 0xFFCF222E;
            TERMINAL = 0xFF1A7F37;
            ULTRAVIOLET = 0xFF8250DF;
            PHOSPHOR = 0xFF2DA44E;
            CARD_BORDER = 0xFFD0D7DE;
            PILL_BORDER = 0xFFD0D7DE;
        }
        WHITE = INK;
        GRAPHITE = FOG;
        MIST = FOG;
        PALE = INK;
    }

    public static int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, metrics));
    }

    public static float dpf(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, metrics);
    }

    public static TextView text(Context c, String s, int color, float sp, int style) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setLineSpacing(0, 1.5f); // GitHub okuma ritmi: 1.5 satır aralığı
        if (style == 2) t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        else if (style == 1) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    /** Mono mikro-etiket: monospace + BÜYÜK harf (GitHub nav/tag dili). */
    public static TextView mono(Context c, String s, int color, float sp) {
        TextView t = new TextView(c);
        t.setText(s.toUpperCase(Locale.ROOT));
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(Typeface.MONOSPACE);
        t.setLetterSpacing(0.015f);
        t.setLineSpacing(0, 1.5f);
        return t;
    }

    /** Ekran başlığı: 30sp medium, -0.02em (display izi, mobil ölçüsü). */
    public static TextView bigTitle(Context c, String s) {
        TextView t = text(c, s, INK, 30, 1);
        t.setLetterSpacing(-0.02f);
        t.setLineSpacing(0, 1.1f);
        return t;
    }

    /** Bölüm başlığı: 18sp medium. */
    public static TextView sectionTitle(Context c, String s) {
        TextView t = text(c, s, INK, 18, 1);
        return t;
    }

    /** Cam kart: 24dp köşe (imza), 1px kenarlık, GÖLGE YOK, 20dp dolgu. */
    public static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD);
        g.setCornerRadius(dp(24));
        g.setStroke(Math.max(1, dp(1)), CARD_BORDER);
        l.setBackground(g);
        l.setPadding(dp(20), dp(18), dp(20), dp(18));
        return l;
    }

    /** Öne çıkan kart: menekşe kenarlık (seçili/önerilen durum). */
    public static LinearLayout featuredCard(Context c) {
        LinearLayout l = card(c);
        GradientDrawable g = (GradientDrawable) l.getBackground();
        g.setStroke(Math.max(1, dp(1)), ULTRAVIOLET);
        return l;
    }

    /** Düz yüzeyli kutu (12dp köşe varyantı — liste içi satırlar). */
    public static LinearLayout flatCard(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD);
        g.setCornerRadius(dp(12));
        g.setStroke(Math.max(1, dp(1)), CARD_BORDER);
        l.setBackground(g);
        l.setPadding(dp(14), dp(12), dp(14), dp(12));
        return l;
    }

    /**
     * Dolgulu buton: 6dp köşe (GitHub buton imzası). Dolgu rengi bg.
     */
    public static TextView button(Context c, String label, int bg, int fg, int border) {
        TextView t = text(c, label, fg, 14, 1);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(20), 0, dp(20), 0);
        t.setMinHeight(dp(36));
        t.setMinWidth(dp(72));
        GradientDrawable g = new GradientDrawable();
        g.setColor(bg);
        g.setCornerRadius(dp(6));
        if (border != 0) g.setStroke(Math.max(1, dp(1)), border);
        t.setBackground(g);
        t.setClickable(true);
        t.setFocusable(true);
        return t;
    }

    /** Birincil CTA: Terminal Green dolgu + beyaz metin (tek sıcak aksan). */
    public static TextView primaryButton(Context c, String label) {
        return button(c, label, TERMINAL, 0xFFFFFFFF, 0);
    }

    /** Ghost buton (metin pili): saydam + kenarlık, 60dp köşe. */
    public static TextView ghostButton(Context c, String label) {
        TextView t = text(c, label, INK, 14, 1);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), 0, dp(16), 0);
        t.setMinHeight(dp(34));
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x00000000);
        g.setCornerRadius(dp(60));
        g.setStroke(Math.max(1, dp(1)), PILL_BORDER);
        t.setBackground(g);
        t.setClickable(true);
        t.setFocusable(true);
        return t;
    }

    /** Tehlike eylemi: kırmızı kenarlıklı, 6dp köşe. */
    public static TextView dangerButton(Context c, String label) {
        return button(c, label, 0x00000000, RED, RED);
    }

    /** Ripple'lı buton (basınca dalga efekti). */
    public static TextView rippleButton(Context c, String label, int bg, int fg, int border) {
        TextView t = button(c, label, bg, fg, border);
        GradientDrawable g = (GradientDrawable) t.getBackground();
        Drawable mask = new GradientDrawable();
        ((GradientDrawable) mask).setCornerRadius(dp(6));
        ((GradientDrawable) mask).setColor(0xFFFFFFFF);
        RippleDrawable rd = new RippleDrawable(
                ColorStateList.valueOf(dark ? 0x26FFFFFF : 0x1A000000), g, mask);
        t.setBackground(rd);
        return t;
    }

    public static TextView primaryPill(Context c, String label) {
        return rippleButton(c, label, TERMINAL, 0xFFFFFFFF, 0);
    }

    public static TextView ghostPill(Context c, String label) {
        return ghostButton(c, label);
    }

    /** Etiket (tag): mono + BÜYÜK harf, 9999dp pil, ince kenarlık. */
    public static TextView badge(Context c, String label, int fg, int border) {
        TextView t = mono(c, label, fg, 10);
        t.setPadding(dp(10), dp(2), dp(10), dp(3));
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x00000000);
        g.setCornerRadius(dp(9999));
        g.setStroke(Math.max(1, dp(1)), border);
        t.setBackground(g);
        return t;
    }

    /** Dolgulu etiket: renk %12 arka plan + renkli mono metin. */
    public static TextView badgeFill(Context c, String label, int color) {
        TextView t = mono(c, label, color, 10);
        t.setPadding(dp(10), dp(2), dp(10), dp(3));
        GradientDrawable g = new GradientDrawable();
        g.setColor(tint(color, dark ? 0x1F : 0x1A));
        g.setCornerRadius(dp(9999));
        t.setBackground(g);
        return t;
    }

    public static TextView miniPill(Context c, String label, int fg, int border) {
        return badge(c, label, fg, border);
    }

    /** Metin bağlantısı: Sky rengi, alt çizgi yok. */
    public static TextView link(Context c, String label) {
        TextView t = text(c, label, BLUE, 14, 1);
        t.setPadding(0, dp(8), 0, dp(8));
        t.setClickable(true);
        return t;
    }

    public static ProgressBar thinProgress(Context c) {
        ProgressBar p = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
        p.setProgressDrawable(progressDrawable());
        p.setMax(100);
        return p;
    }

    private static Drawable progressDrawable() {
        GradientDrawable track = new GradientDrawable();
        track.setColor(dark ? 0xFF21262D : 0xFFD0D7DE);
        track.setCornerRadius(dp(6));
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(TERMINAL);
        fill.setCornerRadius(dp(6));
        ClipDrawable clip = new ClipDrawable(fill, Gravity.LEFT, ClipDrawable.HORIZONTAL);
        LayerDrawable ld = new LayerDrawable(new Drawable[]{track, clip});
        ld.setId(0, android.R.id.background);
        ld.setId(1, android.R.id.progress);
        return ld;
    }

    public static final String[] STEPS() {
        return new String[]{L.s(R.string.step_download), L.s(R.string.step_patch),
                L.s(R.string.step_sign), L.s(R.string.step_install)};
    }

    public static int stepIndex(String stage) {
        if ("patch".equals(stage)) return 1;
        if ("sign".equals(stage)) return 2;
        if ("install".equals(stage)) return 3;
        return 0;
    }

    /** Adım göstergesi: İndiriliyor › Yamalanıyor › İmzalanıyor › Kuruluyor */
    public static LinearLayout steps(Context c, String stage) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int active = stepIndex(stage);
        String[] steps = STEPS();
        for (int i = 0; i < steps.length; i++) {
            if (i > 0) {
                TextView sep = text(c, "›", FOG, 11, 0);
                sep.setPadding(dp(4), 0, dp(4), 0);
                row.addView(sep);
            }
            int col = i == active ? INK : (i < active ? SMOKE : FOG);
            row.addView(text(c, steps[i], col, 11, 1));
        }
        return row;
    }

    /** Harf avatarı (görsel yüklenemezse). */
    public static Bitmap letterAvatar(String hint, int px) {
        Bitmap b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(dark ? 0xFF21262D : 0xFFD0D7DE);
        cv.drawRoundRect(0, 0, px, px, px * 0.19f, px * 0.19f, p);
        p.setColor(dark ? 0xFFA4AEA6 : 0xFF656D76);
        p.setTextSize(px * 0.42f);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        String ch = (hint == null || hint.isEmpty()) ? "?" : hint.substring(0, 1).toUpperCase(Locale.ROOT);
        float ty = px / 2f - (p.descent() + p.ascent()) / 2f;
        cv.drawText(ch, px / 2f, ty, p);
        return b;
    }

    /** Uygulama ikonu: 16dp köşe (görsel yarıçapı token'ı). */
    public static ImageView appIcon(Context c, int sizeDp) {
        ImageView iv = new ImageView(c);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setClipToOutline(true);
        iv.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(16));
            }
        });
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return iv;
    }

    public static ImageView circularImage(Context c, int sizeDp) {
        return appIcon(c, sizeDp);
    }

    /** Ekran görüntüleri: 16dp köşe. */
    public static ImageView roundedImage(Context c, int wDp, int hDp, int radiusDp) {
        ImageView iv = new ImageView(c);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setClipToOutline(true);
        final int r = dp(radiusDp);
        iv.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r);
            }
        });
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(wDp), dp(hDp)));
        return iv;
    }

    /** İskelet bloğu (shimmer UiFx ile canlandırılır). */
    public static View skeleton(Context c, int hDp) {
        View v = new View(c);
        GradientDrawable g = new GradientDrawable();
        g.setColor(dark ? 0xFF161B22 : 0xFFEFF2F5);
        g.setCornerRadius(dp(12));
        v.setBackground(g);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(hDp)));
        return v;
    }

    public static String size(long bytes) {
        if (bytes <= 0) return "";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.US, "%.0f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb);
        return String.format(Locale.US, "%.2f GB", mb / 1024.0);
    }

    public static String date(long millis) {
        if (millis <= 0) return "";
        Locale loc = L.ctx().getResources().getConfiguration().getLocales().get(0);
        return new SimpleDateFormat("dd.MM.yyyy", loc).format(new Date(millis));
    }

    /** Rengin saydam tonu: tint(mavi, 0x1F) → %12 renk dolgusu. */
    public static int tint(int color, int alpha) {
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        return (alpha << 24) | (r << 16) | (g << 8) | b;
    }

    /** Menekşe hero halesi: radyal ultraviolet, düşük alfa (bulanık izlenim). */
    public static GradientDrawable halo() {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        int[] colors = {tint(ULTRAVIOLET, 0x30), tint(ULTRAVIOLET, 0x00)};
        g.setColors(colors);
        return g;
    }
}
