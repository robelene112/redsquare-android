# Outgoing username import warning RED

Android 10 run38117224099 on24d5705 reports five cases, four passes and one failure in28.851s. The import flow verifies the selected scalar2 public key and fails atUsernamePreviewTest.kt:298 because Saved name: NativeCase7@saito is absent. Original PNG was individually viewed and XML inspected: outgoing scalar1 wallet is still active and the different-wallet preview shows no saved-name warning. Later acknowledgement, disabled confirmation and cancellation assertions were not reached. No wallet replacement or live transaction occurred.

Only public unsafe fixtures are used. The fixture authority is explicit debug/emulator/loopback scalar2, not real Saito namespace ownership. Cleanup correctiona6a4faa was committed after this run's source and is not verified by this result.

Original evidence retained verbatim. SHA256:

- `username-ui.txt`: `bfb7dfa537128fd0ea163817d58af33e8290b78a9660b1b094257ef92d912034`
- `username-import-failure.png`: `fa2a6dbd2d0b84ddebc953575f9e02164c2281d9abe41e6b3bf1237327b0c577`
- `username-import-failure.xml`: `d85b8cc445e7061585f7204b5bbf56af93ce660ee83166d0b60801f049f9f2d8`
