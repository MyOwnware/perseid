# Perseid roadmap

Perseid saves video and audio from links on Android.
Paste or share a link to preview the media and choose Audio or Video.
Downloads run in the background and reuse partial files whenever the source allows.
Finished media is available in Download/Perseid/ and in a local library.
The app has no accounts, ads, telemetry, analytics, crash reporting or trackers.

## Architecture

One Android app module, Kotlin, minSdk 29, application ID `app.myownware.perseid`.

```text
Compose screens → ViewModels → immutable UI state via StateFlow
                       ↓
              Room + SharedPreferences
                       ↑
    WorkManager → youtubedl-android → yt-dlp → Android MediaMuxer
                       ↓
              MediaStore Downloads → Android player / share sheet
```

- `feature/download` handles incoming links, preview, format selection and transfer controls.
- `feature/library` handles browsing, playback, sharing and deletion.
- `feature/settings` handles preferences, updates, privacy and licenses.
- `core` holds the database, engine adapter, scheduling, storage and theme. Wire dependencies manually.

## Dependencies

Upstream stable versions checked on 2026-11-21. These are reference versions. Approve each dependency with its license and measured size before adding it, then pin a compatible set. The upstream engine versions can differ from the versions shipped inside the wrapper.

| Dependency | Purpose | Version | License |
| --- | --- | --- | --- |
| Kotlin and Compose compiler plugin | Language and Compose compilation | [2.4.21](https://kotlinlang.org/docs/releases.html) | Apache-2.0 |
| Gradle / Android Gradle Plugin | Build and Android Lint | [9.9.2](https://gradle.org/releases/) / [9.5.0](https://developer.android.com/build/releases/agp-9-5-0-release-notes) | Apache-2.0 |
| Compose BOM / Material 3 | UI and components | [2026.11.01](https://developer.android.com/develop/ui/compose/bom/bom-mapping?from=2026.09.00&to=2026.11.01) / [1.5.0](https://developer.android.com/jetpack/androidx/versions) | Apache-2.0 |
| Activity Compose / Lifecycle / Navigation Compose | Activity, ViewModels, screen routing | [1.13.0 / 2.11.1 / 2.10.2](https://developer.android.com/jetpack/androidx/versions) | Apache-2.0 |
| Coroutines | Asynchronous work, Flow and test scheduling | [1.11.0](https://github.com/Kotlin/kotlinx.coroutines/releases/tag/1.11.0) | Apache-2.0 |
| Room | Job and library records | [2.8.5](https://developer.android.com/jetpack/androidx/versions) | Apache-2.0 |
| WorkManager | Background scheduling | [2.12.0](https://developer.android.com/jetpack/androidx/versions) | Apache-2.0 |
| youtubedl-android | Android engine wrapper and native artifacts | [0.18.1](https://github.com/yausername/youtubedl-android/releases/tag/0.18.1) | [GPL-3.0](https://github.com/yausername/youtubedl-android/blob/0.18.1/LICENSE) |
| yt-dlp | Extractors and transfers | [2026.11.23](https://github.com/yt-dlp/yt-dlp/releases/tag/2026.11.23) | Unlicense for upstream code. Bundled code has additional terms. |
| ktlint / detekt | Kotlin formatting and static analysis | [1.8.0](https://github.com/ktlint/ktlint/releases/tag/1.8.0) / [1.23.8](https://github.com/detekt/detekt/releases/tag/v1.23.8) | MIT / Apache-2.0 |

## Screens and local data

Download, Library and Settings are top-level destinations as they become available. An Android share intent opens Download with the shared link. Paste and share both lead to a preview with thumbnail, title, author and duration when available. Show Audio and Video actions. For video, show available qualities best first with a size where known, mark estimates, and select the best available up to 1080p by default. For audio, prefer the best m4a stream and otherwise save the best audio as-is. Keep container names out of the main UI.

Show progress, speed, time left, pause, resume and cancel with plain errors and a next action. Show Open and Share after publication. Library supports All, Video and Audio filters, search, local playback, share and delete. Settings contains theme, default choice and quality, manual engine update, About, Privacy and licenses.

Room stores the job ID, original URL while needed for retry, media identity, selected format IDs, status, retry state, staging location and export URI. Completed library entries retain title, author, duration, media type, size, completion time and URI. SharedPreferences holds theme and download defaults. Keep partials and sidecars in persistent private storage and exclude private app data from Android backup and transfer. Use only the network requests needed for user-authorized previews, downloads, retries and manual engine updates.

## Resume

Run one active download at a time. Use one unique WorkManager job per download, with a single active slot in the queue. Keep yt-dlp partials and fragment checkpoints in a stable job directory. The files determine how much can resume. Use the same media and formats on retry, with continuation enabled. Show a foreground notification during long transfers.

- Offline or transient failure → keep the files, show Waiting for connection when offline, reschedule with a connected-network constraint and bounded backoff. Exhausted retries show a manual action.
- Pause → persist the user's intention, stop the process tree and preserve progress. Resume → requeue and show Resuming while checking the checkpoint.
- Process death or reboot → WorkManager reschedules eligible work when Android allows it. Reconcile Room and staged files on app start. After a force stop, the user must reopen Perseid. A manually paused job stays paused.
- Expired URL → extract fresh URLs from the original link and verify the same media, formats and resumable stream or fragment layout. If the server rejects ranges, the media changes or continuity cannot be verified, preserve the partials and let the user decide. Never silently restart.
- Finalization → reuse completed tracks if interrupted during local processing. Publish through a pending MediaStore entry, persist its URI, and reconcile interruption so retry cannot publish duplicates. Release staging space after confirmed publication. Cancel removes abandoned partials.

Verify HTTP range and fragment transfers with controlled fixtures and byte or fragment assertions. Test offline recovery, pause, process death, force stop and reboot on devices. Test expired URLs and sources that refuse continuation.

## Release risks

- Extractors break when sites change. Test representative public, authorized links and offer a manual update in Phase 6c. Audit the wrapper's JavaScript runtime and yt-dlp EJS for YouTube before promising support. Review update integrity, keep a working fallback and avoid changing engines during an active job. [yt-dlp documentation](https://github.com/yt-dlp/yt-dlp)
- Native binaries dominate size. Measure download and installed size per ABI and the peak storage needed for staging and export. Publish only arm64-v8a. Test x86_64 on emulators and verify 16 KB page compatibility.
- Use app-owned MediaStore Downloads entries with minSdk 29. Avoid broad storage permissions. Share with URI grants and handle files removed outside the app. Test foreground worker behavior and Android 16 job quotas before release. [MediaStore](https://developer.android.com/training/data-storage/shared/media) · [Long-running workers](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running)
- Audit the exact bundled binaries and third-party code. Ship notices and provide corresponding source and build scripts where required. Use Android MediaMuxer for MP4 and WebM as recorded in [ADR 0006](adr/0006-platform-muxing.md).
- Site support and permission to download are separate. Describe the app as intended for personal use with media the user is allowed to download. Respect copyright and site terms. Keep DRM and access-control bypass out of scope. [YouTube terms](https://www.youtube.com/t/terms)

## Phases

Each implementation phase starts from up-to-date main. Use small signed commits and run available build, lint and tests. Push and open the PR only after approval. The owner performs the rebase merge and creates release tags.

| Phase | Branch | Steps and checks | Release |
| --- | --- | --- | --- |
| 0 Plan | None | Cover architecture, dependencies, resume, risks and roadmap. Approve scope. | |
| 1 Repository foundation | `phase/01-repo-foundation` | Commit repo config → rules → plan and decisions → README → policies → changelog config → templates → branding. Verify signed history and file contents. | |
| 2 Android skeleton and CI | `phase/02-android-skeleton` | Scaffold app and Gradle → add lint and tests → run CI and install on API 29 and an emulator. | |
| 3 Design system and icon | `phase/03-design-system` | Confirm theme → add typography, colors and controls → adapt supplied vector icon with background, foreground and monochrome. Check scaling and contrast in both themes. | |
| 4 Link and preview | `phase/04-link-preview` | Handle paste and share → add engine metadata and preview → test bad links, missing fields and representative sites. | |
| 5 Audio, Video and quality | `phase/05-quality-selection` | Map formats → add quality UI and defaults → test ranking, missing sizes and audio fallback. | |
| 6a Download core | `phase/06a-download-core` | Persist jobs and run one foreground worker → merge compatible tracks → publish and share → verify transfers and playback. | |
| 6b Download and resume | `phase/06-download-resume` | Add queue, pause, resume and recovery → exercise the interruption matrix. | |
| 6c Current engine and manual update | `phase/06c-engine-update` | Bundle verified yt-dlp offline, add signed manual updates and rollback, and verify YouTube playback. | v0.1.0 |
| 7 Release pipeline | `phase/07-release-pipeline` | Build signed arm64 APKs on owner-created vX.Y.Z tags → add checksums, notices, source links and notes → verify the artifact and an install. | v0.1.0 |
| 8 Library and player | `phase/08-library-player` | Add library filters and search → local player → delete/share and missing-file behavior. | v0.2.0 |
| 9 Settings | `phase/09-settings` | Store theme and download preferences, manage abandoned partials, and provide offline privacy and license information. | v0.3.0 |
| 10 Polish | `phase/10-polish` | Review binaries and privacy → check devices, accessibility, recovery and performance → update screenshots and release documentation. | v1.0.0 |

### Phase 4 Download screen order

Link field → preview → Video/Audio → quality list → Download button → downloads list (active + queued) at the bottom.
