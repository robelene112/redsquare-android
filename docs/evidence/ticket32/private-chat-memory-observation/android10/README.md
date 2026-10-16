# Android 10 actual process memory observation

Run 38971341246 at `9e2dcd180852a31104e821703f5765fa555989e2` completed SUCCESS. Both existing protected photo-history cases pass in 77.581 s with unchanged counts, capacity/recovery assertions and deadlines. Instrumentation stream messages now retain the observations: both cases run in PID 3297 with Runtime.maxMemory 536,870,912 bytes (512 MiB), memoryClass 512 and largeMemoryClass 512. Device heapsize is 512m; heapgrowthlimit is blank. Entry and per-save values are in the raw log. Diagnostic status0 messages are progress, not additional passing tests; the final JUnit summary reports two cases.

No heap setting or production source changed. This proves the effective limit of this successful run, not why the earlier run reported a 16 MiB growth limit. That failure remains separately retained. The ordinary photo-history checks pass without raising their memory allowance or reducing their data.

This is storage-only evidence, with no native screenshots. Raw unusable best-effort capture output stays as received. Separate source push 38971334056 passed at the same source, including release assembly and lint. This is not physical Pixel or full app acceptance.
