package io.github.firatnexus.eskidroid;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

import java.util.Locale;

/**
 * Çok dillilik: Tüm arayüz metinleri strings.xml kaynaklarından gelir.
 * Dil değişikliği anında uygulanır (Context yerelleştirmesi; yeniden başlatma yok).
 * Diller: Türkçe (varsayılan), English, Deutsch, Español, Русский.
 */
public final class L {

    private static Context base;
    private static String currentTag = null;

    private L() {
    }

    /** Tema/dil değişince yeniden çağrılır. */
    public static void init(Context ctx) {
        base = ctx.getApplicationContext();
        currentTag = null;
    }

    /** Seçili dile göre yerelleştirilmiş Context döndürür (önbellekli). */
    public static synchronized Context ctx() {
        String want = Prefs.get(base).lang();
        String tag = "system".equals(want) ? null : want;
        if (tag == null) {
            currentTag = null;
            return base; // sistem dili
        }
        if (tag.equals(currentTag)) return cached != null ? cached : base;
        Locale loc = Locale.forLanguageTag(tag);
        Locale.setDefault(loc);
        Configuration conf = base.getResources().getConfiguration();
        conf = new Configuration(conf);
        conf.setLocale(loc);
        conf.setLayoutDirection(loc);
        cached = base.createConfigurationContext(conf);
        currentTag = tag;
        return cached;
    }

    private static Context cached;

    public static String s(int resId) {
        return ctx().getString(resId);
    }

    public static String s(int resId, Object... args) {
        return ctx().getString(resId, args);
    }

    /** Yerelleştirilmiş sayı biçimi (ör. indirme sayıları). */
    public static String num(long n) {
        return String.format(ctx().getResources().getConfiguration().getLocales().get(0), "%,d", n);
    }
}
