#!/usr/bin/env bash
# Reproducible fallback when Gradle repositories are unavailable. Requires Kotlin 2.0.21, JDK 17, Android SDK 35.
set -euo pipefail
cd "$(dirname "$0")"
: "${ANDROID_SDK_ROOT:?Set ANDROID_SDK_ROOT}"
: "${KOTLIN_HOME:?Set KOTLIN_HOME to the compiler directory}"
BUILD_TOOLS="$ANDROID_SDK_ROOT/build-tools/35.0.0"
PLATFORM_JAR="$ANDROID_SDK_ROOT/platforms/android-35/android.jar"
mkdir -p build/manual/{dex,gen}
export ANDROID_APPLICATION_ID="${ANDROID_APPLICATION_ID:-com.hdrg.camera}"
python3 - <<'PY'
import os,xml.etree.ElementTree as ET
ET.register_namespace('android','http://schemas.android.com/apk/res/android')
a='{http://schemas.android.com/apk/res/android}'
t=ET.parse('app/src/main/AndroidManifest.xml');r=t.getroot()
r.set('package',os.environ['ANDROID_APPLICATION_ID']);r.set(a+'versionCode','8');r.set(a+'versionName','0.6.2-rc3')
ET.SubElement(r,'uses-sdk',{a+'minSdkVersion':'26',a+'targetSdkVersion':'35'})
t.write('build/manual/AndroidManifest.xml',encoding='utf-8',xml_declaration=True)
PY
"$BUILD_TOOLS/aapt2" compile --dir app/src/main/res -o build/manual/resources.zip
"$BUILD_TOOLS/aapt2" link -o build/manual/unsigned.apk -I "$PLATFORM_JAR" --manifest build/manual/AndroidManifest.xml --java build/manual/gen --custom-package com.hdrg.camera build/manual/resources.zip
"$KOTLIN_HOME/bin/kotlinc" app/src/main/java/com/hdrg/camera/*.kt -classpath "$PLATFORM_JAR" -jvm-target 17 -d build/manual/classes.jar
"$BUILD_TOOLS/d8" --lib "$PLATFORM_JAR" --min-api 26 --output build/manual/dex build/manual/classes.jar "$KOTLIN_HOME/lib/kotlin-stdlib.jar" "$KOTLIN_HOME/lib/annotations-13.0.jar"
(cd build/manual/dex && zip -q ../unsigned.apk classes*.dex)
"$BUILD_TOOLS/zipalign" -f 4 build/manual/unsigned.apk build/manual/HDRG-Cam-v0.6.2-unsigned.apk
