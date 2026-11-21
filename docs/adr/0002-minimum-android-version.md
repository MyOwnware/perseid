# 0002 — Minimum Android version

## Context

Finished downloads need a shared location with minimal permissions.

## Decision

Set minSdk 29 and publish to Download/Perseid/ through MediaStore Downloads.

## Consequences

Android 10 and later are supported. The app can manage its own entries without legacy storage permissions.
