# 0008 — Try visionOS first for YouTube previews

## Context

On a Redmi Note 14, YouTube previews took 21–22 seconds with the default yt-dlp client selection. QuickJS accounted for about 12 seconds in separate profiling runs. The visionOS client avoided that challenge and returned 1080p H.264 and stereo AAC on the same sample. Stereo AAC meets Perseid's phone playback needs.

## Decision

Try the visionOS client first for YouTube previews. Accept its result when it includes a title and a usable H.264 and AAC combination at or below 1080p. Retry using yt-dlp's default client selection when the first attempt fails, exceeds its time budget or lacks those formats. The fallback retains the default preview time budget.

Keep the order in core/engine so the app controls the fallback across yt-dlp updates. Show all supported resolutions returned by the successful attempt. Download jobs still resolve the chosen format IDs through yt-dlp's default selection.

## Consequences

Links that work with visionOS can preview without QuickJS. Links that require the default client may take longer. The successful client's available streams determine whether 1440p or 4K appears in the picker.
