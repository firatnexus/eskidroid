# EskiDroid

Eski Android sürümlerinde yazılmış oyun ve uygulamaları (APK) yeni Android cihazlara kurulabilir hale getiren araç.

- **Arama ve indirme**: F-Droid arşivi (eski sürümler dahil) ve Internet Archive (archive.org) APK koleksiyonu üzerinden arama, sürüm listeleme ve indirme.
- **Otomatik yama**: Hedef APK'nın `targetSdkVersion` değerini (2.2–4.4'te yaygın olan 8–25) modern kurulum için 28'e yükseltir; Android 11+ cihazlarda "Bu uygulama bu Android sürümüyle uyumlu değil" hatasını giderir.
- **Kendinden imzalı yeniden imzalama**: Yamalanan APK'ları bir seferlik RSA anahtarıyla imzalar.
- **IPA ikon çıkarma**: iOS `.ipa` dosyalarının içindeki uygulama ikonlarını çıkarır.

## Kurulum (Android — uygulama, bilgisayarsız)

**APK indirme: [EskiDroid-v1.1.apk](releases/EskiDroid-v1.1.apk)** (Android 7+; SHA-256: `af19f113e163ac098729a54563f57b1f4d0dfb377c73abd4915aa8e057210b3a`)

1. APK'yı indirin ve açın; "Bilinmeyen kaynaklardan uygulama yüklemeye izin verin".
2. Uygulamayı açın, listeden bir arama yapın (F-Droid + archive.org sonuçları gelir).
3. Sürüm seçin → indirin → kurulumu onaylayın. Eski APK'lar otomatik olarak yamanır ve imzalanır.

Not: Uygulama kurulumu `PackageInstaller` ile yapar; cihazda Android 7 (API 24) veya üstü gerekir.

**APK nasıl derlendi?** Kaynak kod `android-app/` klasöründedir; derleme [GitHub Actions](.github/workflows/build-apk.yml) ile sunucuda yapılır (derleme betiği: `android-app/build.sh`). GitHub'ın sunucusunda [en son derleme](https://github.com/firatnexus/eskidroid/actions/workflows/build-apk.yml) başarılıysa oradan da indirilebilir.

## Kurulum (Termux — PC olmadan komut satırı)

Ayrıntılı rehber: [TERMUX.md](android-app/TERMUX.md) (konsoldan çalıştırma).

## CLI kullanımı

```bash
python3 eskidroid.py search "2048"        # F-Droid'te ara
python3 eskidroid.py aversiyon <paket-adı>  # tüm sürümleri listele
python3 eskidroid.py adownload <arşiv-url> # archive.org'dan indir + yamala + imzala
python3 eskidroid.py fix <dosya.apk>      # yerel APK'yı yamala ve imzala
```

`tools/` klasöründe `apktool.jar` ve `apksigner.jar` bulunmalı.

## Bilinen sınırlar

- IPA → APK dönüşümü teknik olarak mümkün değildir; yalnızca ikon çıkarımı desteklenir.
- 2011 öncesi, bozuk kaynak XML'li bazı arşiv APK'ları açılamaz (hata mesajı görünür).
- Yamalama `targetSdkVersion` değerini değiştirir; davranış değişikliklerine (görünüm, izin istemleri) neden olabilir.

## Katkı ve lisans

MIT — bakınız [LICENSE](LICENSE).
