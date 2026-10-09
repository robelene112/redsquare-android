# Android queued-post background delivery

Checked 2026-10-09 against official Android documentation and published Google
Maven artifacts. This note recommends scheduling behavior, not an immediate
delivery or exactly-once network guarantee.

## Dependency and APIs

Use `implementation("androidx.work:work-runtime-ktx:2.12.0")` and, where needed,
`androidTestImplementation("androidx.work:work-testing:2.12.0")`. Current stable
2.12.0 was released September 23, 2026; its minimum SDK is 24. The published
runtime POM declares Kotlin stdlib 2.1.20; its AAR metadata requires compile SDK
35 and AGP 8.6.0. These requirements fit this project's min29, compile36,
Kotlin2.1.20 and AGP8.10.1. This is metadata compatibility, pending an actual
project build. [Release notes](https://developer.android.com/jetpack/androidx/releases/work#2.12.0),
[published POM](https://dl.google.com/dl/android/maven2/androidx/work/work-runtime/2.12.0/work-runtime-2.12.0.pom),
[published AAR](https://dl.google.com/dl/android/maven2/androidx/work/work-runtime/2.12.0/work-runtime-2.12.0.aar).

Recommend a `CoroutineWorker` with `OneTimeWorkRequestBuilder`,
`Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED)`, and
`enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, request)`, named per immutable
queued transaction. KEEP avoids duplicate unfinished scheduler jobs; it does
not deduplicate network effects. Return `Result.retry()` for transient failures;
default backoff is exponential, initially 30 seconds. A lost constraint stops
execution and permits another attempt. Keep each attempt bounded, propagate
coroutine cancellation and close sockets. Ordinary CoroutineWorker execution
has a ten-minute limit. [Requests](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work),
[unique work](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/manage-work),
[CoroutineWorker](https://developer.android.com/reference/androidx/work/CoroutineWorker).

## Application responsibilities

The following are implementation recommendations inferred from those limits:

- Persist the encrypted signed transaction, owner identity, original backend
  and explicit Queued state before scheduling. Pass only an opaque record ID
  through WorkManager Data. Reconcile durable records with scheduling at app
  startup to repair a crash between persistence and enqueue.
- At worker invocation and immediately before claiming delivery, reread the
  active wallet and selected backend. A mismatch pauses the record. Scheduling
  must never redirect it. Recheck under the same serialization boundary used
  by wallet/backend changes and queue cancellation.
- Make cancellation and the durable Queued-to-Sending claim mutually exclusive.
  Persist cancellation before cancelling scheduler work. Once Sending is
  committed, cancellation must no longer promise that publication was prevented.
  WorkManager cancellation is best effort; executing work can continue.
  Persist uncertainty before transmission, retain the original signature, and
  reconcile available history before retrying that same transaction.
  [Cancellation contract](https://developer.android.com/reference/androidx/work/WorkManager#cancelUniqueWork(java.lang.String)).

## Delivery limits

WorkManager persists scheduling across app restarts and device reboots, including
background execution. Doze, system optimization and retry backoff can delay
delivery. Connectivity does not prove the selected Saito server is reachable.
Force-stop keeps the package stopped until user interaction removes that state;
do not promise delivery while force-stopped. Restore scheduling when reopened.
[Persistent work](https://developer.android.com/develop/background-work/background-tasks/persistent),
[force-stop behavior](https://developer.android.com/about/versions/15/behavior-changes-all#enhanced-stop-states).
