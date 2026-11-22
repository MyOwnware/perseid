#!/usr/bin/env bash
set +x
set -euo pipefail
umask 077

tag=${1:?release tag is required}
unsigned=${2:?unsigned APK is required}
output=${3:?output directory is required}
build_tools="${ANDROID_HOME:?Android SDK is required}/build-tools/36.0.0"

fail() {
    printf '%s\n' "$1" >&2
    exit 1
}

[[ $tag =~ ^v(0|[1-9][0-9]{0,5})\.(0|[1-9][0-9]?)\.(0|[1-9][0-9]?)$ ]] ||
    fail 'Expected a stable release tag.'
version=${tag#v}
IFS=. read -r major minor patch <<<"$version"
code=$((major * 10000 + minor * 100 + patch))
((code >= 1 && code <= 2100000000)) || fail 'Release versionCode is out of range.'

badging=$("$build_tools/aapt" dump badging "$unsigned")
grep -Fq "package: name='app.myownware.perseid' versionCode='$code' versionName='$version'" <<<"$badging" ||
    fail 'APK version or package does not match the release.'
grep -Fxq "native-code: 'arm64-v8a'" <<<"$badging" || fail 'Release APK must contain only arm64-v8a.'

: "${PERSEID_KEYSTORE_BASE64:?Release keystore secret is required}"
: "${PERSEID_KEYSTORE_PASSWORD:?Release keystore password is required}"
: "${PERSEID_KEY_ALIAS:?Release key alias is required}"
: "${PERSEID_KEY_PASSWORD:?Release key password is required}"
temporary=$(mktemp -d)
trap 'rm -rf -- "$temporary"' EXIT
printf '%s' "$PERSEID_KEYSTORE_BASE64" | base64 --decode >"$temporary/keystore" 2>/dev/null ||
    fail 'Could not decode the release keystore.'
unset PERSEID_KEYSTORE_BASE64

mkdir -p "$output"
name="perseid-video-downloader-$tag-arm64.apk"
"$build_tools/zipalign" -P 16 -f 4 "$unsigned" "$temporary/aligned.apk"
"$build_tools/apksigner" sign --ks "$temporary/keystore" --ks-key-alias "$PERSEID_KEY_ALIAS" \
    --ks-pass env:PERSEID_KEYSTORE_PASSWORD --key-pass env:PERSEID_KEY_PASSWORD \
    --out "$temporary/$name" "$temporary/aligned.apk" >"$temporary/signing.log" 2>&1 ||
    fail 'APK signing failed. Check the configured release secrets.'
unset PERSEID_KEY_ALIAS PERSEID_KEYSTORE_PASSWORD PERSEID_KEY_PASSWORD

certificate=$("$build_tools/apksigner" verify --verbose --print-certs "$temporary/$name")
fingerprint=$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' <<<"$certificate")
[[ $fingerprint =~ ^[0-9a-fA-F]{64}$ ]] || fail 'Could not read the signing certificate fingerprint.'
"$build_tools/zipalign" -c -P 16 4 "$temporary/$name"

mv "$temporary/$name" "$output/$name"
(cd "$output" && sha256sum "$name" >"$name.sha256" && sha256sum -c "$name.sha256" >&2)
printf '%s\n' "$fingerprint"
