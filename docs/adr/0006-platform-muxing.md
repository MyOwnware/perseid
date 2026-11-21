# 0006 — Platform track merging

## Context

Phase 6a needs to join separate video and audio tracks while keeping the arm64 APK small. The unsigned arm64 release baseline measured 20,645,319 bytes. The equivalent build with the youtubedl-android 0.18.1 FFmpeg package measured 56,304,736 bytes, an increase of 35,659,417 bytes. The baseline predates the Phase 6a muxing code.

## Decision

Use Android MediaExtractor and MediaMuxer. Merge H.264 or HEVC with AAC into MP4, and VP9 with Opus into WebM. Save verified direct files as-is. Use the Android 10 muxing baseline and hide unsupported stream pairs. Show “May not play on every phone” for 1440p and 4K WebM.

## Consequences

YouTube keeps its 1080p MP4 default. The Phase 5 fixture's 1440p and 4K AV1/AAC MP4 pairs give way to VP9/Opus WebM pairs. AV1 muxing is deferred. The X fixture's split video pairs lack a declared audio codec and are excluded. Direct MP4 candidates can be used once verified, and the audio-only option depends on the device transfer check. TikTok and Facebook retain verified combined formats.

Dropping the FFmpeg bundle removes the audit and corresponding-source obligations for that bundle. Bundled yt-dlp and other artifacts still require license, notice and source review. This decision supersedes the FFmpeg part of ADR 0001.
