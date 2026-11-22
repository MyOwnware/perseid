# Contributing

Check the [roadmap](docs/PLAN.md) and discuss scope in an issue before starting. Base the phase branch on current main and follow [AGENTS.md](AGENTS.md).

Use small GPG-signed Conventional Commits, such as `feat(download): show video qualities`. Include the checks you ran and their results in your PR. For behavior changes, test the behavior and relevant failure cases. Once the Android project exists, run the build, tests, ktlint, detekt and Android Lint before opening a PR.

PRs use rebase merge. The maintainer merges and creates release tags. Keep private media links, credentials and personal information out of issues and PRs.

## How to bump yt-dlp

1. Download the Unix `yt-dlp` asset, `SHA2-256SUMS` and its signature from the [official release](https://github.com/yt-dlp/yt-dlp/releases).
2. Check the signature with the [upstream signing key](https://github.com/yt-dlp/yt-dlp/blob/master/public.key). Match the asset's SHA-256 against the signed checksums.
3. Replace `app/src/main/assets/engine/yt-dlp` and update its version and SHA-256 together in `app/build.gradle.kts`. Refresh the engine notices, run the Android checks and verify a fresh install offline before testing a download.
