package io.github.firatnexus.eskidroid;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Hareket, ses ve dokunsal geri bildirim (Things "sakin" ruhuna uygun):
 * - Buton basma: kısa ölçek animasyonu (ripple Ui.rippleButton'da)
 * - Stagger: liste kartları sırayla belirir
 * - Shimmer: iskelet yükleme blokları
 * - Sayfa geçişi: fade + slide (kart → detay, geri dönüşte tersi)
 * - Tamam: tik animasyonu; Hata: sarsılma
 * - Haptic: tıklama ve işlem bitişi
 * - Ses: indirme bitti / kurulum bitti / hata (kodla üretilen, telifsiz tonlar)
 * Sistem "animasyonları kaldır" ayarı ve Ayarlar'daki anahtarlar dikkate alınır.
 */
public final class UiFx {

    public static final long QUICK = 140;
    public static final long NORMAL = 240;
    public static final long SLOW = 360;

    private static SoundPool pool;
    private static int sndDone, sndInstall, sndError;
    private static boolean loaded;
    private static android.content.Context appCtx;

    private UiFx() {
    }

    public static boolean anims(Context c) {
        return Prefs.get(c).anims(c);
    }

    // ============================ basınca ölçek ============================

    /** Butonlara basınca kısa ölçek animasyonu (0.96) + ripple. */
    public static void press(final View v) {
        if (!anims(v.getContext())) return;
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        view.animate().scaleX(0.96f).scaleY(0.96f)
                                .setDuration(QUICK).start();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        view.animate().scaleX(1f).scaleY(1f)
                                .setDuration(QUICK).start();
                        break;
                }
                return false; // click listener'lar çalışmaya devam eder
            }
        });
    }

    // ============================ stagger ============================

    /** Liste kartları ilk yüklemede sırayla belirir. */
    public static void stagger(Context c, List<View> views) {
        if (!anims(c) || views.isEmpty()) return;
        int i = 0;
        for (View v : views) {
            final int idx = i++;
            v.setAlpha(0f);
            v.setTranslationY(dp(v.getContext(), 24));
            v.postDelayed(() -> {
                v.animate().alpha(1f).translationY(0f)
                        .setDuration(NORMAL)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
            }, 40L * idx);
        }
    }

    // ============================ shimmer ============================

    /** İskelet bloklarına shimmer (yukarı kayan ışık şeridi). */
    public static void shimmer(Context c, View v) {
        if (!anims(c)) return;
        int w = v.getWidth() <= 0 ? dp(c, 300) : v.getWidth();
        final ShapeDrawable[] bg = new ShapeDrawable[1];
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(1100);
        a.setRepeatCount(ValueAnimator.INFINITE);
        a.setRepeatMode(ValueAnimator.RESTART);
        a.addUpdateListener(an -> {
            float t = (float) an.getAnimatedValue();
            int x = (int) (-w + 2 * w * t);
            if (bg[0] == null) {
                bg[0] = new ShapeDrawable(new RectShape());
                bg[0].setIntrinsicWidth(v.getWidth() <= 0 ? dp(c, 300) : v.getWidth());
                bg[0].setIntrinsicHeight(v.getHeight() <= 0 ? dp(c, 40) : v.getHeight());
                v.setBackground(bg[0]);
            }
            int base = Ui.isDark() ? 0xFF24282D : 0xFFE7EBEF;
            int hi = Ui.isDark() ? 0xFF323941 : 0xFFF7FAFC;
            bg[0].getPaint().setShader(new LinearGradient(x, 0, x + w / 2f, 0,
                    base, hi, Shader.TileMode.CLAMP));
            v.invalidate();
        });
        a.start();
        v.setTag(R.id.tag_fx, a);
    }

    public static void stopShimmer(View v) {
        Object t = v.getTag(R.id.tag_fx);
        if (t instanceof ValueAnimator) ((ValueAnimator) t).cancel();
    }

    public static void shimmerAll(Context c, List<View> vs) {
        for (View v : vs) shimmer(c, v);
    }

    // ============================ sayfa geçişi ============================

    /** Detay sayfası açılışı: fade + sağdan kayma. */
    public static void slideIn(Context c, View v) {
        if (!anims(c)) return;
        v.setAlpha(0f);
        v.setTranslationX(dp(c, 80));
        v.animate().alpha(1f).translationX(0f)
                .setDuration(SLOW)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    /** Detaydan geri dönüş: fade + sağa kayma, sonra removeView. */
    public static void slideOut(Context c, View v, Runnable end) {
        if (!anims(c)) {
            end.run();
            return;
        }
        v.animate().alpha(0f).translationX(dp(c, 80))
                .setDuration(SLOW)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator a) {
                        end.run();
                    }
                })
                .start();
    }

    /** Sekme değişince içerik hafif fade. */
    public static void fadeIn(Context c, View v) {
        if (!anims(c)) return;
        v.setAlpha(0f);
        v.animate().alpha(1f).setDuration(NORMAL).start();
    }

    // ============================ ilerleme / sonuç ============================

    /** İlerleme çubuğunu yumuşakça yeni değere taşır; yüzde metni de güncellenir. */
    public static void animateProgress(Context c, android.widget.ProgressBar pb,
                                       android.widget.TextView pct, int from, int to) {
        if (!anims(c)) {
            pb.setProgress(to);
            if (pct != null) pct.setText(to + "%");
            return;
        }
        ValueAnimator a = ValueAnimator.ofInt(from, to);
        a.setDuration(Math.max(120, Math.min(500, Math.abs(to - from) * 25)));
        a.setInterpolator(new DecelerateInterpolator());
        a.addUpdateListener(an -> {
            int v = (int) an.getAnimatedValue();
            pb.setProgress(v);
            if (pct != null) pct.setText(v + "%");
        });
        a.start();
    }

    /** Bitiş: tik animasyonu (büyüyerek belirir). */
    public static void checkPop(Context c, View v) {
        if (!anims(c)) return;
        v.setScaleX(0f);
        v.setScaleY(0f);
        v.setAlpha(1f);
        v.animate().scaleX(1f).scaleY(1f)
                .setDuration(NORMAL)
                .setInterpolator(new OvershootInterpolator(1.4f))
                .start();
    }

    /** Hata: sarsılma animasyonu. */
    public static void shake(Context c, View v) {
        if (!anims(c)) return;
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(450);
        a.setInterpolator(input -> input); // linear
        a.addUpdateListener(an -> {
            float t = (float) an.getAnimatedValue();
            float d = (float) (Math.sin(t * Math.PI * 6) * (1f - t) * dp(c, 10));
            v.setTranslationX(d);
        });
        a.start();
    }

    // ============================ haptic ============================

    private static Vibrator vib(Context c) {
        if (Build.VERSION.SDK_INT >= 31) {
            return (Vibrator) c.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) != null
                    ? ((android.os.VibratorManager) c.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)).getDefaultVibrator()
                    : (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
        }
        return (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
    }

    /** Tıklama geri bildirimi (kısa, hafif). */
    public static void tap(Context c) {
        try {
            vibrate(c, 12);
        } catch (Exception ignored) {
        }
    }

    /** İşlem bitti geri bildirimi (iki kısa darbe). */
    public static void done(Context c) {
        try {
            long[] pattern = {0, 30, 60, 30};
            Vibrator v = vib(c);
            if (v == null) return;
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createWaveform(pattern, -1));
            } else {
                v.vibrate(pattern, -1);
            }
        } catch (Exception ignored) {
        }
    }

    /** Hata geri bildirimi (tek uzun). */
    public static void err(Context c) {
        try {
            vibrate(c, 80);
        } catch (Exception ignored) {
        }
    }

    private static void vibrate(Context c, long ms) {
        Vibrator v = vib(c);
        if (v == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            v.vibrate(VibrationEffect.createOneShot(ms,
                    VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            v.vibrate(ms);
        }
    }

    // ============================ sesler (kodla üretilmiş, telifsiz) ============================

    /**
     * Üç kısa bildirim sesi — WAV baytları kodla üretilir (sinüs dalgası +
     * zarf), hiçbir dosya indirilmez, telif sorunu yoktur.
     *  - done:    yükselen iki ton (E5→A5)
     *  - install: majör arpej (C5-E5-G5)
     *  - error:   alçak çift bip (A3)
     */
    private static void ensurePool(Context c) {
        if (loaded) return;
        appCtx = c.getApplicationContext();
        synchronized (UiFx.class) {
            if (loaded) return;
            try {
                AudioAttributes attrs = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                pool = new SoundPool.Builder().setMaxStreams(2)
                        .setAudioAttributes(attrs).build();
                sndDone = load(genWav(new int[][]{{659, 0, 90}, {880, 90, 200}}));
                sndInstall = load(genWav(new int[][]{
                        {523, 0, 80}, {659, 80, 160}, {784, 160, 280}}));
                sndError = load(genWav(new int[][]{{220, 0, 120}, {220, 180, 320}}));
                loaded = true;
            } catch (Exception ignored) {
                loaded = true; // ses desteklenmiyorsa sessiz devam
            }
        }
    }

    /** WAV baytlarını geçici dosyaya yazıp SoundPool'a yükler (byte[] yüklemesi API 26+). */
    private static int load(byte[] wav) {
        java.io.File f = null;
        try {
            f = java.io.File.createTempFile("fx", ".wav", appCtx.getCacheDir());
            java.io.FileOutputStream fo = new java.io.FileOutputStream(f);
            fo.write(wav);
            fo.close();
            return pool.load(f.getAbsolutePath(), 1);
        } catch (Exception e) {
            return -1;
        }
    }

    /** Sinüs tonlarından 16-bit mono 44.1kHz WAV baytları üretir. */
    static byte[] genWav(int[][] notes) {
        final int SR = 22050;
        int total = 0;
        for (int[] n : notes) total = Math.max(total, n[1] + n[2]);
        int samples = (int) ((long) total * SR / 1000L);
        short[] pcm = new short[samples];
        for (int[] n : notes) {
            int start = (int) ((long) n[1] * SR / 1000L);
            int end = (int) ((long) (n[1] + n[2]) * SR / 1000L);
            for (int i = start; i < end && i < samples; i++) {
                double t = (i - start) / (double) SR;
                double dur = (end - start) / (double) SR;
                double env = Math.min(1.0, t / 0.008) * Math.min(1.0, (dur - t) / 0.05);
                if (env < 0) env = 0;
                pcm[i] += (short) (Math.sin(2 * Math.PI * n[0] * t) * env * 0.5 * 32767);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int dataSize = samples * 2;
        writeStr(out, "RIFF");
        writeInt(out, 36 + dataSize);
        writeStr(out, "WAVE");
        writeStr(out, "fmt ");
        writeInt(out, 16);
        writeShort(out, 1);
        writeShort(out, 1);
        writeInt(out, SR);
        writeInt(out, SR * 2);
        writeShort(out, 2);
        writeShort(out, 16);
        writeStr(out, "data");
        writeInt(out, dataSize);
        for (short s : pcm) {
            out.write(s & 0xFF);
            out.write((s >> 8) & 0xFF);
        }
        return out.toByteArray();
    }

    private static void writeStr(ByteArrayOutputStream o, String s) {
        for (byte b : s.getBytes()) o.write(b);
    }

    private static void writeInt(ByteArrayOutputStream o, int v) {
        o.write(v & 0xFF);
        o.write((v >> 8) & 0xFF);
        o.write((v >> 16) & 0xFF);
        o.write((v >> 24) & 0xFF);
    }

    private static void writeShort(ByteArrayOutputStream o, int v) {
        o.write(v & 0xFF);
        o.write((v >> 8) & 0xFF);
    }

    public static void sndDone(Context c) {
        if (!Prefs.get(c).sounds()) return;
        ensurePool(c);
        if (pool != null) pool.play(sndDone, 0.6f, 0.6f, 1, 0, 1f);
    }

    public static void sndInstall(Context c) {
        if (!Prefs.get(c).sounds()) return;
        ensurePool(c);
        if (pool != null) pool.play(sndInstall, 0.6f, 0.6f, 1, 0, 1f);
    }

    public static void sndError(Context c) {
        if (!Prefs.get(c).sounds()) return;
        ensurePool(c);
        if (pool != null) pool.play(sndError, 0.5f, 0.5f, 1, 0, 1f);
    }

    private static int dp(Context c, int v) {
        return Ui.dp(v);
    }
}
