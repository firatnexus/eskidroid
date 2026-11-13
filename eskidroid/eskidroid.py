#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
EskiDroid - eski Android uygulamalarını modern cihazlarda kurulabilir yapan araç.

İki özellik:
  1) search: F-Droid arşivinde bir uygulamanın eski sürümlerini arar
  2) download: seçilen sürümü indirir
  3) fix: APK'nın hedef SDK'sını yükseltip yeniden imzalar,
     böylece Android 14+ bunu "eski" diye kurulumu engellemez.

Gereksinimler: java (8+), apktool.jar, uber-apk-signer.jar (bu repoda tools/ altında).
IPA -> APK dönüşümü YOKTUR; nedeni README'de anlatılıyor.
"""

import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tempfile
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

INDEX_URL = "https://f-droid.org/repo/index-v1.json"
APK_BASE = "https://f-droid.org/repo/"
TOOLS_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "tools")
MIN_TARGET_SDK = 23  # Android 14 (targetSdk < 23) kurulumu engeller; 23-28 güvenli aralık
SAFE_TARGET_SDK = 28


def find_java():
    j = shutil.which("java")
    if not j:
        sys.exit("Hata: 'java' bulunamadı. OpenJDK kurun (ör: apt install default-jre-headless).")
    return j


def run(cmd, **kw):
    r = subprocess.run(cmd, capture_output=True, text=True, **kw)
    if r.returncode != 0:
        sys.exit("Komut başarısız (" + " ".join(cmd) + "):\n" + (r.stderr or r.stdout)[-2000:])
    return r.stdout


# ---------------------------------------------------------------- F-Droid

_index_cache = None

def check_https(url):
    """Güvenlik: indirme yalnızca HTTPS üzerinden yapılır."""
    if not url.startswith("https://"):
        sys.exit("Reddedildi (HTTPS değil): " + url)


def load_index():
    global _index_cache
    if _index_cache is None:
        cache = os.path.join(tempfile.gettempdir(), "eskidroid-fdroid-index-v1.json")
        if not os.path.exists(cache):
            print("F-Droid dizini indiriliyor (bir kere, ~10-20 MB)...")
            check_https(INDEX_URL)
            urllib.request.urlretrieve(INDEX_URL, cache)
            # /tmp önbelleğini yalnızca sahibi okuyabilir (diğer kullanıcılar hayır)
            os.chmod(cache, 0o600)
        with open(cache, encoding="utf-8") as fh:
            _index_cache = json.load(fh)
    return _index_cache


def cmd_search(args):
    q = args.query.lower()
    index = load_index()
    hits = []
    for app in index["apps"]:
        name = (app.get("localized", {}).get("en-US", {}) or {}).get("name") or app.get("packageName", "")
        if q in name.lower() or q in app.get("packageName", "").lower():
            hits.append(app)
    if not hits:
        print("Sonuç bulunamadı:", args.query)
        return
    print("Bulunan uygulamalar (eski sürümleri indirmek için paket adını kopyalayın):\n")
    for app in hits[:20]:
        name = (app.get("localized", {}).get("en-US", {}) or {}).get("name") or ""
        print("  %-38s %s" % (app.get("packageName", ""), name))
        print("      sürümler: " + ", ".join(
            str(v.get("versionName")) for v in index["packages"].get(app.get("packageName", ""), [])[:10]))


def cmd_download(args):
    index = load_index()
    pkg = args.package
    versions = index["packages"].get(pkg)
    if not versions:
        sys.exit("Paket bulunamadı: " + pkg + " (önce 'search' ile bulun).")
    chosen = None
    if args.version:
        for v in versions:
            if str(v.get("versionName")) == args.version:
                chosen = v
                break
        if not chosen:
            print("Sürüm bulunamadı. Mevcut sürümler:")
            for v in versions:
                print("  ", v.get("versionName"))
            return
    else:
        chosen = versions[-1]
        print("Sürüm belirtilmedi; en yeni sürüm indiriliyor:", chosen.get("versionName"))

    fname = "%s_%s.apk" % (pkg, chosen.get("versionCode"))
    url = APK_BASE + fname
    check_https(url)
    print("İndiriliyor:", url)
    urllib.request.urlretrieve(url, fname)
    digest = hashlib.sha256(open(fname, "rb").read()).hexdigest()
    print("İndi: %s (%.1f KB)  sha256=%s" % (fname, os.path.getsize(fname) / 1024, digest[:16] + "..."))
    if args.fix:
        cmd_fix_arg = argparse.Namespace(apk=fname, output=None, target_sdk=None, keep_temp=False)
        cmd_fix(cmd_fix_arg)


# ---------------------------------------------------------------- APK fix

def read_sdkinfo(apk_path):
    """apktool ile decode edip sdkInfo'yu okur; araç yoksa binary manifest'ten dener."""
    tmp = tempfile.mkdtemp(prefix="eskidroid-")
    run([find_java(), "-jar", os.path.join(TOOLS_DIR, "apktool.jar"), "d", "-f", "-o",
         os.path.join(tmp, "d"), apk_path])
    yml = os.path.join(tmp, "d", "apktool.yml")
    text = open(yml, encoding="utf-8").read()
    min_sdk = target = None
    for line in text.splitlines():
        line = line.strip()
        if line.startswith("minSdkVersion:"):
            min_sdk = line.split(":", 1)[1].strip()
        elif line.startswith("targetSdkVersion:"):
            target = line.split(":", 1)[1].strip()
    return tmp, os.path.join(tmp, "d"), min_sdk, target


# ---------------------------------------------------------------- Archive.org

IA_SEARCH = "https://archive.org/advancedsearch.php?q=%s&rows=%d&output=json&fl[]=identifier&fl[]=title&fl[]=publicdate"
IA_METADATA = "https://archive.org/metadata/%s"

def ia_get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "eskidroid/1.0"})
    return json.load(urllib.request.urlopen(req, timeout=30))

def ia_item(identifier):
    return ia_get(IA_METADATA % urllib.parse.quote(identifier))

def cmd_asearch(args):
    # Her iki ana koleksiyonda ara
    searches = [
        ("APK Archive koleksiyonu", 'collection:("apkarchive") AND title:(%s)'),
        ("Android Apps koleksiyonu", 'collection:("Android Apps") AND title:(%s)'),
        ("Tüm Archive.org (APK formatında)", 'format:("Android Package Archive") AND (title:(%s) OR identifier:(%s))'),
    ]
    q = args.query.strip()
    total_shown = 0
    for desc, pattern in searches:
        if args.collection == "apkarchive" and not desc.startswith("APK Archive"):
            continue
        if args.collection == "android" and not desc.startswith("Android Apps"):
            continue
        if args.collection == "general" and not desc.startswith("Tüm"):
            continue
        query = pattern % (q, q) if pattern.count("%s") > 1 else pattern % q
        url = IA_SEARCH % (urllib.parse.quote(query), args.rows)
        try:
            d = ia_get(url)
        except Exception as e:
            print("Arşiv erişimi başarısız (%s): %s" % (desc, e))
            continue
        n = d["response"]["numFound"]
        print("== %s (toplam %d)" % (desc, n))
        for doc in d["response"]["docs"][:args.rows]:
            print("  %-55s %s" % (doc.get("identifier", ""), str(doc.get("title", ""))[:50]))
            total_shown += 1
    print("\nİndirmek için: eskidroid adownload <identifier>")
    print("Sürüm listesi için: eskidroid aversiyon <identifier>")


def ia_list_apks(identifier):
    """Archive.org öğesinin .apk dosyalarını listeler."""
    d = ia_item(identifier)
    return [(f["name"], int(f.get("size", 0) or 0)) for f in d.get("files", []) if f.get("name", "").endswith(".apk")]

def cmd_aversiyon(args):
    apks = ia_list_apks(args.identifier)
    if not apks:
        sys.exit("APK bulunamadı: %s" % args.identifier)
    for name, size in apks:
        print("  %-60s %d KB" % (name, size // 1024))


def cmd_adownload(args):
    apks = ia_list_apks(args.identifier)
    if not apks:
        sys.exit("APK bulunamadı: %s" % args.identifier)
    name = args.file or apks[0][0]
    if name not in [a[0] for a in apks]:
        sys.exit("Dosya listede yok: %s. 'aversiyon' ile listeyin." % name)
    url = "https://archive.org/download/%s/%s" % (urllib.parse.quote(args.identifier), urllib.parse.quote(name))
    check_https(url)
    out = os.path.basename(name)
    print("İndiriliyor:", url)
    urllib.request.urlretrieve(url, out)
    print("İndi:", out)
    if args.fix:
        cmd_fix_arg = argparse.Namespace(apk=out, output=None, target_sdk=None, keep_temp=False)
        cmd_fix(cmd_fix_arg)


# ---------------------------------------------------------------- APK fix

def cmd_fix(args):
    apk = args.apk
    if not os.path.exists(apk):
        sys.exit("Dosya yok: " + apk)
    if apk.lower().endswith(".ipa"):
        sys.exit("Bu bir iOS IPA dosyası. IPA -> APK dönüşümü mümkün değildir (README'ye bakın). "
                 "Eşdeğer Android sürümünü arşivden arayıp indirebilirsiniz.")

    print("APK çözülüyor:", apk)
    tmp, decoded, min_sdk, target = read_sdkinfo(apk)
    print("Mevcut: minSdk=%s targetSdk=%s" % (min_sdk, target))

    new_target = int(args.target_sdk or SAFE_TARGET_SDK)
    old_target = int(target) if target and target.isdigit() else None
    if old_target is not None and old_target >= MIN_TARGET_SDK and not args.target_sdk:
        print("Bu APK zaten modern (targetSdk >= 23); yine de yeniden imzalanabilir.")

    yml_path = os.path.join(decoded, "apktool.yml")
    text = open(yml_path, encoding="utf-8").read()
    if target and target.strip().isdigit():
        text = text.replace("targetSdkVersion: " + target, "targetSdkVersion: %d" % new_target)
        open(yml_path, "w", encoding="utf-8").write(text)
        print("targetSdk %s -> %d olarak değiştirildi." % (target, new_target))

    print("APK yeniden derleniyor...")
    fixed = os.path.join(tmp, "fixed.apk")
    run([find_java(), "-jar", os.path.join(TOOLS_DIR, "apktool.jar"), "b", decoded, "-o", fixed])

    print("İmzalanıyor (zipalign + debug imzası)...")
    run([find_java(), "-jar", os.path.join(TOOLS_DIR, "apksigner.jar"), "sign", "--apks", fixed])
    out_signed = os.path.join(tmp, "fixed-aligned-debugSigned.apk")
    if not os.path.exists(out_signed):
        # farklı isimlendirme varyantı
        cands = [f for f in os.listdir(tmp) if f.endswith("Signed.apk")]
        out_signed = os.path.join(tmp, cands[0]) if cands else fixed

    out = args.output or (os.path.splitext(apk)[0] + "-eskidroid.apk")
    shutil.copy(out_signed, out)
    print("Hazır:", out)
    if not args.keep_temp:
        shutil.rmtree(tmp, ignore_errors=True)
    print("\nKurulum: adb install -r " + out)
    print("veya telefona kopyalayıp yükleyin (bilinmeyen kaynaklara izin verin).")


def cmd_ipa_icons(args):
    """IPA içindeki ikon/simgeleri çıkarır (dönüşüm DEĞİL - görsel kurtarma)."""
    if not os.path.exists(args.ipa):
        sys.exit("Dosya yok: " + args.ipa)
    out = args.output or (os.path.splitext(args.ipa)[0] + "-icons")
    os.makedirs(out, exist_ok=True)
    found = 0
    with zipfile.ZipFile(args.ipa) as z:
        for n in z.namelist():
            base = os.path.basename(n)
            if base.lower().endswith((".png", ".jpg", ".jpeg", ".gif")):
                with open(os.path.join(out, base), "wb") as f:
                    f.write(z.read(n))
                found += 1
    print("%d görsel çıkarıldı: %s" % (found, out))
    if found == 0:
        print("Görsel bulunamadı; IPA bozuk olabilir.")


def main():
    p = argparse.ArgumentParser(prog="eskidroid",
                                 description="Eski Android uygulamalarını arşivden indirip modern cihazlarda kurulabilir yapar.")
    sub = p.add_subparsers(dest="cmd", required=True)

    s = sub.add_parser("search", help="F-Droid arşivinde uygulama ara")
    s.add_argument("query")
    s.set_defaults(func=cmd_search)

    d = sub.add_parser("download", help="Paketin seçilen sürümünü indir")
    d.add_argument("package", help="paket adı, örn. org.fdroid.fdroid")
    d.add_argument("--version", help="sürüm adı, örn. 1.23.2")
    d.add_argument("--fix", action="store_true", help="indirdikten sonra hemen eski ise düzelt")
    d.set_defaults(func=cmd_download)

    f = sub.add_parser("fix", help="APK'nın hedef SDK'sını yükselt ve yeniden imzala")
    f.add_argument("apk", help="APK dosyası")
    f.add_argument("-o", "--output", help="çıktı dosyası")
    f.add_argument("--target-sdk", type=int, help="yeni hedef SDK (varsayılan %d)" % SAFE_TARGET_SDK)
    f.add_argument("--keep-temp", action="store_true", help="geçici dosyaları silme")
    f.set_defaults(func=cmd_fix)

    a = sub.add_parser("asearch", help="Archive.org arşivlerinde eski APK ara")
    a.add_argument("query", help="anahtar kelime, örn. whatsapp")
    a.add_argument("--rows", type=int, default=10, help="sonuç sayısı (varsayılan 10)")
    a.add_argument("--collection", choices=["all", "apkarchive", "android", "general"], default="all",
                    help="arama kapsamı: all=üç kaynak, apkarchive, android, general=tüm arşiv")
    a.set_defaults(func=cmd_asearch)

    av = sub.add_parser("aversiyon", help="Archive.org öğesinin APK dosyalarını/sürümlerini listele")
    av.add_argument("identifier", help="örnek: lmc-8.8-beta-fix-snap.apk")
    av.set_defaults(func=cmd_aversiyon)

    ad = sub.add_parser("adownload", help="Archive.org öğesinden APK indir")
    ad.add_argument("identifier", help="Archive.org identifier")
    ad.add_argument("--file", help="öğe içindeki .apk dosya adı (birden fazlaysa gerekli)")
    ad.add_argument("--fix", action="store_true", help="indirdikten sonra eski ise düzelt")
    ad.set_defaults(func=cmd_adownload)

    i = sub.add_parser("ipa-icons", help="IPA içindeki ikon/görselleri çıkar (APK dönüşümü değil)")
    i.add_argument("ipa", help="IPA dosyası")
    i.add_argument("-o", "--output", help="çıktı klasörü")
    i.set_defaults(func=cmd_ipa_icons)

    args = p.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
