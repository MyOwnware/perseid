#!/usr/bin/env bash
set -euo pipefail

./gradlew assembleRelease -PperseidArm64=true --warning-mode fail
apk=app/build/outputs/apk/release/app-release-unsigned.apk
tools="${ANDROID_HOME:?Android SDK is required}/build-tools/36.0.0"
badging=$("$tools/aapt" dump badging "$apk")
grep -Fq "versionName='0.0.0-dev'" <<<"$badging"
grep -Fxq "native-code: 'arm64-v8a'" <<<"$badging"
"$tools/zipalign" -c -P 16 4 "$apk"
printf '%s\n' 'Verified unsigned arm64 development APK.'
