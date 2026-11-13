package io.github.firatnexus.eskidroid;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Kaynak arama motorları: F-Droid, Internet Archive (derin arama) ve GitHub.
 * Her kaynak bağımsız çalışır; biri hata verirse diğerleri sürer.
 * Arama sonuçları AppGroup'larda (uygulama adına göre) birleştirilir.
 */
public final class Sources {

    public static final String FDROID = "fdroid";
    public static final String ARCHIVE = "archive";
    public static final String GITHUB = "github";

    /** Bir kaynağın arama sonucu (başarı/hata ayrı tutulur). */
    public static final class SourceResult {
        public final String source;
        public final List<SearchItem> items = new ArrayList<>();
        public String error;   // null → başarı

        SourceResult(String source) {
            this.source = source;
        }
    }

    private Sources() {
    }

    // ============================ F-Droid ============================

    public static SourceResult searchFdroid(String q) {
        SourceResult r = new SourceResult(FDROID);
        try {
            String url = "https://f-droid.org/en/search?count=25&fdfilter=" + URLEncoder.encode(q, "UTF-8");
            String html = Net.get(url);
            Pattern block = Pattern.compile(
                    "<a class=\"package-header\" href=\"/en/packages/([a-zA-Z0-9_.]+)/\">(.*?)</a>",
                    Pattern.DOTALL);
            Matcher m = block.matcher(html);
            while (m.find() && r.items.size() < 20) {
                SearchItem it = new SearchItem();
                it.id = m.group(1);
                it.source = FDROID;
                String inner = m.group(2);
                Matcher img = Pattern.compile("src=\"([^\"]+)\"").matcher(inner);
                if (img.find()) it.iconUrl = img.group(1);
                Matcher nm = Pattern.compile("<h4 class=\"package-name\">(.*?)</h4>", Pattern.DOTALL).matcher(inner);
                if (nm.find()) it.name = Html.strip(nm.group(1));
                Matcher sm = Pattern.compile("<span class=\"package-summary\">(.*?)</span>", Pattern.DOTALL).matcher(inner);
                if (sm.find()) it.summary = Html.strip(sm.group(1));
                if (it.name == null || it.name.isEmpty()) it.name = NameUtil.prettyPkg(it.id);
                r.items.add(it);
            }
        } catch (Exception e) {
            r.error = e.getMessage();
        }
        return r;
    }

    // ============================ archive.org (derin arama) ============================

    /**
     * Derin arama: başlık + alternatif yazımlar + paket adı sorguları,
     * apk koleksiyonları (apkarchive, softwarelibrary_android) ve sayfalama.
     */
    public static SourceResult searchArchive(String q, int page) {
        SourceResult r = new SourceResult(ARCHIVE);
        try {
            Set<String> seen = new HashSet<>();
            String[] queries = {
                    // boşluklu başlık
                    "(collection:(apkarchive) OR collection:(softwarelibrary_android)) AND title:(\"" + q + "\")",
                    // boşluksuz yazım (clashofclans gibi)
                    "(collection:(apkarchive) OR collection:(softwarelibrary_android)) AND title:(\"" + NameUtil.normalize(q) + "\")",
                    // paket adı içinde (com.uygulama)
                    "(collection:(apkarchive) OR collection:(softwarelibrary_android)) AND identifier:(" + q.toLowerCase(java.util.Locale.ROOT) + ")",
            };
            for (String query : queries) {
                fetchArchivePage(query, page, seen, r);
            }
        } catch (Exception e) {
            r.error = e.getMessage();
        }
        return r;
    }

    private static void fetchArchivePage(String query, int page, Set<String> seen, SourceResult r) throws Exception {
        int rows = 25;
        int start = page * rows;
        String url = "https://archive.org/advancedsearch.php?q=" +
                URLEncoder.encode(query, "UTF-8") +
                "&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=downloads&fl%5B%5D=mediatype" +
                "&rows=" + rows + "&page=" + (page + 1) + "&sort%5B%5D=downloads+desc&output=json";
        JSONObject obj = new JSONObject(Net.get(url));
        JSONArray docs = obj.getJSONObject("response").getJSONArray("docs");
        for (int i = 0; i < docs.length() && r.items.size() < 40; i++) {
            JSONObject d = docs.getJSONObject(i);
            String id = d.optString("identifier");
            if (id.isEmpty() || !seen.add(id)) continue;
            SearchItem it = new SearchItem();
            it.id = id;
            String title = d.optString("title");
            String[] nv = NameUtil.clean(title.isEmpty() ? id : title);
            it.name = nv[0];
            it.version = nv[1];
            it.source = ARCHIVE;
            it.spam = NameUtil.spam(id, title);
            it.downloads = d.optLong("downloads", -1);
            it.iconUrl = "https://archive.org/services/img/" + id;
            r.items.add(it);
        }
    }

    /** Arşiv öğesinin dosya listesinden .apk/.xapk/.apks sürümlerini çıkarır. */
    public static List<VersionInfo> archiveVersions(String identifier) throws Exception {
        JSONObject files = new JSONObject(Net.get("https://archive.org/metadata/" + identifier));
        List<VersionInfo> out = new ArrayList<>();
        JSONArray arr = files.getJSONArray("files");
        for (int i = 0; i < arr.length(); i++) {
            JSONObject f = arr.getJSONObject(i);
            String fn = f.optString("name", "");
            String low = fn.toLowerCase(java.util.Locale.ROOT);
            if (!low.endsWith(".apk") && !low.endsWith(".xapk") && !low.endsWith(".apks")) continue;
            VersionInfo v = new VersionInfo();
            String ver = NameUtil.versionOf(fn);
            v.label = ver.isEmpty() ? fn : ver;
            v.url = "https://archive.org/download/" + identifier + "/" +
                    URLEncoder.encode(fn, "UTF-8").replace("+", "%20");
            v.size = f.optLong("size", -1);
            v.date = f.optLong("mtime", -1) * 1000L;
            v.sourceTag = ARCHIVE;
            // arşiv meta: format alanında ABI bilgisi olabilir
            String format = f.optString("format", "");
            if (format.contains("ARM")) v.abi = format;
            out.add(v);
        }
        return out;
    }

    /** Arşiv öğesi meta bilgisi (detay ekranı için). */
    public static JSONObject archiveMeta(String identifier) throws Exception {
        return new JSONObject(Net.get("https://archive.org/metadata/" + identifier));
    }

    // ============================ GitHub ============================

    /** GitHub depo araması: ad/açıklamada sorgu + android/apk konuları. */
    public static SourceResult searchGithub(String q) {
        SourceResult r = new SourceResult(GITHUB);
        try {
            String query = URLEncoder.encode(
                    q + " in:name,description apk", "UTF-8");
            String url = "https://api.github.com/search/repositories?q=" + query +
                    "&sort=stars&order=desc&per_page=15";
            JSONObject obj = new JSONObject(Net.get(url));
            JSONArray items = obj.optJSONArray("items");
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject d = items.getJSONObject(i);
                    SearchItem it = new SearchItem();
                    it.id = d.optString("full_name");
                    it.source = GITHUB;
                    it.name = d.optString("name");
                    it.summary = d.optString("description");
                    it.stars = d.optInt("stargazers_count", -1);
                    String desc = it.summary == null ? "" : it.summary.toLowerCase(java.util.Locale.ROOT);
                    it.spam = desc.contains("mod apk") || desc.contains("hack") || desc.contains("cheat");
                    it.iconUrl = d.optJSONObject("owner") != null
                            ? d.getJSONObject("owner").optString("avatar_url") : null;
                    if (it.name == null || it.name.isEmpty()) it.name = it.id;
                    r.items.add(it);
                }
            }
        } catch (Exception e) {
            r.error = e.getMessage();
        }
        return r;
    }

    /** GitHub deposunun APK'lı sürümleri (releases → assets). */
    public static List<VersionInfo> githubVersions(String repo) throws Exception {
        List<VersionInfo> out = new ArrayList<>();
        JSONArray rels = new JSONArray(Net.get(
                "https://api.github.com/repos/" + repo + "/releases?per_page=30"));
        for (int i = 0; i < rels.length(); i++) {
            JSONObject rel = rels.getJSONObject(i);
            JSONArray assets = rel.optJSONArray("assets");
            if (assets == null) continue;
            for (int j = 0; j < assets.length(); j++) {
                JSONObject a = assets.getJSONObject(j);
                String name = a.optString("name", "");
                String low = name.toLowerCase(java.util.Locale.ROOT);
                if (!low.endsWith(".apk") && !low.endsWith(".xapk") && !low.endsWith(".apks")) continue;
                VersionInfo v = new VersionInfo();
                String ver = rel.optString("tag_name", "");
                if (ver.startsWith("v")) ver = ver.substring(1);
                v.label = ver.isEmpty() ? NameUtil.versionOf(name) : ver;
                v.url = a.optString("browser_download_url");
                v.size = a.optLong("size", -1);
                v.date = a.optLong("created_at", 0) > 0
                        ? java.text.DateFormat.getDateTimeInstance().parse(a.optString("published_at", a.optString("created_at"))).getTime()
                        : -1;
                try {
                    String iso = a.optString("published_at", a.optString("created_at"));
                    if (iso.endsWith("Z")) {
                        java.time.Instant inst = java.time.Instant.parse(iso);
                        v.date = inst.toEpochMilli();
                    }
                } catch (Exception ignored) {
                }
                v.versionCode = a.optLong("id", -1);
                v.sourceTag = GITHUB;
                v.whatsNew = rel.optString("body", "");
                if (v.whatsNew != null && v.whatsNew.isEmpty()) v.whatsNew = null;
                out.add(v);
            }
        }
        return out;
    }

    // ============================ F-Droid detay ============================

    /** F-Droid paket sayfasından zengin detay verisi. */
    public static final class FdroidDetail {
        public String description;
        public String license;
        public String licenseUrl;
        public String website;
        public String sourceCode;
        public String issueTracker;
        public String changelogUrl;
        public String author;
        public String lastUpdated;
        public List<String> antiFeatures = new ArrayList<>();
        public List<String> screenshots = new ArrayList<>();
        public List<VersionInfo> versions = new ArrayList<>();
        public final Map<String, VersionInfo> perVersion = new HashMap<>(); // label → genişletilmiş
    }

    public static FdroidDetail fdroidDetail(String pkg) throws Exception {
        FdroidDetail d = new FdroidDetail();
        String html = Net.get("https://f-droid.org/en/packages/" + pkg + "/");

        // Açıklama
        Matcher m = Pattern.compile("class=\"package-description\"[^>]*>(.*?)</div>", Pattern.DOTALL).matcher(html);
        if (m.find()) d.description = Html.strip(m.group(1));

        // Bağlantılar (package-link listesi)
        d.license = linkText(html, "license");
        d.licenseUrl = linkHref(html, "license");
        d.website = linkHref(html, "website");
        d.sourceCode = linkHref(html, "source_code");
        d.issueTracker = linkHref(html, "issue_tracker");
        d.changelogUrl = linkHref(html, "changelog");

        // Anti-features
        Matcher af = Pattern.compile("class=\"package-anti-features\".*?<ul[^>]*>(.*?)</ul>", Pattern.DOTALL).matcher(html);
        if (af.find()) {
            Matcher li = Pattern.compile("<li[^>]*>(.*?)</li>", Pattern.DOTALL).matcher(af.group(1));
            while (li.find()) {
                String t = Html.strip(li.group(1));
                if (!t.isEmpty()) d.antiFeatures.add(t);
            }
        }
        // Anti-features alternatif konum (başlık + liste)
        if (d.antiFeatures.isEmpty()) {
            int i = html.indexOf("Anti-Features");
            if (i > 0) {
                String chunk = html.substring(i, Math.min(html.length(), i + 3000));
                Matcher li = Pattern.compile("<li[^>]*>(.*?)</li>", Pattern.DOTALL).matcher(chunk);
                while (li.find()) {
                    String t = Html.strip(li.group(1));
                    if (!t.isEmpty() && !t.toLowerCase(java.util.Locale.ROOT).contains("license")
                            && t.length() < 60) d.antiFeatures.add(t);
                }
            }
        }

        // Ekran görüntüleri
        Matcher imgs = Pattern.compile("<img[^>]*>").matcher(html);
        while (imgs.find()) {
            String tag = imgs.group();
            if (!tag.contains("app screenshot")) continue;
            Matcher s = Pattern.compile("src=\"([^\"]+)\"").matcher(tag);
            if (s.find()) d.screenshots.add(s.group(1));
        }

        // Sürüm blokları: ad/kod, tarih, ABI, izinler, whats-new
        Matcher ver = Pattern.compile(
                "<li class=\"package-version\"[^>]*>(.*?)(?=<li class=\"package-version\"|</ul>)",
                Pattern.DOTALL).matcher(html);
        while (ver.find()) {
            String block = ver.group(1);
            VersionInfo v = new VersionInfo();
            Matcher hdr = Pattern.compile("Version ([^<]+)</b> \\((\\d+)\\)").matcher(block);
            if (!hdr.find()) continue;
            v.label = hdr.group(1).trim();
            v.versionCode = Long.parseLong(hdr.group(2));
            v.url = "https://f-droid.org/repo/" + pkg + "_" + hdr.group(2) + ".apk";
            v.sourceTag = FDROID;
            Matcher added = Pattern.compile("Added on ([^<]+)<").matcher(block);
            if (added.find()) {
                try {
                    java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.US);
                    v.date = f.parse(added.group(1).trim()).getTime();
                } catch (Exception ignored) {
                }
            }
            StringBuilder abis = new StringBuilder();
            Matcher abi = Pattern.compile("package-nativecode\">([^<]+)</code>").matcher(block);
            while (abi.find()) {
                if (abis.length() > 0) abis.append(", ");
                abis.append(abi.group(1).trim());
            }
            if (abis.length() > 0) v.abi = abis.toString();
            int pc = 0;
            Matcher perms = Pattern.compile("class=\"permission-label\"[^>]*>").matcher(block);
            while (perms.find()) {
                pc++;
                Matcher lbl = Pattern.compile("class=\"permission-label\"[^>]*>\\s*([^<]+)").matcher(block);
                // etiketleri tek tek topla
            }
            v.permCount = countMatches(block, "class=\"permission-label\"");
            if (v.permCount > 0) {
                Matcher pl = Pattern.compile("class=\"permission-label\"[^>]*>\\s*([^<]+)", Pattern.DOTALL).matcher(block);
                while (pl.find()) v.permissions.add(Html.strip(pl.group(1)));
            }
            d.versions.add(v);
        }
        // whats-new (en yeni sürüm için)
        Matcher wn = Pattern.compile("new-in-version[^>]*>([^<]+)</div>\\s*<div[^>]*>(.*?)</div>", Pattern.DOTALL).matcher(html);
        if (wn.find() && !d.versions.isEmpty()) {
            d.versions.get(0).whatsNew = Html.strip(wn.group(2));
        }
        // Son güncelleme (en yeni sürüm tarihi)
        if (!d.versions.isEmpty() && d.versions.get(0).date > 0) {
            d.lastUpdated = Ui.date(d.versions.get(0).date);
        }
        return d;
    }

    private static int countMatches(String s, String needle) {
        int c = 0, i = 0;
        while ((i = s.indexOf(needle, i)) >= 0) {
            c++;
            i += needle.length();
        }
        return c;
    }

    private static String linkText(String html, String id) {
        Matcher m = Pattern.compile("id=\"" + id + "\">\\s*<a[^>]*>(.*?)</a>", Pattern.DOTALL).matcher(html);
        return m.find() ? Html.strip(m.group(1)) : null;
    }

    private static String linkHref(String html, String id) {
        Matcher m = Pattern.compile("id=\"" + id + "\">\\s*<a href=\"([^\"]+)\"", Pattern.DOTALL).matcher(html);
        return m.find() ? m.group(1) : null;
    }

    /** F-Droid API: sürüm listesi (versionName → versionCode). */
    public static Map<String, Long> fdroidVersions(String pkg) throws Exception {
        JSONObject o = new JSONObject(Net.get("https://f-droid.org/api/v1/packages/" + pkg));
        JSONArray pkgs = o.optJSONArray("packages");
        Map<String, Long> best = new LinkedHashMap<>();
        if (pkgs == null) return best;
        for (int i = 0; i < pkgs.length(); i++) {
            JSONObject p = pkgs.getJSONObject(i);
            String vn = p.optString("versionName");
            long vc = p.optLong("versionCode", -1);
            if (vn == null || vn.isEmpty() || vc < 0) continue;
            Long old = best.get(vn);
            if (old == null || vc > old) best.put(vn, vc);
        }
        return best;
    }

    // ============================ HTML yardımcıları ============================

    public static final class Html {
        private Html() {
        }

        public static String strip(String s) {
            if (s == null) return "";
            String r = s.replaceAll("<[^>]+>", "");
            r = r.replace("&amp;", "&")
                    .replace("&lt;", "<").replace("&gt;", ">")
                    .replace("&quot;", "\"")
                    .replace("&#39;", "'")
                    .replace("&nbsp;", " ");
            r = r.trim();
            return r.replaceAll("\\s+", " ");
        }
    }
}
