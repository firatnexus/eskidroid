package io.github.firatnexus.eskidroid;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

/**
 * Monoline (tek çizgi) ikonlar — Canvas ile çizilir, ikon kütüphanesi yok.
 * Tüm ikonlar 24dp kare, 1.8dp beyaz çizgi (Fey: monoline, beyaz/mist).
 */
public final class Icons {

    public interface Drawer {
        void draw(Canvas cv, Paint p, int px);
    }

    private Icons() {
    }

    public static Drawable icon(Context c, int color, Drawer d) {
        int px = Ui.dp(24);
        Bitmap b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeWidth(Ui.dpf(1.8f));
        p.setColor(color);
        d.draw(cv, p, px);
        return new BitmapDrawable(c.getResources(), b);
    }

    public static Drawable search(Context c, int color) {
        return icon(c, color, (cv, p, px) -> {
            float u = px;
            cv.drawCircle(u * 0.40f, u * 0.40f, u * 0.22f, p);
            cv.drawLine(u * 0.57f, u * 0.57f, u * 0.80f, u * 0.80f, p);
        });
    }

    public static Drawable close(Context c, int color) {
        return icon(c, color, (cv, p, px) -> {
            float u = px;
            cv.drawLine(u * 0.28f, u * 0.28f, u * 0.72f, u * 0.72f, p);
            cv.drawLine(u * 0.72f, u * 0.28f, u * 0.28f, u * 0.72f, p);
        });
    }

    public static Drawable chevronRight(Context c, int color) {
        return icon(c, color, (cv, p, px) -> {
            float u = px;
            cv.drawLine(u * 0.40f, u * 0.25f, u * 0.62f, u * 0.50f, p);
            cv.drawLine(u * 0.62f, u * 0.50f, u * 0.40f, u * 0.75f, p);
        });
    }

    public static Drawable chevronLeft(Context c, int color) {
        return icon(c, color, (cv, p, px) -> {
            float u = px;
            cv.drawLine(u * 0.60f, u * 0.25f, u * 0.38f, u * 0.50f, p);
            cv.drawLine(u * 0.38f, u * 0.50f, u * 0.60f, u * 0.75f, p);
        });
    }

    public static Drawable box(Context c, int color) {
        return icon(c, color, (cv, p, px) -> {
            float u = px;
            cv.drawRoundRect(u * 0.15f, u * 0.30f, u * 0.85f, u * 0.80f, u * 0.06f, u * 0.06f, p);
            cv.drawLine(u * 0.15f, u * 0.46f, u * 0.85f, u * 0.46f, p);
            cv.drawLine(u * 0.50f, u * 0.46f, u * 0.50f, u * 0.80f, p);
        });
    }

    public static Drawable gear(Context c, int color) {
        return icon(c, color, (cv, p, px) -> {
            float u = px, cx = u / 2f, cy = u / 2f;
            cv.drawCircle(cx, cy, u * 0.18f, p);
            for (int i = 0; i < 8; i++) {
                double a = Math.PI * 2 * i / 8;
                float x1 = (float) (cx + Math.cos(a) * u * 0.30f);
                float y1 = (float) (cy + Math.sin(a) * u * 0.30f);
                float x2 = (float) (cx + Math.cos(a) * u * 0.40f);
                float y2 = (float) (cy + Math.sin(a) * u * 0.40f);
                cv.drawLine(x1, y1, x2, y2, p);
            }
        });
    }

    public static Drawable download(Context c, int color) {
        return icon(c, color, (cv, p, px) -> {
            float u = px;
            cv.drawLine(u * 0.50f, u * 0.18f, u * 0.50f, u * 0.62f, p);
            cv.drawLine(u * 0.34f, u * 0.46f, u * 0.50f, u * 0.62f, p);
            cv.drawLine(u * 0.66f, u * 0.46f, u * 0.50f, u * 0.62f, p);
            cv.drawLine(u * 0.22f, u * 0.80f, u * 0.78f, u * 0.80f, p);
        });
    }
}
