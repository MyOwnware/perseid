# 0003 — Local storage

## Context

Downloads must survive interruption and finished items need a searchable library.

## Decision

Use Room for jobs and library entries, DataStore for preferences and persistent private files for partial downloads.

## Consequences

Job state and library records can be updated transactionally. Schema changes need migrations. Exclude private state and partials from Android backup and transfer.
