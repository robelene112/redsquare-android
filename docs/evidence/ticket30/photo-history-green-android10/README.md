# Growing private and group photo history, Android 10

Run38731107784 exact96e41ebc SUCCESS,2PASS71.705s. Twelve private and
sixteen group bounded valid320x240 PNG messages exceed5MiB total. Fresh reads
retain all originals and separate drafts, then one further photo succeeds.
Private contact discovery works through multipart storage. A cache-only group
is not marked as unsent work; restoring its draft preserves work detection.
Full installed wallet/backend and independent scoped restoration pass.

Raw output explicitly qualifies API29: its base/.bak AtomicFile implementation
does not exercise the .new write obstruction. No write-failure/retry claim is
made on10. Actual API30+ fault/retry acceptance remains pending on17. This
storage-only run does not exercise native photo picker, process recovery or
complete application acceptance.
