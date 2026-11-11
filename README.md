# EskiDroid

Eski Android sürümlerinde yazılmış oyun ve uygulamaları (APK) yeni Android cihazlara kurulabilir hale getiren araç.

- **Arama ve indirme**: F-Droid arşivi (eski sürümler dahil) ve Internet Archive (archive.org) APK koleksiyonu üzerinden arama, sürüm listeleme ve indirme; sonuç kartlarında uygulama ikonları.
- **Depo (kütüphane)**: Her indirme kalıcı olarak saklanır (`original.apk` + `patched.apk` + `meta.json`); aynı sürüm bir daha asla indirilmez, kesilen indirme kaldığı yerden devam eder (`.part` + HTTP Range), SHA-256 doğrulaması, Yamalanmış/Yamasız filtreleri, Kur/Yamala/Paylaş/Sil eylemleri, çevrimdışı çalışır.
- **Uyumluluk modu**: Yama hedefi artık sabit 28 değil; cihaza göre en düşük gerekli `targetSdkVersion` seçilir (Android 15+ → 24, Android 14 → 23, altı → önce yamasız deneme). "Yüksek (28)" seçeneği eski davranışı korur. Hedef SDK asla düşürülmez, gereksiz yama yapılmaz; yamalı ve yamasız kurulum ayrı ayrı sunulur.
- **Kurulum sonrası yardımcılar**: ABI uyumsuzluk uyarısı (lib/ vs cihaz mimarisi), "İzinleri aç" kısayolu, "Açılışta çöktü mü?" ile diğer modla yeniden kurulum.
- **Kendinden imzalı yeniden imzalama**: Yamalanan APK'ları kalıcı RSA anahtarıyla imzalar.
- **IPA ikon çıkarma**: iOS `.ipa` dosyalarının içindeki uygulama ikonlarını çıkarır.

## Kurulum (Android — uygulama, bilgisayarsız)

**APK indirme: [EskiDroid-v1.2.apk](releases/EskiDroid-v1.2.apk)** (Android 7+; SHA-256: `f53ee6311fe6abeb9ffdb470d77afaf9bee841d0e3cba2516869276f879dedad`)

Önceki sürümler: [v1.1](releases/EskiDroid-v1.1.apk) (SHA-256: `af19f113e163ac098729a54563f57b1f4d0dfb377c73abd4915aa8e057210b3a`), [v1.0](releases/EskiDroid-v1.0.apk)

1. APK'yı indirin ve açın; "Bilinmeyen kaynaklardan uygulama yüklemeye izin verin".
2. Uygulamayı açın, listeden bir arama yapın (F-Droid + archive.org sonuçları gelir).
3. Sürüm seçin → indirin → kurulumu onaylayın. Eski APK'lar otomatik olarak yamanır ve imzalanır.

Not: Uygulama kurulumu `PackageInstaller` ile yapar; cihazda Android 7 (API 24) veya üstü gerekir.

### Arayüz

v1.2'de arayüz tamamen yeniden çizildi: [DESIGN.md](DESIGN.md) dosyasında tanımlı Fey tasarım sistemi (koyu tuval, kartlar, 99dp hap butonlar, monoline ikonlar) uygulanır; tüm ekranlar Java koduyla çizilir (XML yok). Alt menü: Ara · Depo · Ayarlar. Depo, indirdiğiniz her şeyi kalıcı olarak saklar ve çevrimdışı çalışır.

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
- Yamalama `targetSdkVersion` değerini değiştirir; davranış değişikliklerine (görünüm, izin istemleri) neden olabilir. "Uyumluluk modu: Otomatik" bu etkiyi en aza indirir; sorun yaşarsanız "Yüksek" veya yamasız kurulumu deneyin.

## Katkı ve lisans

MIT — bakınız [LICENSE](LICENSE).
