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
 * Ayarlar ekranı: uyumluluk modu, depolama, önbellek, hakkında, kaynak bağlantıları.
 */
public final class SettingsScreen {

    private final MainActivity main;
    private final LinearLayout root;
    private final TextView storageLine;
    private final List<TextView> compatLabels = new ArrayList<>();
    private final List<View> compatLines = new ArrayList<>();
    private TextView compatInfoView;

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

        TextView title = Ui.text(main, "Ayarlar", Ui.WHITE, 22, 2);
        title.setLetterSpacing(-0.02f);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        title.setLayoutParams(tlp);
        col.addView(title);

        // --- Uyumluluk modu ---
        LinearLayout compatCard = Ui.card(main);
        compatCard.addView(Ui.text(main, "Uyumluluk modu", Ui.WHITE, 15, 1));
        TextView cd = Ui.text(main,
                "Otomatik: cihazının gerektirdiği en düşük hedef SDK'ya yamalar — "
                        + "runtime izinler, servis kısıtları ve cleartext yasağı gibi "
                        + "eski uygulamaları çökerten değişiklikler en aza iner.\n"
                        + "Yüksek: her yamalı APK'da hedef SDK 28 (eski davranış).",
                Ui.GRAPHITE, 12, 0);
        cd.setPadding(0, Ui.dp(6), 0, Ui.dp(8));
        compatCard.addView(cd);
        String mode = LibraryManager.get(main).compatMode();
        addCompatChip(compatCard, "Otomatik", LibraryManager.COMPAT_AUTO, mode);
        addCompatChip(compatCard, "Yüksek", LibraryManager.COMPAT_HIGH, mode);
        compatInfoView = Ui.text(main, compatInfo(), Ui.GRAPHITE, 11, 0);
        compatInfoView.setPadding(0, Ui.dp(8), 0, 0);
        compatCard.addView(compatInfoView);
        col.addView(compatCard);
        compatCard.requestFocus(); // odak aramaya kaymasın

        // --- Depolama ---
        LinearLayout storageCard = Ui.card(main);
        LinearLayout.LayoutParams s1 = new LinearLayout.LayoutParams(-1, -2);
        s1.topMargin = Ui.dp(12);
        storageCard.setLayoutParams(s1);
        storageCard.addView(Ui.text(main, "Depolama", Ui.WHITE, 15, 1));
        storageLine = Ui.text(main, "", Ui.GRAPHITE, 12, 0);
        storageLine.setPadding(0, Ui.dp(6), 0, Ui.dp(4));
        storageCard.addView(storageLine);
        TextView clearImg = Ui.ghostPill(main, "Görsel önbelleğini temizle");
        clearImg.setOnClickListener(v -> {
            ImageLoader.get(main).clearCache();
            Toast.makeText(main, "Görsel önbelleği temizlendi", Toast.LENGTH_SHORT).show();
            refresh();
        });
        storageCard.addView(marginV(clearImg));
        TextView clearLib = Ui.ghostPill(main, "Kütüphaneyi temizle");
        clearLib.setOnClickListener(v -> main.confirm(
                "Kütüphaneyi temizle",
                "Depodaki tüm APK'lar silinecek. Emin misin?",
                new String[]{"Sil", "Vazgeç"},
                new Runnable[]{() -> LibraryManager.get(main).clearAll(), null}));
        storageCard.addView(marginV(clearLib));
        col.addView(storageCard);

        // --- Hakkında ---
        LinearLayout aboutCard = Ui.card(main);
        LinearLayout.LayoutParams a1 = new LinearLayout.LayoutParams(-1, -2);
        a1.topMargin = Ui.dp(12);
        aboutCard.setLayoutParams(a1);
        aboutCard.addView(Ui.text(main, "Hakkında", Ui.WHITE, 15, 1));
        TextView about = Ui.text(main,
                "EskiDroid v1.2\n"
                        + "Eski Android uygulamalarını arar, indirir; gerekirse hedef SDK'yı "
                        + "yükseltip kendinden imzalı anahtarla yeniden imzalar ve kurar.\n"
                        + "İmza anahtarı bu cihazda üretilir ve saklanır.",
                Ui.GRAPHITE, 12, 0);
        about.setPadding(0, Ui.dp(6), 0, 0);
        aboutCard.addView(about);
        col.addView(aboutCard);

        // --- Kaynaklar ---
        LinearLayout srcCard = Ui.card(main);
        LinearLayout.LayoutParams r1 = new LinearLayout.LayoutParams(-1, -2);
        r1.topMargin = Ui.dp(12);
        srcCard.setLayoutParams(r1);
        srcCard.addView(Ui.text(main, "Kaynaklar", Ui.WHITE, 15, 1));
        addLink(srcCard, "F-Droid", "https://f-droid.org");
        addLink(srcCard, "Internet Archive", "https://archive.org");
        addLink(srcCard, "GitHub — firatnexus/eskidroid", "https://github.com/firatnexus/eskidroid");
        col.addView(srcCard);

        refresh();
    }

    public View view() {
        return root;
    }

    public void onShow() {
        refresh();
    }

    private String compatInfo() {
        LibraryManager lib = LibraryManager.get(main);
        int min = lib.requiredMin();
        return "Cihaz: API " + Build.VERSION.SDK_INT
                + (min > 0 ? " • gereken en düşük hedef SDK: " + min : " • yamasız kurulabilir")
                + " • yama hedefi: " + lib.patchTarget();
    }

    private void addCompatChip(LinearLayout parent, String label, final String value, String current) {
        LinearLayout col = new LinearLayout(main);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(Ui.dp(12), Ui.dp(6), Ui.dp(12), Ui.dp(2));
        TextView t = Ui.text(main, label, Ui.GRAPHITE, 13, 1);
        col.addView(t);
        View line = new View(main);
        line.setLayoutParams(new LinearLayout.LayoutParams(-1, Ui.dp(2)));
        line.setBackgroundColor(Ui.BLUE);
        line.setVisibility(value.equals(current) ? View.VISIBLE : View.INVISIBLE);
        col.addView(line);
        col.setOnClickListener(v -> {
            LibraryManager.get(main).setCompatMode(value);
            updateCompatChips(value);
            main.refreshVersions();
            Toast.makeText(main, "Uyumluluk modu: " + label, Toast.LENGTH_SHORT).show();
        });
        parent.addView(col);
        compatLabels.add(t);
        compatLines.add(line);
    }

    private void updateCompatChips(String active) {
        String[] vals = {LibraryManager.COMPAT_AUTO, LibraryManager.COMPAT_HIGH};
        for (int i = 0; i < compatLabels.size(); i++) {
            boolean on = vals[i].equals(active);
            compatLabels.get(i).setTextColor(on ? Ui.WHITE : Ui.GRAPHITE);
            compatLines.get(i).setVisibility(on ? View.VISIBLE : View.INVISIBLE);
        }
        if (compatInfoView != null) compatInfoView.setText(compatInfo());
    }

    private void addLink(LinearLayout parent, String label, final String url) {
        TextView t = Ui.text(main, label, Ui.MIST, 14, 0);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(0, Ui.dp(10), 0, 0);
        t.setOnClickListener(v -> {
            try {
                main.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception e) {
                Toast.makeText(main, "Açılamadı", Toast.LENGTH_SHORT).show();
            }
        });
        parent.addView(t);
    }

    public void refresh() {
        LibraryManager lib = LibraryManager.get(main);
        long libB = lib.totalSize();
        long imgB = ImageLoader.get(main).cacheBytes();
        storageLine.setText("Depo: " + (libB > 0 ? Ui.size(libB) : "boş")
                + " • Görsel önbelleği: " + (imgB > 0 ? Ui.size(imgB) : "0 KB"));
    }

    private View marginV(View v) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = Ui.dp(8);
        v.setLayoutParams(lp);
        return v;
    }
}
