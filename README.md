# Perseid

Perseid is a private video and audio downloader for Android.

Perseid is the first app in MyOwnware. It is planned to keep download history, preferences and files on the device without accounts, ads, analytics, telemetry, crash reporting or tracking. See [Privacy](PRIVACY.md).

## Features

- Paste or share a public media link to preview its thumbnail, title, author and duration when available.
- Choose Audio or Video from the available formats. Video defaults to the best available quality up to 1080p when available. Audio prefers m4a and otherwise keeps the best available audio format.
- Download in the background, pause and resume, and save finished files to Download/Perseid/.
- Planned — search, play, share and delete downloads from a local library.
- Update or restore the download engine from Settings.
- Planned — change theme and download defaults.

## Install

Perseid releases are for **Android 10 or later** on **arm64-v8a** devices.

1. Open [GitHub Releases](https://github.com/MyOwnware/perseid/releases) and download the APK and its matching `.apk.sha256` file from the same release.
2. Before installing, verify both files on a computer. These commands use v0.1.0 as an example. On macOS, use `shasum -a 256 -c` in place of `sha256sum -c`.

   ```sh
   sha256sum -c perseid-video-downloader-v0.1.0-arm64.apk.sha256
   apksigner verify --print-certs perseid-video-downloader-v0.1.0-arm64.apk
   ```

   Confirm the checksum reports `OK` and compare the signer certificate SHA-256 digest with the fingerprint in the release notes. `apksigner` is part of Android SDK Build-Tools. [Android signing tool documentation](https://developer.android.com/tools/apksigner)
3. Copy the verified APK to your phone and open it from Downloads. If Android prompts you, open **Settings**, turn on **Allow from this source** for the app opening the APK, then return and tap **Install**. You can switch the permission off after installation.

Perseid checks for download-engine updates only when asked and never checks for app updates. An app such as Obtainium can track the Perseid repository on GitHub for APK updates. [Obtainium](https://obtainium.imranr.dev/)

## Build from source

Use JDK 21 and install Android SDK Platform 37.2 and Build-Tools 36.0.0.
Set `ANDROID_HOME` to the SDK location or open the project in Android Studio.

```sh
./gradlew ktlintCheck detekt lint testDebugUnitTest assembleDebug --warning-mode fail
```

The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`. It opens
the Download screen and supports arm64-v8a devices and x86_64 emulators.

## Screenshots

See [preview and quality selection screenshots](docs/design/) in French and Arabic and
[download recovery screenshots](docs/design/download-06b/) in English, French and Arabic.
See [engine update screenshots](docs/design/engine-06c/) in French and Arabic.
The playback screenshot shows Big Buck Bunny, © 2008 Blender Foundation / www.bigbuckbunny.org. [CC BY 3.0 attribution](https://peach.blender.org/about/).

## License

Perseid is licensed under [GPL-3.0](LICENSE). See [engine notices and source links](app/src/main/assets/licenses/download-engine.txt) and the [roadmap](docs/PLAN.md).

## Use

Perseid is for personal use with media you are allowed to download. Respect copyright and each site's terms.

## Known limitations

- Instagram: many posts require sign-in. Perseid works with posts the site makes accessible without signing in.
- An outdated engine can receive HTTP 403 during a YouTube download or resume. Check for an engine update in Settings. If retries run out, Perseid keeps the partial files and offers **Start again** or **Cancel**. [Issue #9](https://github.com/MyOwnware/perseid/issues/9)
