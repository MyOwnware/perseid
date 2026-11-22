# Contributing

Check the [roadmap](docs/PLAN.md) and discuss scope in an issue before starting. Base the phase branch on current main and follow [AGENTS.md](AGENTS.md).

Use small GPG-signed Conventional Commits, such as `feat(download): show video qualities`. Include the checks you ran and their results in your PR. For behavior changes, test the behavior and relevant failure cases. Once the Android project exists, run the build, tests, ktlint, detekt and Android Lint before opening a PR.

PRs use rebase merge. The maintainer merges and creates release tags. Keep private media links, credentials and personal information out of issues and PRs.

## How to bump yt-dlp

1. Download the Unix `yt-dlp` asset, `SHA2-256SUMS` and its signature from the [official release](https://github.com/yt-dlp/yt-dlp/releases).
2. Check the signature with the [upstream signing key](https://github.com/yt-dlp/yt-dlp/blob/master/public.key). Match the asset's SHA-256 against the signed checksums.
3. Replace `app/src/main/assets/engine/yt-dlp` and update its version and SHA-256 together in `app/build.gradle.kts`. Refresh the engine notices, run the Android checks and verify a fresh install offline before testing a download.

The manual updater verifies the detached signature with Bouncy Castle OpenPGP 1.87 and a pinned yt-dlp key. Review any upstream signing-key change before updating the pin. [License notices](app/src/main/assets/licenses/bouncycastle.txt) · [Corresponding source](https://github.com/bcgit/bc-java/tree/r1rv87).

## Prepare a release

1. Open a release PR titled `chore(release): vX.Y.Z`. Generate `CHANGELOG.md` from commits using the pinned git-cliff version.

   ```sh
   bash .github/scripts/install-git-cliff.sh /tmp/perseid-release-tools
   /tmp/perseid-release-tools/git-cliff --config cliff.toml --tag v0.1.0 --output CHANGELOG.md
   ```

2. Add `docs/releases/vX.Y.Z.md` with a short title on a Markdown heading line (`# Short title`), then two or three sentences on what changed for users. Run a preview and review the generated notes before merging the PR.
3. After the release PR is merged, the owner creates and pushes a signed annotated tag on `main` using the release key pinned in `.github/keys/release-signing.asc`. The workflow runs the existing Android checks and verifies the tag before signing or publishing. Keep minor and patch numbers below 100 so the Android version code stays unique. Development builds use `0.0.0-dev`.

The **Release** workflow's **Run workflow** button previews a version on a fork or branch without signing or publishing. The workflow must exist on the repository's default branch before GitHub can offer it for manual runs. The published APK certificate fingerprint is read from the signed APK and is separate from the Git tag signing key.

To preview locally without a tag or release secrets, run the CI Gradle checks, then run:

```sh
bash .github/scripts/preview-release-apk.sh
PATH="/tmp/perseid-release-tools:$PATH" python3 .github/scripts/release-notes.py v0.1.0 MyOwnware/perseid --dry-run
```
