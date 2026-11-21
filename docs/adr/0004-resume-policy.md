# 0004 — Resume policy

## Context

Servers can reject range requests, expire URLs or change the available content.

## Decision

Keep partial files and continue from the last safe checkpoint. If continuity cannot be verified, explain the problem and wait for the user to decide. Run one download at a time.

## Consequences

Retries can re-extract the original link. They must confirm compatible media and formats. Partial files take space until completion or cancellation.
