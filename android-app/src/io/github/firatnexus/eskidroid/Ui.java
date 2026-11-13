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
 * Things tasarım sistemi (docs/design/DESIGN.md) — arayüz tamamen koddan
 * çizilir (XML yok). Renkler, yarıçaplar ve tipografi Things tokenlerine
 * uyar; karanlık mod aynı tokenlerden türetilmiştir. Tema değişimi
 * Ui.applyTheme() ile: statik alanlar güncellenir, ekranlar yeniden çizilir.
 */
public final class Ui {

    // ---- Things renk tokenleri (aydınlık varsayılan; applyTheme ile değişir) ----
    public static int CANVAS = 0xFFF2F5F7;   // Mist — sayfa zemini
    public static int CARD = 0xFFFFFFFF;     // Paper — kart yüzeyi
    public static int ELEVATED = 0xFFFFFFFF; // giriş alanları (Things: beyaz)
    public static int OVERLAY = 0x73000000;  // %45 siyah
    public static int INK = 0xFF303336;      // ana metin
    public static int FOG = 0xFF838B96;      // ikincil metin
    public static int ASH = 0xFF55606E;      // üçüncül metin / ikon vuruşları
    public static int SMOKE = 0xFF44474B;    // vurgulu gövde metni
    public static int HAIRLINE = 0xFFDFE3E8; // kenarlıklar
    public static int BLUE = 0xFF2576EB;     // Signal Blue — etkileşim
    public static int SKY = 0xFF5C9CF5;      // Sky Blue — ikincil vurgu
    // EskiDroid durum uzantıları (DESIGN.md "Durum Renkleri")
    public static int GREEN = 0xFF1E8E5A;
    public static int EMBER = 0xFFC77F2A;
    public static int RED = 0xFFCC4B4B;
    // Eski uyumluluk eski adlar (eski çağrı alanları)
    public static int WHITE = INK;
    public static int GRAPHITE = FOG;
    public static int MIST = FOG;
    public static int PALE = INK;

    private static DisplayMetrics metrics;
    private static boolean dark = false;

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
            CANVAS = 0xFF1A1D21;
            CARD = 0xFF24282D;
            ELEVATED = 0xFF1F2327;
            OVERLAY = 0x99000000;
            INK = 0xFFE8EAED;
            FOG = 0xFF9AA3AD;
            ASH = 0xFF7C8590;
            SMOKE = 0xFFB9C0C9;
            HAIRLINE = 0xFF3A4048;
            BLUE = 0xFF4F91FB;
            SKY = 0xFF5C9CF5;
            GREEN = 0xFF4ECB92;
            EMBER = 0xFFF0B25F;
            RED = 0xFFEF8080;
        } else {
            CANVAS = 0xFFF2F5F7;
            CARD = 0xFFFFFFFF;
            ELEVATED = 0xFFFFFFFF;
            OVERLAY = 0x73000000;
            INK = 0xFF303336;
            FOG = 0xFF838B96;
            ASH = 0xFF55606E;
            SMOKE = 0xFF44474B;
            HAIRLINE = 0xFFDFE3E8;
            BLUE = 0xFF2576EB;
            SKY = 0xFF5C9CF5;
            GREEN = 0xFF1E8E5A;
            EMBER = 0xFFC77F2A;
            RED = 0xFFCC4B4B;
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

    /** Things tipografi ölçeği: caption 13, body 15, subheading 18, heading-sm 20, heading 24, display 36. */
    public static TextView text(Context c, String s, int color, float sp, int style) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setLineSpacing(0, sp >= 17 ? 1.25f : 1.15f);
        if (style == 2) t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        else if (style == 1) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    /** Things kartı: Paper yüzey, 18dp köşe, katmanlı gölge (hairline yerine shadow kenarı). */
    public static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD);
        g.setCornerRadius(dp(18));
        l.setBackground(g);
        l.setPadding(dp(16), dp(16), dp(16), dp(16));
        // Things gölgesi: ambient + contact
        l.setElevation(dp(4));
        l.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(18));
            }
        });
        l.setClipToOutline(false);
        return l;
    }

    /** Düz yüzeyli kutu (gölgesiz kart varyantı — liste içi satırlar). */
    public static LinearLayout flatCard(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD);
        g.setCornerRadius(dp(12));
        l.setBackground(g);
        l.setPadding(dp(14), dp(12), dp(14), dp(12));
        return l;
    }

    /** Buton: Things --radius-buttons 6dp. bg=TRANSPARENT + kenarlık → ghost. */
    public static TextView button(Context c, String label, int bg, int fg, int border) {
        TextView t = text(c, label, fg, 14, 1);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(18), 0, dp(18), 0);
        t.setMinHeight(dp(40));
        t.setMinWidth(dp(64));
        GradientDrawable g = new GradientDrawable();
        g.setColor(bg);
        g.setCornerRadius(dp(6));
        if (border != 0) g.setStroke(Math.max(1, dp(1)), border);
        t.setBackground(g);
        t.setClickable(true);
        t.setFocusable(true);
        return t;
    }

    /** Birincil eylem: Signal Blue dolgu, beyaz metin (accent surface). */
    public static TextView primaryButton(Context c, String label) {
        return button(c, label, BLUE, dark ? 0xFF10131A : 0xFFFFFFFF, 0);
    }

    /** Ghost buton: hairline kenarlık, Ink metin. */
    public static TextView ghostButton(Context c, String label) {
        return button(c, label, 0x00000000, INK, HAIRLINE);
    }

    /** Tehlike eylemi: kırmızı kenarlıklı ghost. */
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
                ColorStateList.valueOf(dark ? 0x33FFFFFF : 0x1F2576EB), g, mask);
        t.setBackground(rd);
        return t;
    }

    public static TextView primaryPill(Context c, String label) {
        return rippleButton(c, label, BLUE, dark ? 0xFF10131A : 0xFFFFFFFF, 0);
    }

    public static TextView ghostPill(Context c, String label) {
        return rippleButton(c, label, 0x00000000, INK, HAIRLINE);
    }

    /** Küçük rozet: 6dp köşe, ince kenarlık (kaynak, sürüm, durum). */
    public static TextView badge(Context c, String label, int fg, int border) {
        TextView t = text(c, label, fg, 10, 1);
        t.setPadding(dp(8), dp(2), dp(8), dp(3));
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x00000000);
        g.setCornerRadius(dp(6));
        g.setStroke(Math.max(1, dp(1)), border);
        t.setBackground(g);
        return t;
    }

    public static TextView miniPill(Context c, String label, int fg, int border) {
        return badge(c, label, fg, border);
    }

    /** Metin bağlantısı: Signal Blue, alt çizgi yok (Things ilkesi). */
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
        track.setColor(dark ? 0xFF3A4048 : 0xFFDFE3E8);
        track.setCornerRadius(dp(99));
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(BLUE);
        fill.setCornerRadius(dp(99));
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

    /** Harf avatarı (görsel yüklenemezse) — Things serin tonu. */
    public static Bitmap letterAvatar(String hint, int px) {
        Bitmap b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(dark ? 0xFF2E333A : 0xFFDFE3E8);
        cv.drawRoundRect(0, 0, px, px, px * 0.22f, px * 0.22f, p);
        p.setColor(dark ? 0xFF9AA3AD : 0xFF55606E);
        p.setTextSize(px * 0.42f);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        String ch = (hint == null || hint.isEmpty()) ? "?" : hint.substring(0, 1).toUpperCase(Locale.ROOT);
        float ty = px / 2f - (p.descent() + p.ascent()) / 2f;
        cv.drawText(ch, px / 2f, ty, p);
        return b;
    }

    /** Yuvarlatılmış köşeli ikon (Things: 3px radius → 12dp mobilde). */
    public static ImageView appIcon(Context c, int sizeDp) {
        ImageView iv = new ImageView(c);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setClipToOutline(true);
        iv.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(),
                        dp(sizeDp) * 0.22f);
            }
        });
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return iv;
    }

    public static ImageView circularImage(Context c, int sizeDp) {
        return appIcon(c, sizeDp);
    }

    /** Ekran görüntüleri: 18dp köşe (Things kart yarıçapı). */
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
        g.setColor(dark ? 0xFF24282D : 0xFFE7EBEF);
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
}
