package io.github.firatnexus.eskidroid;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Kullanıcı tercihleri: tema (sistem/aydınlık/karanlık), dil (sistem + 5 dil),
 * sesler ve animasyonlar. Tümü anında uygulanabilir; uygulama yeniden
 * başlatılmaz (arayüz yeniden çizilir).
 */
public final class Prefs {

    public static final String THEME_SYSTEM = "system";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";

    public static final String LANG_SYSTEM = "system";

    private static Prefs s;
    private final SharedPreferences sp;

    private Prefs(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences("eskidroid", Context.MODE_PRIVATE);
    }

    public static synchronized Prefs get(Context ctx) {
        if (s == null) s = new Prefs(ctx);
        return s;
    }

    // ---- tema ----

    public String theme() {
        return sp.getString("theme", THEME_SYSTEM);
    }

    public void setTheme(String t) {
        sp.edit().putString("theme", t).apply();
    }

    public boolean dark(Context ctx) {
        String t = theme();
        if (THEME_DARK.equals(t)) return true;
        if (THEME_LIGHT.equals(t)) return false;
        int night = ctx.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return night == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    // ---- dil ----

    public String lang() {
        return sp.getString("lang", LANG_SYSTEM);
    }

    public void setLang(String l) {
        sp.edit().putString("lang", l).apply();
    }

    // ---- sesler / animasyonlar ----

    public boolean sounds() {
        return sp.getBoolean("sounds", true);
    }

    public void setSounds(boolean v) {
        sp.edit().putBoolean("sounds", v).apply();
    }

    public boolean anims(Context ctx) {
        if (!sp.getBoolean("anims", true)) return false;
        try {
            // Sistem "animasyonları kaldır" ayarına saygı duyar
            float scale = android.provider.Settings.Global.getFloat(
                    ctx.getContentResolver(),
                    android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
            if (scale == 0f) return false;
            float ta = android.provider.Settings.Global.getFloat(
                    ctx.getContentResolver(),
                    android.provider.Settings.Global.TRANSITION_ANIMATION_SCALE, 1f);
            return ta != 0f;
        } catch (Exception e) {
            return true;
        }
    }

    public boolean animsOnly() {
        return sp.getBoolean("anims", true);
    }

    public void setAnims(boolean v) {
        sp.edit().putBoolean("anims", v).apply();
    }
}
