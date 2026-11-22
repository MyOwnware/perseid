# 0007 — Download scheduling across Android versions

## Context

Perseid transfers can outlast the screen turning off. Starting a separate job for each item makes a queued download depend on another background start. Android 14 introduced user-initiated data transfer jobs for long transfers started by the user. [Android Developers](https://developer.android.com/develop/background-work/background-tasks/uidt)

## Decision

Use a user-initiated data transfer job on API 34 and higher. Use a network-constrained foreground WorkManager worker on API 29–33. Both use the same FIFO runner for resume validation, track merging and MediaStore publishing. A running job takes the next queued item when it can.

Connectivity loss allows the scheduler to retry against the network constraint. If Android refuses a start or stops a transfer for another reason, leave its staging files intact and show “Android paused this download. Open Perseid to continue.” in the app language. Bringing Perseid to the foreground requeues Android-paused work. User-paused work still requires an explicit resume.

## Consequences

On API 34 and higher, reopening the app after a reboot schedules unfinished work again. On older versions, WorkManager can restore its pending work across reboot. Both paths reconstruct transfers from Room and validate staged bytes before appending. The resume safety policy in ADR 0004 still applies.
