package io.github.firatnexus.eskidroid;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
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
 * Fey tasarım sistemi (DESIGN.md) — arayüz tamamen koddan çizilir (XML yok).
 * Renkler, yarıçaplar ve tipografi DESIGN.md'deki tokenlere birebir uyar.
 */
public final class Ui {

    // ---- Fey renk tokenleri ----
    public static final int CANVAS   = 0xFF0B0B0B;
    public static final int CARD     = 0xFF191919;
    public static final int ELEVATED = 0xFF131313;
    public static final int OVERLAY  = 0xCC000000;
    public static final int WHITE    = 0xFFFFFFFF;
    public static final int GRAPHITE = 0xFF868F97;
    public static final int MIST     = 0xFFCCCCCC;
    public static final int SMOKE    = 0xFF525252;
    public static final int PALE     = 0xFFE6E6E6;
    public static final int BLUE     = 0xFF479FFA;  // sinyal: ilerleme / etkin
    public static final int GREEN    = 0xFF4EBE96;  // sinyal: uyum / başarı
    public static final int EMBER    = 0xFFFFA16C;  // sinyal: uyarı

    private static DisplayMetrics metrics;

    private Ui() {
    }

    public static void init(Context ctx) {
        metrics = ctx.getResources().getDisplayMetrics();
    }

    public static int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, metrics));
    }

    public static float dpf(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, metrics);
    }

    /** style: 0 normal, 1 medium, 2 bold (yalnız büyük başlıkta). */
    public static TextView text(Context c, String s, int color, float sp, int style) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (style == 2) t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        else if (style == 1) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    /** #191919 kart: 16dp köşe, 1dp #525252 hairline kenarlık (halo gölge yerine). */
    public static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD);
        g.setCornerRadius(dp(16));
        g.setStroke(Math.max(1, dp(1)), SMOKE);
        l.setBackground(g);
        l.setPadding(dp(16), dp(14), dp(16), dp(14));
        return l;
    }

    /** 99dp pil buton. bg=TRANSPARENT + border → ghost. */
    public static TextView pill(Context c, String label, int bg, int fg, int border) {
        TextView t = text(c, label, fg, 13, 1);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(18), 0, dp(18), 0);
        t.setMinHeight(dp(40));
        t.setMinWidth(dp(64));
        GradientDrawable g = new GradientDrawable();
        g.setColor(bg);
        g.setCornerRadius(dp(99));
        if (border != 0) g.setStroke(Math.max(1, dp(1)), border);
        t.setBackground(g);
        return t;
    }

    /** Birincil eylem: beyaz dolu pil, siyah metin (EskiDroid uzantısı). */
    public static TextView primaryPill(Context c, String label) {
        return pill(c, label, WHITE, CANVAS, 0);
    }

    /** Ghost pil. */
    public static TextView ghostPill(Context c, String label) {
        return pill(c, label, 0x00000000, MIST, PALE);
    }

    /** Küçük durum pil'i (10sp, ince kenarlık). */
    public static TextView miniPill(Context c, String label, int fg, int border) {
        TextView t = text(c, label, fg, 10, 1);
        t.setPadding(dp(10), dp(2), dp(10), dp(3));
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x00000000);
        g.setCornerRadius(dp(99));
        g.setStroke(Math.max(1, dp(1)), border);
        t.setBackground(g);
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
        track.setColor(0xFF232323);
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

    public static final String[] STEPS = {"İndiriliyor", "Yamalanıyor", "İmzalanıyor", "Kuruluyor"};

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
        for (int i = 0; i < STEPS.length; i++) {
            if (i > 0) {
                TextView sep = text(c, "›", GRAPHITE, 11, 0);
                sep.setPadding(dp(4), 0, dp(4), 0);
                row.addView(sep);
            }
            int col = i == active ? WHITE : (i < active ? MIST : GRAPHITE);
            row.addView(text(c, STEPS[i], col, 11, 1));
        }
        return row;
    }

    /** Harf avatarı (görsel yüklenemezse). */
    public static Bitmap letterAvatar(String hint, int px) {
        Bitmap b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(CARD);
        cv.drawCircle(px / 2f, px / 2f, px / 2f, p);
        p.setColor(WHITE);
        p.setTextSize(px * 0.42f);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        String ch = (hint == null || hint.isEmpty()) ? "?" : hint.substring(0, 1).toUpperCase(Locale.ROOT);
        float ty = px / 2f - (p.descent() + p.ascent()) / 2f;
        cv.drawText(ch, px / 2f, ty, p);
        return b;
    }

    /** Dairesel kırpılan görsel (48dp ikon). */
    public static ImageView circularImage(Context c, int sizeDp) {
        ImageView iv = new ImageView(c);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setClipToOutline(true);
        iv.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return iv;
    }

    /** Yuvarlatılmış köşeli görsel (ekran görüntüleri, 16dp). */
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

    /** İskelet bloğu (#191919). */
    public static View skeleton(Context c, int hDp) {
        View v = new View(c);
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD);
        g.setCornerRadius(dp(10));
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
        return new SimpleDateFormat("dd.MM.yyyy", Locale.US).format(new Date(millis));
    }
}
