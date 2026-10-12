# Pending payment process recovery, Android 17

Focused [run38192305814](https://github.com/robelene112/redsquare-android/actions/runs/38192305814)
atd0db1e3 passes core57/source/debug/test and this bounded native recovery
baseline. Separate source push38192305645 passes release/lint. This is
existing-policy coverage: no production change or missing-feature RED.

Ordinary transfer suite3PASS47.058s; cold recovery1PASS8.964s; positive native
credential cleanup1PASS0.043s. Counts reproduce5completed cases/3completion
records/1actual interrupted process stage from both original and retained logs.
The first deliberate-death stage is not counted as a completed test.

Original process4785 actually terminates, independently checked by the host;
cold process4877 retains the same public identity, owner backend and exact
signed candidate without reset. Reopening emits nothing. Explicit Retry shows
the exact recipient,6.25 SAITO,0.1 fee,original backend and public memo, then
requires freshly observed real Android PIN authentication before exactly one
byte-identical retry frame. System credential cleanup is positively checked.
Public checkpoints compare every original field, not merely identity.

Root individually views4original PNGs and separately parses4XML observations:
before death and cold recovery show Pending/zeroavailable/originalbackend;
cold review shows exact details and Authenticate; after retry remains Pending.
These are separate observations, not atomic pairs. PIN entry itself has no
PNG here; its real system controls, no-frame-before-entry and fresh grant are
runtime assertions. Bitmap appearance does not prove process death or signatures.

Root executes the independent pinned WASM/Elliptic verifier separately against
both actual762-byte frames (0.35s/0.32s). Both results match completely, including
hash735508964cfa1e234b571506a4be482409276fac94b2f117ab40d7233e9ef3a7.
It checks literal synthetic input locations/types,625000000payment,
10000000fee,115000000change,canonicalmetadata,authorsignature,fullreserialization
and actualroutinghop. Isolated network-disabled/read-only/env-empty runner
mounts only named dependency/runtime/reference files and each public frame.
No Saito application/wallet initialization or private node state is accessed.

The initial new fixture compile fails52s at an incorrect public-signature
reference; corrected compilation passes1m28s. Both raw logs are retained. That
error is not a missing-runtime-behavior RED. Baseline source Spec0; Standards0
hard breaches and1DuplicatedCode improvement. The subsequent narrow shared
funding/observed-IME fixture extraction keeps each journey's assertions;
its test APK compilation passes1m21s. Runtime and bounded fix/evidence reviews
remain pending.

SHA256SUMS retains25original/public files. All captures use disposable `.dev`
emulator state and synthetic funds. No real transfer, consensus admission,
chain inclusion, finality, reorg, physical Pixel or whole-ticket acceptance.
Broader failure/isolation, receiving QR, inclusion and final10/17 remain.
