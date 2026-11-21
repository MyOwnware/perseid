# Perseid

All reports and messages are written in English.

Use one approved phase at a time. Start its branch from up-to-date main and work in small steps. Use the authorized phase workflow. For unscheduled changes across multiple files, get approval for a short plan first. State assumptions and ask about ambiguity or material trade-offs. Before adding a dependency, verify its current version online and get approval for its purpose, license and measured size. Ask before destructive actions. Run the available build, tests, ktlint, detekt and Android Lint checks with zero warnings. Report commands and results. After meaningful changes, explain the design briefly unless asked to skip it.

Use the existing Git identity and normal GPG-signed commits. Test signing first with `echo test | gpg --clearsign > /dev/null`. Stop if signing fails. Use small Conventional Commits with lowercase imperative subjects. Commit after each step of an approved phase without asking again. Keep authorship free of generated attribution. Get approval before pushing. Open the PR as part of the approved push when requested. The owner rebases and merges PRs and creates tags. Never merge, tag, release manually or force-push. Keep secrets out of Git.

Prefer KISS and YAGNI. Extract repetition on the third use. Keep functions focused and generally under 25 lines. Keep edits surgical. Use Kotlin, Compose and Material 3, immutable screen state, ViewModels, Coroutines and Flow, and manual DI. Package by feature under `app.myownware.perseid` with shared platform code in `core`. Put UI text in string resources. Comment only to explain a non-obvious reason. Use short, plain prose. Test behavior, including logic, ViewModels and failure recovery.

Use a single font, a 4/8 dp spacing scale, Material Symbols, 48 dp targets, AA contrast, font scaling and purposeful 150–300 ms animations. Use neutral light and dark surfaces and amber #E8A13A as the sole accent. Preserve the supplied icon with background #121417 and foreground #F2EFE8. Avoid gradients, glass, glows, decorative icons and unnecessary cards.

Keep data on device. No accounts, ads, analytics, telemetry, crash reporting, trackers or Google Play Services. Network access is for user-requested link previews, downloads and manual engine updates, including retries for authorized jobs. Use minimum permissions and exclude private app data from backup and transfer.

Set minSdk 29. Use Room for downloads and library, DataStore for preferences, WorkManager for background jobs, youtubedl-android for yt-dlp, Android MediaMuxer for track merging, and Media3 for playback. Run one download at a time. Default to Video up to 1080p. Audio prefers the best m4a stream and otherwise saves the best audio as-is. Save finished media under Download/Perseid/. Preserve partials when interrupted. If safe resume is impossible, explain why and wait for the user's decision. Delete abandoned partials only on cancellation.

Use GPL-3.0. Audit the exact bundled artifacts, retain notices and provide corresponding sources. Distribute signed arm64-v8a APKs via GitHub Releases. Use x86_64 for emulators only. Use SemVer. Owner-created vX.Y.Z tags trigger release automation with checksums and release notes derived from commits.
