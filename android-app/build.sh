#!/usr/bin/env bash
# EskiDroid APK derleme betigi (GitHub Actions ve yerel kullanim).
# Gereksinim: JDK 17+, Android SDK (build-tools 34+, platform android-34+), curl, zip
# Ortam degiskenleri:
#   ANDROID_HOME      SDK konumu
#   KEYSTORE, KS_PASS, KEY_ALIAS   imzalama icin (yoksa gecici anahtar uretilir)
set -euo pipefail

cd "$(dirname "$0")"
OUT="${OUT:-$PWD/out}"
rm -rf "$OUT" && mkdir -p "$OUT"

: "${ANDROID_HOME:?ANDROID_HOME gerekli}"
BT="$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)"
PLATFORM="$(ls -d "$ANDROID_HOME"/platforms/android-* | sort -V | tail -1)"
ANDROID_JAR="$PLATFORM/android.jar"
echo "build-tools: $BT"
echo "platform:    $PLATFORM"

ARSC_VER=1.4.0
APKSIG_VER=8.5.0
mkdir -p "$OUT/libs"
[ -f "$OUT/libs/arsclib.jar" ] || curl -fsSL -o "$OUT/libs/arsclib.jar" \
  "https://repo1.maven.org/maven2/io/github/reandroid/ARSCLib/$ARSC_VER/ARSCLib-$ARSC_VER.jar"
[ -f "$OUT/libs/apksig.jar" ] || curl -fsSL -o "$OUT/libs/apksig.jar" \
  "https://dl.google.com/android/maven2/com/android/tools/build/apksig/$APKSIG_VER/apksig-$APKSIG_VER.jar"

# 1) kaynaklar
"$BT/aapt2" compile --dir res -o "$OUT/compiled.zip"
mkdir -p "$OUT/gen"
"$BT/aapt2" link -o "$OUT/app.base.apk" "$OUT/compiled.zip" \
  --manifest AndroidManifest.xml -I "$ANDROID_JAR" --java "$OUT/gen" --auto-add-overlay

# 2) java
mkdir -p "$OUT/obj"
find src "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac --release 11 -encoding UTF-8 -classpath "$ANDROID_JAR:$OUT/libs/arsclib.jar:$OUT/libs/apksig.jar" \
  -d "$OUT/obj" @"$OUT/sources.txt"

# 3) dex
mkdir -p "$OUT/dex"
"$BT/d8" --release --lib "$ANDROID_JAR" --min-api 21 --output "$OUT/dex" \
  $(find "$OUT/obj" -name '*.class') "$OUT/libs/arsclib.jar" "$OUT/libs/apksig.jar"

# 4) paketle + hizala
cp "$OUT/app.base.apk" "$OUT/unaligned.apk"
(cd "$OUT/dex" && zip -q ../unaligned.apk classes*.dex)
"$BT/zipalign" -f 4 "$OUT/unaligned.apk" "$OUT/aligned.apk"

# 5) imzala
if [ -z "${KEYSTORE:-}" ]; then
  echo "KEYSTORE yok: gecici (CI-only) anahtar uretiliyor"
  KEYSTORE="$OUT/tmp.keystore"; KS_PASS=tmp-pass; KEY_ALIAS=tmp
  keytool -genkeypair -keystore "$KEYSTORE" -alias "$KEY_ALIAS" -keyalg RSA -keysize 2048 \
    -validity 10950 -storepass "$KS_PASS" -keypass "$KS_PASS" -dname "CN=EskiDroid CI" >/dev/null
fi
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
  --ks-key-alias "$KEY_ALIAS" --out "$OUT/EskiDroid.apk" "$OUT/aligned.apk"
"$BT/apksigner" verify --verbose "$OUT/EskiDroid.apk" | head -5
ls -la "$OUT/EskiDroid.apk"
