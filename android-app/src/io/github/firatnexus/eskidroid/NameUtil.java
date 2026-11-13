package io.github.firatnexus.eskidroid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Tanıyıcı adlardan temiz ad + sürüm çıkarır ("clash-of-clans-v-13.675.20"
 * → "Clash Of Clans" + "13.675.20") ve spam/mirror kaynakları işaretler.
 */
public final class NameUtil {

    private static final String[] SPAM = {
            "pureapkap", "freefullapkgames", "apkpure", "revdl", "happymod", "modapk",
            "andropalace", "apkd", "androidapksfree", "fullapk", "downloadfreefull",
            "blogspot", "weebly", "wixsite", "wordpress"
    };

    private NameUtil() {
    }

    /** ["Temiz Ad", "sürüm"] döndürür; sürüm çıkarılamazsa ikinci eleman boş. */
    public static String[] clean(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.length() > 4 && s.toLowerCase(Locale.ROOT).endsWith(".apk")) {
            s = s.substring(0, s.length() - 4);
        }
        String[] toks = s.split("[-_. ]+");
        List<String> ver = new ArrayList<>();
        int i = toks.length - 1;
        while (i >= 0) {
            String t = toks[i];
            String tl = t.toLowerCase(Locale.ROOT);
            if (tl.isEmpty() || tl.equals("apk") || tl.equals("v") || tl.equals("ver")
                    || tl.equals("version")) {
                i--;
                continue;
            }
            if (tl.matches("v?\\d+([.]\\d+)*[a-z]?")) {
                ver.add(0, t);
                i--;
                continue;
            }
            break;
        }
        List<String> name = new ArrayList<>();
        for (; i >= 0; i--) name.add(0, toks[i]);
        if (name.isEmpty()) return new String[]{title(s), ""};
        StringBuilder v = new StringBuilder();
        for (String t : ver) {
            if (v.length() > 0) v.append('.');
            v.append(t);
        }
        return new String[]{title(name), v.toString()};
    }

    private static String title(List<String> toks) {
        StringBuilder sb = new StringBuilder();
        for (String t : toks) {
            if (t == null || t.isEmpty()) continue;
            sb.append(Character.toUpperCase(t.charAt(0))).append(t.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    private static String title(String s) {
        return title(Arrays.asList(s.split("[-_. ]+")));
    }

    /** Spam/mirror görünümlü kaynak (pureapkap, .blogspot.com, .com son ekleri...). */
    public static boolean spam(String id, String title) {
        String s = ((id == null ? "" : id) + " " + (title == null ? "" : title)).toLowerCase(Locale.ROOT);
        for (String t : SPAM) {
            if (s.contains(t)) return true;
        }
        for (String tok : s.split("[-_. /]+")) {
            if (tok.equals("com") || tok.equals("net") || tok.equals("org")) return true;
        }
        return false;
    }

    /** F-Droid paket adından yaklaşık ad üretir (HTML'de ad yoksa yedek). */
    public static String prettyPkg(String pkg) {
        String last = pkg.substring(pkg.lastIndexOf('.') + 1);
        StringBuilder sb = new StringBuilder();
        for (String w : last.split("_")) {
            if (w.isEmpty()) continue;
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    /** Gruplama anahtarı: küçük harf, yalnız harf+rakam (boşluk/nokta yok). */
    public static String normalize(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : s.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(c)) sb.append(c);
        }
        return sb.toString();
    }

    /** Dosya adından sürüm ayıklar (NameUtil.clean sarmalayıcı; xapk/apks dahil). */
    public static String versionOf(String fileName) {
        if (fileName == null) return "";
        String f = fileName.toLowerCase(Locale.ROOT);
        if (f.endsWith(".apk") || f.endsWith(".xapk") || f.endsWith(".apks")) {
            f = f.substring(0, f.lastIndexOf('.'));
        }
        String[] nv = clean(f);
        return nv[1];
    }
}
