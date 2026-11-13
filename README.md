# EskiDroid

Eski Android sürümlerinde yazılmış oyun ve uygulamaları (APK) yeni Android cihazlara kurulabilir hale getiren araç.

- **Arama ve indirme**: Önce kaynak seçilir (F-Droid / Internet Archive / GitHub); arama sonuçları yalnız seçilen kaynaktan gelir. Sürüm listeleme, indirme ve uygulama ikonları dahil.
- **Depo (kütüphane)**: Her indirme kalıcı olarak saklanır (`original.apk` + `patched.apk` + `meta.json`); aynı sürüm bir daha asla indirilmez, kesilen indirme kaldığı yerden devam eder (`.part` + HTTP Range), SHA-256 doğrulaması, Yamalanmış/Yamasız filtreleri, Kur/Yamala/Paylaş/Sil eylemleri, çevrimdışı çalışır. **Cihazdaki APK dosyaları da "Cihazdan APK ekle" ile Depo'ya alınabilir** (yamalanıp kurulabilir).
- **Uyumluluk modu**: Yama hedefi artık sabit 28 değil; cihaza göre en düşük gerekli `targetSdkVersion` seçilir (Android 15+ → 24, Android 14 → 23, altı → önce yamasız deneme). "Yüksek (28)" seçeneği eski davranışı korur. Hedef SDK asla düşürülmez, gereksiz yama yapılmaz; yamalı ve yamasız kurulum ayrı ayrı sunulur.
- **Mağaza arayüzü (v1.5)**: App Store havası — beyaz/karanlık zemin, pil butonlar, büyük başlıklar, kenardan kenara alt sekme çubuğu; detay sayfasında ekran görüntüleri şeridi ve Play Store tarzı tam ekran görüntüleyici (sayfalama + yakınlaştırma).
- **Çökme önleme (v1.5)**: Yamalanan APK'lara `extractNativeLibs=true`, `org.apache.http.legacy` kütüphanesi ve `usesCleartextTraffic=true` eklenir; yeni nesil cihazlarda açılış sonrası çökme büyük ölçüde giderilir. Eski yamalı kopyalar otomatik yeniden yamalanır.
- **Güvenlik (v1.5)**: İndirmeler yalnız HTTPS; kurulum sağlayıcısı yalnız kendi hazırladığı `install-*.apk` dosyalarını sunar (yol geçişi kapalı); `allowBackup=false`; GitHub Actions işleri en az gerekli izinle çalışır.
- **Kurulum sonrası yardımcılar**: ABI uyumsuzluk uyarısı (lib/ vs cihaz mimarisi), "İzinleri aç" kısayolu, "Açılışta çöktü mü?" ile diğer modla yeniden kurulum.
- **Kendinden imzalı yeniden imzalama**: Yamalanan APK'ları kalıcı RSA anahtarıyla imzalar.
- **IPA ikon çıkarma**: iOS `.ipa` dosyalarının içindeki uygulama ikonlarını çıkarır.

## Kurulum (Android — uygulama, bilgisayarsız)

**APK indirme: [EskiDroid-v1.5.apk](releases/EskiDroid-v1.5.apk)** (Android 7+; SHA-256: `23fd981a941b2c8d50a11137ba5c8ae0ad95a40ff32d6d3be781ccb4ca85f720`)

v1.5: App Store tarzı mağaza arayüzü; aramada önce kaynak seçimi (F-Droid / Web Arşivi / GitHub — yalnız seçilen kaynağın sonuçları); yamalı uygulamaların açılış sonrası çökmesini önleyen yama iyileştirmeleri; cihazdan APK içe aktarma; Play Store tarzı tam ekran ekran görüntüsü görüntüleyicisi; güvenlik sağlamlaştırma (HTTPS zorunlu, sağlayıcı kısıtı, allowBackup kapatma, CI izinleri).

Önceki sürümler: [v1.4](releases/EskiDroid-v1.4.apk) (SHA-256: `ff02f985a666868d539d4db8ea4639111d9d9c7447e9314eb7cf197d99461e9f`), [v1.3](releases/EskiDroid-v1.3.apk) (SHA-256: `ba784a36f06166cb7244c55d0a6346c96f44cffa6162d608b4ce58275ee223c6`)

v1.3: `ACCESS_NETWORK_STATE` izni eklendi ("internet bağlantısı yok" hatası giderildi), `versionCode`/`versionName` manifest köküne taşındı ("Sürüm: null" hatası giderildi), bağlantı durumu belirlenemezse arama artık engellenmez.

Diğer önceki sürümler: [v1.2](releases/EskiDroid-v1.2.apk) (SHA-256: `f53ee6311fe6abeb9ffdb470d77afaf9bee841d0e3cba2516869276f879dedad`), [v1.1](releases/EskiDroid-v1.1.apk) (SHA-256: `af19f113e163ac098729a54563f57b1f4d0dfb377c73abd4915aa8e057210b3a`), [v1.0](releases/EskiDroid-v1.0.apk)

1. APK'yı indirin ve açın; "Bilinmeyen kaynaklardan uygulama yüklemeye izin verin".
2. Uygulamayı açın, bir kaynak seçin (F-Droid / Web Arşivi / GitHub) ve arama yapın.
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
