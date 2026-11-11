# EskiDroid'i Termux'ta (telefondan, bilgisayarsız) kullanma

Termux, Android'de çalışan bir Linux terminalidir; EskiDroid CLI'yi telefonda çalıştırmanızı sağlar.

## 1. Termux'u kurun

Google Play'deki sürüm eski kalmıştır; **F-Droid'den** Termux'u kurun:
- F-Droid: https://f-droid.org/tr/packages/com.termux/ ("APK'yı indir" düğmesi)

(F-Droid uygulaması kuruluysa oradan da yüklenebilir.)

## 2. Temel paketleri kurun

Termux'u açın ve şunu yazın:

```bash
pkg update
pkg install python git openjdk-17
```

`openjdk-17`, Java 17 sağlar; EskiDroid'in APK yamalama araçları (apktool, apksigner) Java ile çalışır.

## 3. Depoyu klonlayın

```bash
git clone https://github.com/firatnexus/eskidroid.git
cd eskidroid/eskidroid
```

## 4. Kullanın

```bash
python3 eskidroid.py search "2048"        # F-Droid arşivinde arama
python3 eskidroid.py aversiyon <paket-adı>  # paketin tüm sürümleri
python3 eskidroid.py adownload <arşiv-sayfa-url>  # archive.org'dan indir + yamala + imzala
python3 eskidroid.py fix <dosya.apk>      # telefondaki bir APK'yı yamala
```

İndirilen/yamalanan APK'lar Termux ana dizininde (`~/`) oluşturulur; "Files" uygulamasıyla veya `termux-setup-storage` sonrası `~/storage/downloads`'a kopyalayıp Android'den kurabilirsiniz.

## İpuçları

- Depoyu güncellemek için: `git pull`
- `java -version` ile Java'nın 17+ olduğunu kontrol edin.
- Eski APK'ların çoğu, yamalama sonrası Android 11+ cihazlara kurulur; ancak çok eski (2010 öncesi) bazı dosyalar açılamaz — bu bir dosya formatı sorunudur, sorun değildir.

## Alternatif: Android uygulaması

Komut satırıyla uğraşmak istemiyorsanız [EskiDroid.apk](../releases/EskiDroid-v1.0.apk) uygulamasını kullanın; arama, indirme, yamalama ve kurulum tek uygulamada.
