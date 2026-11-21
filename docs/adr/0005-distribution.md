# 0005 — Distribution

## Context

The engine ships native binaries and each architecture adds to the release footprint.

## Decision

Publish signed arm64-v8a APKs on GitHub Releases only. Use x86_64 for emulator builds. Owner-created version tags trigger the release workflow.

## Consequences

Users install and update from GitHub Releases. Each release includes checksums, release notes, license notices and corresponding source access.
