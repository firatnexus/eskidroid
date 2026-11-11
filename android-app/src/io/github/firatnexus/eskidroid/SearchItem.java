package io.github.firatnexus.eskidroid;

/** Arama sonucu: F-Droid paketi ya da archive.org öğesi. */
public final class SearchItem {
    public String id;        // paket adı veya arşiv tanıyıcısı
    public String name;      // temiz ad
    public String source;    // "fdroid" | "archive"
    public String iconUrl;   // olabilir
    public String summary;   // olabilir
    public String version;   // olabilir (archive başlığından çıkarıldı)
    public boolean spam;

    public String sourceLabel() {
        return "fdroid".equals(source) ? "F-Droid" : "archive.org";
    }
}
