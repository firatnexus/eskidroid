package io.github.firatnexus.eskidroid;

import java.util.ArrayList;
import java.util.List;

/**
 * Arama sonucu: F-Droid paketi, archive.org öğesi veya GitHub deposu.
 * Aynı uygulamanın farklı kaynaklardaki öğeleri tek grupta birleştirilir.
 */
public final class SearchItem {
    public String id;        // paket adı / arşiv tanıyıcısı / repo (owner/name)
    public String name;      // temiz ad
    public String source;    // "fdroid" | "archive" | "github"
    public String iconUrl;   // olabilir
    public String summary;   // olabilir
    public String version;   // olabilir
    public boolean spam;
    public long downloads = -1;   // archive.org / github yıldız yerine kullanılır
    public int stars = -1;        // github
    public double score = 0;      // alaka puanı (gruplamada sıralama için)

    /** Gruplama anahtarı: normalleştirilmiş ad. */
    public String key() {
        return NameUtil.normalize(name != null && !name.isEmpty() ? name : id);
    }

    public String sourceLabel() {
        return sourceLabel(source);
    }

    public static String sourceLabel(String src) {
        if ("fdroid".equals(src)) return "F-Droid";
        if ("github".equals(src)) return "GitHub";
        return "archive.org";
    }
}

/** Aynı uygulamanın kaynaklar arası birleşimi (tek sonuç kartı). */
final class AppGroup {
    final String key;
    final String name;
    String summary;
    String iconUrl;       // en güvenilir kaynağın ikonu
    final List<SearchItem> items = new ArrayList<>();

    AppGroup(String key, String name) {
        this.key = key;
        this.name = name;
    }

    List<String> sources() {
        List<String> s = new ArrayList<>();
        for (SearchItem it : items) if (!s.contains(it.source)) s.add(it.source);
        return s;
    }

    /** Alaka puanı: ad eşleşmesi + kaynak güvenilirliği + popülerlik. */
    double score(String q) {
        double s = 0;
        String nq = NameUtil.normalize(q);
        String nn = NameUtil.normalize(name);
        if (nn.equals(nq)) s += 100;
        else if (nn.startsWith(nq)) s += 60;
        else if (nn.contains(nq)) s += 30;
        for (SearchItem it : items) {
            if ("fdroid".equals(it.source)) s += 15;
            else if ("github".equals(it.source)) s += it.stars / 200.0;
            if (it.downloads > 0) s += Math.min(10, it.downloads / 20000.0);
            if (it.spam) s -= 25;
        }
        return s;
    }
}
