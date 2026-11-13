package io.github.firatnexus.eskidroid;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Ayarlar ekranı: Görünüm (Sistem/Aydınlık/Karanlık), Dil (Sistem + 5 dil),
 * Sesler, Animasyonlar, uyumluluk modu, depolama, hakkında, kaynak bağlantıları.
 * Tema/dil değişikliği anında uygulanır (MainActivity.rebuildUi).
 */
public final class SettingsScreen {

    private final MainActivity main;
    private final LinearLayout root;
    private TextView storageLine;
    private final List<TextView> compatLabels = new ArrayList<>();
    private final List<View> compatLines = new ArrayList<>();
    private final List<String> compatValues = new ArrayList<>();
    private final List<TextView> themeLabels = new ArrayList<>();
    private final List<View> themeLines = new ArrayList<>();
    private final List<String> themeValues = new ArrayList<>();
    private final List<TextView> langLabels = new ArrayList<>();
    private final List<View> langLines = new ArrayList<>();
    private final List<String> langValues = new ArrayList<>();
    private final List<TextView> fxBtns = new ArrayList<>();
    private TextView compatInfoView;
    private TextView soundState;
    private TextView animState;

    public SettingsScreen(MainActivity main) {
        this.main = main;
        root = new LinearLayout(main);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.CANVAS);

        ScrollView scroll = new ScrollView(main);
        LinearLayout col = new LinearLayout(main);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dp(16), Ui.dp(16), Ui.dp(16), Ui.dp(120));
        scroll.addView(col, new FrameLayout.LayoutParams(-1, -2));
        scroll.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(scroll);

        TextView title = Ui.text(main, L.s(R.string.tab_settings), Ui.INK, 22, 2);
        title.setLetterSpacing(-0.02f);
        col.addView(title);

        // --- Görünüm (tema) ---
        LinearLayout themeCard = Ui.card(main);
        themeCard.addView(Ui.text(main, L.s(R.string.appearance), Ui.INK, 15, 1));
        TextView td = Ui.text(main, L.s(R.string.appearance_desc), Ui.FOG, 12, 0);
        td.setPadding(0, Ui.dp(6), 0, Ui.dp(8));
        themeCard.addView(td);
        String theme = Prefs.get(main).theme();
        addChoice(themeCard, L.s(R.string.theme_system), Prefs.THEME_SYSTEM, theme, themeLabels, themeLines, themeValues, () -> main.rebuildUi());
        addChoice(themeCard, L.s(R.string.theme_light), Prefs.THEME_LIGHT, theme, themeLabels, themeLines, themeValues, () -> main.rebuildUi());
        addChoice(themeCard, L.s(R.string.theme_dark), Prefs.THEME_DARK, theme, themeLabels, themeLines, themeValues, () -> main.rebuildUi());
        col.addView(margined(themeCard));

        // --- Dil ---
        LinearLayout langCard = Ui.card(main);
        langCard.addView(Ui.text(main, L.s(R.string.language), Ui.INK, 15, 1));
        TextView ld = Ui.text(main, L.s(R.string.language_desc), Ui.FOG, 12, 0);
        ld.setPadding(0, Ui.dp(6), 0, Ui.dp(8));
        langCard.addView(ld);
        String lang = Prefs.get(main).lang();
        addChoice(langCard, L.s(R.string.lang_system), Prefs.LANG_SYSTEM, lang, langLabels, langLines, langValues, () -> main.rebuildUi());
        addChoice(langCard, "Türkçe", "tr", lang, langLabels, langLines, langValues, () -> main.rebuildUi());
        addChoice(langCard, "English", "en", lang, langLabels, langLines, langValues, () -> main.rebuildUi());
        addChoice(langCard, "Deutsch", "de", lang, langLabels, langLines, langValues, () -> main.rebuildUi());
        addChoice(langCard, "Español", "es", lang, langLabels, langLines, langValues, () -> main.rebuildUi());
        addChoice(langCard, "Русский", "ru", lang, langLabels, langLines, langValues, () -> main.rebuildUi());
        col.addView(margined(langCard));

        // --- Sesler ve animasyonlar ---
        LinearLayout fxCard = Ui.card(main);
        fxCard.addView(Ui.text(main, L.s(R.string.effects), Ui.INK, 15, 1));
        soundState = Ui.text(main, "", Ui.FOG, 12, 0);
        soundState.setPadding(0, Ui.dp(6), 0, 0);
        fxCard.addView(soundState);
        TextView soundBtn = Ui.ghostButton(main, "");
        soundBtn.setOnClickListener(v -> {
            Prefs p = Prefs.get(main);
            p.setSounds(!p.sounds());
            refreshFx();
            Toast.makeText(main, L.s(R.string.sounds) + ": " + onOff(p.sounds()), Toast.LENGTH_SHORT).show();
        });
        fxBtns.add(soundBtn);
        fxCard.addView(marginV(soundBtn));
        animState = Ui.text(main, "", Ui.FOG, 12, 0);
        animState.setPadding(0, Ui.dp(8), 0, 0);
        fxCard.addView(animState);
        TextView animBtn = Ui.ghostButton(main, "");
        animBtn.setOnClickListener(v -> {
            Prefs p = Prefs.get(main);
            p.setAnims(!p.animsOnly());
            refreshFx();
            Toast.makeText(main, L.s(R.string.animations) + ": " + onOff(p.animsOnly()), Toast.LENGTH_SHORT).show();
        });
        fxBtns.add(animBtn);
        fxCard.addView(marginV(animBtn));
        col.addView(margined(fxCard));

        // --- Uyumluluk modu ---
        LinearLayout compatCard = Ui.card(main);
        compatCard.addView(Ui.text(main, L.s(R.string.compat_mode), Ui.INK, 15, 1));
        TextView cd = Ui.text(main, L.s(R.string.compat_desc), Ui.FOG, 12, 0);
        cd.setPadding(0, Ui.dp(6), 0, Ui.dp(8));
        compatCard.addView(cd);
        String mode = LibraryManager.get(main).compatMode();
        addChoice(compatCard, L.s(R.string.compat_auto), LibraryManager.COMPAT_AUTO, mode, compatLabels, compatLines, compatValues, null);
        addChoice(compatCard, L.s(R.string.compat_high), LibraryManager.COMPAT_HIGH, mode, compatLabels, compatLines, compatValues, null);
        compatInfoView = Ui.text(main, compatInfo(), Ui.FOG, 11, 0);
        compatInfoView.setPadding(0, Ui.dp(8), 0, 0);
        compatCard.addView(compatInfoView);
        col.addView(margined(compatCard));
        compatCard.requestFocus(); // odak aramaya kaymasın

        // --- Depolama ---
        LinearLayout storageCard = Ui.card(main);
        storageCard.addView(Ui.text(main, L.s(R.string.storage), Ui.INK, 15, 1));
        storageLine = Ui.text(main, "", Ui.FOG, 12, 0);
        storageLine.setPadding(0, Ui.dp(6), 0, Ui.dp(4));
        storageCard.addView(storageLine);
        TextView clearImg = Ui.ghostButton(main, L.s(R.string.clear_image_cache));
        clearImg.setOnClickListener(v -> {
            ImageLoader.get(main).clearCache();
            Toast.makeText(main, L.s(R.string.image_cache_cleared), Toast.LENGTH_SHORT).show();
            refresh();
        });
        storageCard.addView(marginV(clearImg));
        TextView clearLib = Ui.dangerButton(main, L.s(R.string.clear_library));
        clearLib.setOnClickListener(v -> main.confirm(
                L.s(R.string.clear_library),
                L.s(R.string.clear_library_confirm),
                new String[]{L.s(R.string.delete), L.s(R.string.cancel)},
                new Runnable[]{() -> LibraryManager.get(main).clearAll(), null}));
        storageCard.addView(marginV(clearLib));
        col.addView(margined(storageCard));

        // --- Hakkında ---
        LinearLayout aboutCard = Ui.card(main);
        aboutCard.addView(Ui.text(main, L.s(R.string.about), Ui.INK, 15, 1));
        TextView about = Ui.text(main, L.s(R.string.about_text), Ui.FOG, 12, 0);
        about.setPadding(0, Ui.dp(6), 0, 0);
        aboutCard.addView(about);
        col.addView(margined(aboutCard));

        // --- Kaynaklar ---
        LinearLayout srcCard = Ui.card(main);
        srcCard.addView(Ui.text(main, L.s(R.string.sources), Ui.INK, 15, 1));
        addLink(srcCard, "F-Droid", "https://f-droid.org");
        addLink(srcCard, "Internet Archive", "https://archive.org");
        addLink(srcCard, "GitHub — firatnexus/eskidroid", "https://github.com/firatnexus/eskidroid");
        col.addView(margined(srcCard));

        refresh();
        refreshFx();
    }

    public View view() {
        return root;
    }

    public void onShow() {
        refresh();
    }

    private String onOff(boolean v) {
        return v ? L.s(R.string.on) : L.s(R.string.off);
    }

    private void refreshFx() {
        Prefs p = Prefs.get(main);
        soundState.setText(L.s(R.string.sounds_desc));
        animState.setText(L.s(R.string.animations_desc));
        for (int i = 0; i < fxBtns.size(); i++) {
            TextView b = fxBtns.get(i);
            if (i == 0) b.setText((p.sounds() ? "✓ " : "") + L.s(R.string.sounds_toggle));
            else b.setText((p.animsOnly() ? "✓ " : "") + L.s(R.string.animations_toggle));
        }
    }

    private String compatInfo() {
        LibraryManager lib = LibraryManager.get(main);
        int min = lib.requiredMin();
        return L.s(R.string.device_api, Build.VERSION.SDK_INT)
                + (min > 0 ? " • " + L.s(R.string.min_target, min) : " • " + L.s(R.string.can_install_unpatched))
                + " • " + L.s(R.string.patch_target, lib.patchTarget());
    }

    /** Ortak seçim çipi (tema/dil/uyumluluk): seçili = Ink metin + mavi alt çizgi. */
    private void addChoice(LinearLayout parent, String label, final String value, String current,
                           final List<TextView> labels, final List<View> lines,
                           final List<String> values, final Runnable after) {
        LinearLayout col = new LinearLayout(main);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(Ui.dp(12), Ui.dp(6), Ui.dp(12), Ui.dp(2));
        TextView t = Ui.text(main, label, value.equals(current) ? Ui.INK : Ui.FOG, 13, 1);
        col.addView(t);
        View line = new View(main);
        line.setLayoutParams(new LinearLayout.LayoutParams(-1, Ui.dp(2)));
        line.setBackgroundColor(Ui.BLUE);
        line.setVisibility(value.equals(current) ? View.VISIBLE : View.INVISIBLE);
        col.addView(line);
        col.setOnClickListener(v -> {
            UiFx.tap(main);
            if (labels == compatLabels) {
                LibraryManager.get(main).setCompatMode(value);
                updateChoices(labels, lines, values, value);
                if (compatInfoView != null) compatInfoView.setText(compatInfo());
                main.refreshVersions();
                Toast.makeText(main, L.s(R.string.compat_mode) + ": " + label, Toast.LENGTH_SHORT).show();
            } else if (labels == themeLabels) {
                Prefs.get(main).setTheme(value);
                if (after != null) after.run();
            } else {
                Prefs.get(main).setLang(value);
                if (after != null) after.run();
            }
        });
        parent.addView(col);
        labels.add(t);
        lines.add(line);
        values.add(value);
    }

    private void updateChoices(List<TextView> labels, List<View> lines, List<String> values, String active) {
        for (int i = 0; i < labels.size(); i++) {
            boolean on = values.get(i).equals(active);
            labels.get(i).setTextColor(on ? Ui.INK : Ui.FOG);
            lines.get(i).setVisibility(on ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private void addLink(LinearLayout parent, String label, final String url) {
        TextView t = Ui.link(main, label);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setOnClickListener(v -> main.openUrl(url));
        parent.addView(t);
    }

    public void refresh() {
        LibraryManager lib = LibraryManager.get(main);
        long libB = lib.totalSize();
        long imgB = ImageLoader.get(main).cacheBytes();
        storageLine.setText(L.s(R.string.storage_line,
                libB > 0 ? Ui.size(libB) : L.s(R.string.empty),
                imgB > 0 ? Ui.size(imgB) : "0 KB"));
    }

    private View margined(View v) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(12);
        v.setLayoutParams(lp);
        return v;
    }

    private View marginV(View v) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = Ui.dp(8);
        v.setLayoutParams(lp);
        return v;
    }
}
