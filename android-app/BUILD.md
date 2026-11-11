# EskiDroid Android Uygulaması

Telefondan bilgisayarsız kullanım için: arama, indirme, eski APK düzeltme ve
kurulum tek uygulamada.

## Kurulum (hazır APK)

**Releases** sayfasındaki `EskiDroid-v1.0.apk` dosyasını indirip telefonuna kur:
[Releases](https://github.com/firatnexus/eskidroid/releases)

Gereksinim: Android 5.0+ (API 21). Kurulumdan sonra uygulamaya
"bilinmeyen kaynaklardan uygulama yükleme" iznini vermen gerekir.

## Ne yapar

1. **F-Droid arşivinde ara** — açık kaynak uygulamaların eski sürümleri
   (sürümler, kod ve hedef SDK listelenir).
2. **Archive.org'da ara** — APK Archive koleksiyonu (135.000+ eski APK).
3. Seçilen sürümü indirir; hedef SDK 23'ten düşükse (Android 14+ kurulum engeli)
   manifest'i 28'e yükseltip yeniden imzalar ve kurulumu başlatır.

## Teknik notlar

- Manifest düzenleme: [ARSCLib](https://github.com/REAndroid/ARSCLib) (Apache-2.0)
- İmzalama: [apksig](https://developer.android.com/tools/apksigner) (v1+v2+v3)
- İmza anahtarı uygulamaya gömülüdür; kişisel kullanım içindir. Bu imzayla
  imzalanan APK'lar Play Store güncellemeleriyle uyumlu olmaz.
- 2011 öncesi (Android 1.5-2.x) bazı APK'larda manifest biçimi okunamıyor;
  uygulama bunu net bir hata mesajıyla bildirir.
- Derleme (geliştirici): `aapt2` + `javac` + `d8` + `zipalign` + `apksigner`
  (build-tools 34, platform android-34).

## Kaynak derleme adımları

```bash
# 0) KeyMaterial.java.template dosyasini kopyalayip gercek anahtar degerleriyle doldurun (src/.../KeyMaterial.java). Anahtar uretimi:
```bash
keytool -genkeypair -keystore esk.keystore -alias eskidroid -keyalg RSA -keysize 2048 -validity 10950
openssl pkcs12 -in esk.keystore -nodes -nocerts -out key.pem   # sifre girin
openssl pkcs12 -in esk.keystore -nokeys -out cert.pem
openssl pkcs8 -topk8 -inform PEM -in key.pem -outform DER -out key.der -nocrypt
openssl x509 -in cert.pem -outform DER -out cert.der
# sonra base64 alip KeyMaterial.java icine koyun
```

1) kaynaklar
aapt2 compile --dir res -o compiled.zip
aapt2 link -o app.base.apk compiled.zip --manifest AndroidManifest.xml \
  -I android.jar --java gen --auto-add-overlay

# 2) java
javac --release 11 -classpath android.jar:arsclib.jar:apksig.jar -d obj \
  $(find src gen -name "*.java")

# 3) dex (multidex otomatik)
d8 --release --lib android.jar --min-api 21 --output dexout \
  $(find obj -name "*.class") arsclib.jar apksig.jar

# 4) paketle + hizala + imzala
zip app.base.apk dexout/classes.dex
zipalign -f 4 app.base.apk aligned.apk
apksigner sign --ks <keystore> --out EskiDroid.apk aligned.apk
```

## Anahtar uretimi (KeyMaterial.java icin)

```bash
keytool -genkeypair -keystore esk.keystore -alias eskidroid -keyalg RSA -keysize 2048 -validity 10950
openssl pkcs12 -in esk.keystore -nodes -nocerts -out key.pem
openssl pkcs12 -in esk.keystore -nokeys -out cert.pem
openssl pkcs8 -topk8 -inform PEM -in key.pem -outform DER -out key.der -nocrypt
openssl x509 -in cert.pem -outform DER -out cert.der
base64 -w0 key.der   # PRIVATE_KEY
base64 -w0 cert.der   # CERTIFICATE
```

Gizli anahtar (`key.pem`, `key.der`, keystore) asla repoya konmaz.
