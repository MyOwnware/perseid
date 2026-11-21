# 0001 — Download engine

## Context

Perseid needs site extraction, media transfer and track merging on Android.

## Decision

Use youtubedl-android with yt-dlp and FFmpeg. Use audited prebuilt FFmpeg binaries for 0.x releases. Engine updates are manual.

## Consequences

Verify bundled versions, 16 KB page support, licenses, build flags and exact corresponding sources before release. Ship notices and exact source links. Reassess FFmpeg distribution before 1.0.0.
