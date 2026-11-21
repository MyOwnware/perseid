# Perseid

Perseid is a private video and audio downloader for Android.

Perseid is the first app in MyOwnware. It is planned to keep download history, preferences and files on the device without accounts, ads, analytics, telemetry, crash reporting or tracking. See [Privacy](PRIVACY.md).

## Features

- Planned — paste or share a link and preview the media.
- Planned — choose Audio or Video and select video quality.
- Planned — download in the background, pause, resume and save to Download/Perseid/.
- Planned — search, play, share and delete downloads from a local library.
- Planned — change theme and download defaults and update the engine manually.

## Install

Signed arm64-v8a APKs for Android 10 and later will be published on [GitHub Releases](https://github.com/MyOwnware/perseid/releases). The app is in development. There is no APK yet.

## Build from source

Use JDK 21 and install Android SDK Platform 37.2 and Build-Tools 36.0.0.
Set `ANDROID_HOME` to the SDK location or open the project in Android Studio.

```sh
./gradlew ktlintCheck detekt lint testDebugUnitTest assembleDebug --warning-mode fail
```

The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`. It opens a
single Compose screen and supports arm64-v8a devices and x86_64 emulators.

## Screenshots

Screenshots will be added when the app UI is available.

## License

Perseid is licensed under [GPL-3.0](LICENSE). See the [roadmap](docs/PLAN.md) for the planned engine, architecture and release checks.

## Use

Perseid is for personal use with media you are allowed to download. Respect copyright and each site's terms.
