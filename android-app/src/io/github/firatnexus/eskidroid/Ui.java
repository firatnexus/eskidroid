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
 * App Store tarzı mağaza görünümü (v1.5): büyük başlıklar, pil butonlar
 * ("GET" havası), beyaz zemin + ince ayraçlı kartlar, yuvarlak büyük ikonlar.
 * Arayüz tamamen koddan çizilir (XML yok). Aydınlık/karanlık mod aynı
 * mantıkla: aynı vurgu rengi, aynı kontrast kuralları. Tema değişimi
 * Ui.applyTheme() ile: statik alanlar güncellenir, ekranlar yeniden çizilir.
 */
public final class Ui {

    // ---- mağaza renkleri (aydınlık varsayılan; applyTheme ile değişir) ----
    public static int CANVAS = 0xFFFFFFFF;   // saf beyaz zemin (App Store)
    public static int CARD = 0xFFFFFFFF;     // kart yüzeyi
    public static int ELEVATED = 0xFFF2F2F7; // arama kutusu / ikincil dolgu
    public static int OVERLAY = 0x73000000;  // %45 siyah
    public static int INK = 0xFF111111;      // ana metin
    public static int FOG = 0xFF6E6E73;      // ikincil metin
    public static int ASH = 0xFF8E8E93;      // üçüncül metin / ikon
    public static int SMOKE = 0xFF3A3A3C;    // vurgulu gövde metni
    public static int HAIRLINE = 0xFFE5E5EA; // ayraçlar
    public static int BLUE = 0xFF007AFF;     // mağaza mavisi (App Store)
    public static int SKY = 0xFF5AC8FA;      // ikincil vurgu
    // durum renkleri
    public static int GREEN = 0xFF34C759;
    public static int EMBER = 0xFFFF9500;
    public static int RED = 0xFFFF3B30;
    // eski uyumluluk adları
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
            CANVAS = 0xFF000000;
            CARD = 0xFF1C1C1E;
            ELEVATED = 0xFF1C1C1E;
            OVERLAY = 0x99000000;
            INK = 0xFFF2F2F7;
            FOG = 0xFFAEAEB2;
            ASH = 0xFF636366;
            SMOKE = 0xFFC7C7CC;
            HAIRLINE = 0xFF38383A;
            BLUE = 0xFF0A84FF;
            SKY = 0xFF64D2FF;
            GREEN = 0xFF30D158;
            EMBER = 0xFFFF9F0A;
            RED = 0xFFFF453A;
        } else {
            CANVAS = 0xFFFFFFFF;
            CARD = 0xFFFFFFFF;
            ELEVATED = 0xFFF2F2F7;
            OVERLAY = 0x73000000;
            INK = 0xFF111111;
            FOG = 0xFF6E6E73;
            ASH = 0xFF8E8E93;
            SMOKE = 0xFF3A3A3C;
            HAIRLINE = 0xFFE5E5EA;
            BLUE = 0xFF007AFF;
            SKY = 0xFF5AC8FA;
            GREEN = 0xFF34C759;
            EMBER = 0xFFFF9500;
            RED = 0xFFFF3B30;
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
        t.setLineSpacing(0, sp >= 17 ? 1.25f : 1.2f);
        if (style == 2) t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        else if (style == 1) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    /** Mağaza büyük başlığı (App Store "Büyük Başlık": 30sp kalın). */
    public static TextView bigTitle(Context c, String s) {
        TextView t = text(c, s, INK, 30, 2);
        t.setLetterSpacing(-0.02f);
        return t;
    }

    /** Bölüm başlığı: 20sp kalın (App Store bölüm başlığı). */
    public static TextView sectionTitle(Context c, String s) {
        return text(c, s, INK, 20, 2);
    }

    /** Mağaza kartı: 16dp köşe, hairline kenarlık, hafif gölge. */
    public static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD);
        g.setCornerRadius(dp(16));
        g.setStroke(Math.max(1, dp(1)), HAIRLINE);
        l.setBackground(g);
        l.setPadding(dp(16), dp(14), dp(16), dp(14));
        l.setElevation(dp(2));
        l.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(16));
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

    /** Pil buton: mağaza "GET" stili — 99dp köşe, 36dp yükseklik. */
    public static TextView button(Context c, String label, int bg, int fg, int border) {
        TextView t = text(c, label, fg, 14, 1);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(20), 0, dp(20), 0);
        t.setMinHeight(dp(36));
        t.setMinWidth(dp(72));
        GradientDrawable g = new GradientDrawable();
        g.setColor(bg);
        g.setCornerRadius(dp(99));
        if (border != 0) g.setStroke(Math.max(1, dp(1)), border);
        t.setBackground(g);
        t.setClickable(true);
        t.setFocusable(true);
        return t;
    }

    /** GET butonu: mavi dolgu, beyaz metin. */
    public static TextView primaryButton(Context c, String label) {
        return button(c, label, BLUE, dark ? 0xFFFFFFFF : 0xFFFFFFFF, 0);
    }

    /** İkincil buton: gri dolgu (iOS "secondary"), Ink metin. */
    public static TextView ghostButton(Context c, String label) {
        return button(c, label, ELEVATED, INK, 0);
    }

    /** Tehlike eylemi: kırmızı kenarlıklı. */
    public static TextView dangerButton(Context c, String label) {
        return button(c, label, 0x00000000, RED, RED);
    }

    /** Ripple'lı buton (basınca dalga efekti). */
    public static TextView rippleButton(Context c, String label, int bg, int fg, int border) {
        TextView t = button(c, label, bg, fg, border);
        GradientDrawable g = (GradientDrawable) t.getBackground();
        Drawable mask = new GradientDrawable();
        ((GradientDrawable) mask).setCornerRadius(dp(99));
        ((GradientDrawable) mask).setColor(0xFFFFFFFF);
        RippleDrawable rd = new RippleDrawable(
                ColorStateList.valueOf(dark ? 0x33FFFFFF : 0x1F007AFF), g, mask);
        t.setBackground(rd);
        return t;
    }

    public static TextView primaryPill(Context c, String label) {
        return rippleButton(c, label, BLUE, 0xFFFFFFFF, 0);
    }

    public static TextView ghostPill(Context c, String label) {
        return rippleButton(c, label, ELEVATED, INK, 0);
    }

    /** Rozet: pil biçimli, ince kenarlık (kaynak, sürüm, durum). */
    public static TextView badge(Context c, String label, int fg, int border) {
        TextView t = text(c, label, fg, 10, 1);
        t.setPadding(dp(9), dp(2), dp(9), dp(3));
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x00000000);
        g.setCornerRadius(dp(99));
        g.setStroke(Math.max(1, dp(1)), border);
        t.setBackground(g);
        return t;
    }

    /** Dolgulu rozet: renk %12 arka plan + renkli metin (mağaza kategorisi). */
    public static TextView badgeFill(Context c, String label, int color) {
        TextView t = text(c, label, color, 10, 1);
        t.setPadding(dp(9), dp(2), dp(9), dp(3));
        GradientDrawable g = new GradientDrawable();
        g.setColor(tint(color, dark ? 0x26 : 0x1A));
        g.setCornerRadius(dp(99));
        t.setBackground(g);
        return t;
    }

    public static TextView miniPill(Context c, String label, int fg, int border) {
        return badge(c, label, fg, border);
    }

    /** Metin bağlantısı: mağaza mavisi, alt çizgi yok. */
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
        track.setColor(dark ? 0xFF2C2C2E : 0xFFE5E5EA);
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

    /** Harf avatarı (görsel yüklenemezse). */
    public static Bitmap letterAvatar(String hint, int px) {
        Bitmap b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(dark ? 0xFF2C2C2E : 0xFFE5E5EA);
        cv.drawRoundRect(0, 0, px, px, px * 0.24f, px * 0.24f, p);
        p.setColor(dark ? 0xFFAEAEB2 : 0xFF8E8E93);
        p.setTextSize(px * 0.42f);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        String ch = (hint == null || hint.isEmpty()) ? "?" : hint.substring(0, 1).toUpperCase(Locale.ROOT);
        float ty = px / 2f - (p.descent() + p.ascent()) / 2f;
        cv.drawText(ch, px / 2f, ty, p);
        return b;
    }

    /** Mağaza ikonu: yuvarlatılmış köşe (App Store ~%22). */
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

    /** Ekran görüntüleri: 12dp köşe. */
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
        g.setColor(dark ? 0xFF1C1C1E : 0xFFEFEFF4);
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

    /** Rengin saydam tonu: tint(mavi, 0x1F) → %12 mavi dolgu. */
    public static int tint(int color, int alpha) {
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        return (alpha << 24) | (r << 16) | (g << 8) | b;
    }
}
