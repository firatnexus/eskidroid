# EskiDroid — Eski Android uygulamalarını modern cihazlarda çalıştır

Eski sürümlü Android oyun ve uygulamalarını arşivden indirip, modern Android'in
kurulum engelini (target SDK kısıtı) aşarak kurulabilir hale getiren Python CLI aracı.

## Ne yapar

1. **`search <kelime>`** — F-Droid arşivinde uygulama arar (yüzlerce açık kaynak uygulamanın tüm eski sürümleri).
2. **`download <paket> --version 1.2`** — seçilen eski sürümü indirir; `--fix` verirsen eski ise otomatik düzeltir.
3. **`asearch <kelime>`** — Archive.org arşivlerinde APK arar: [APK Archive koleksiyonu](https://archive.org/details/apkarchive) (135.000+ eski sürüm), "Android Apps" koleksiyonu ve tüm arşiv genelinde. `--collection apkarchive|android|general` ile kapsamı daraltabilirsiniz.
4. **`aversiyon <identifier>`** — Archive.org öğesindeki .apk dosyalarını listeler.
5. **`adownload <identifier> [--file X.apk] [--fix]`** — Archive.org öğesinden APK indirir; `--fix` verirsen eski ise hemen düzeltir.
6. **`fix <app.apk>`** — APK'nın hedef SDK'sını yükseltir ve debug sertifikasıyla yeniden imzalar.
7. **`ipa-icons <file.ipa>`** — IPA içindeki ikon/görselleri çıkarır (APK dönüşümü DEĞİL).

## Gereksinimler

- Python 3.8+
- Java 8+ (`java -jar` çalışmalı)
- `tools/` içinde `apktool.jar` (v2.9.3) ve `apksigner.jar` (uber-apk-signer 1.3.0) — bu repoda hazır.

## Kurulum

```bash
git clone https://github.com/firatnexus/eskidroid.git
cd eskidroid
```

## Kullanım örnekleri

```bash
# Arşivde ara
python3 eskidroid/eskidroid.py search calendar

# Eski sürümü indir ve otomatik düzelt
python3 eskidroid/eskidroid.py download ch.ihdg.calendarcolor --version 0.4 --fix

# Elindeki eski APK'yı düzelt
python3 eskidroid/eskidroid.py fix OyunEski.apk
```

## Nasıl çalışır

Modern Android (14+), `targetSdk < 23` olan uygulamaların kurulumunu engeller.
`fix` komutu APK'yı `apktool` ile çözer, manifest'in `targetSdkVersion` değerini 28'e
yükseltir, APK'yı yeniden derler ve `uber-apk-signer` ile debug sertifikasıyla
imzalar. Sonuçta dosya eski uygulamayı, modern kurulum kısıtlamalarını geçecek
şekilde kurabilirsiniz. Debug imzası kalıcıdır — Google Play güncellemeleri
aynı imza gerektirdiği için bu APK'lar artık Play Store'dan güncellenemez; bu
bilinçli bir kısıttır (orijinal imza anahtarı sadece Google Play'de bulunur).

## IPA -> APK dönüşümü NEDEN yok?

iOS IPA dosyaları, Apple'ın ARM işlemcilerine derlenmiş makine kodu içerir; aynı
binary Android'de çalışamaz. Bunu "çeviren" iddia eden araçlar kısıtlamalarla
(dolgu/hile) çalışır. EskiDroid dürüst bir araç olarak bunu yapmaz; bunun yerine
IPA içinden görselleri kurtarmak için `ipa-icons` komutu var.

## Kaynaklar

- [apktool](https://github.com/iBotPeaches/Apktool) (Apache 2.0)
- [uber-apk-signer](https://github.com/patrickfav/uber-apk-signer) (Apache 2.0)
- [F-Droid](https://f-droid.org) (açık kaynak arşiv)

## Lisans

Bu araç MIT lisansıyla yayımlanır. APK'lar üzerinde yaptığınız değişikliklerin
yasal durumu, kaynağın lisansına bağlıdır; yalnızca hakkınız olan dosyaları kullanın.
