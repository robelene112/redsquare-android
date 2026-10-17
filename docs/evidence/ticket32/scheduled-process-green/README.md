# Scheduled social receipt across actual app death

Additional ordinary[#32](https://github.com/robelene112/redsquare-android/issues/32)
verification of the accepted worker. No production receipt change or new RED claim.

| Check | Actual result |
| --- | --- |
| [Android17](android17/README.md),38984566019 at57ffd6f8 | SUCCESS, scheduled PID4482→4741, restoration0.835s |
| [Android10](android10/README.md),38985316045 at47be60d8 | SUCCESS, scheduled PID3804→4054, restoration0.874s |
| [Original source](original-source-run.json),38984556683 | SUCCESS, core/debug/test/release/lint |
| [Corrected source](corrected-source-run.json),38985300921 | SUCCESS, core/debug/test/release/lint |

Both device runs report core114/0/0 and retain their preceding actual dismissed-
replay recovery/restoration. The peer moves byte-identically into shared test-only
sources, compiled by core.test and app.androidTest. A bounded host JVM keeps that
same authenticated Archive fixture alive outside the target process through an
owned reverse mapping. Test/support dependencies do not enter the production APK.

Both setups establish persisted retry and a live PID, then verify its death before
making the Archive available. The original external stop succeeds on17. On10 it
initially exits1; its exact cause is unknown because stderr was not retained.
[That failed run](../scheduled-process-stop-failure/android10/README.md) remains
FAILURE with scheduled recovery tails unexecuted. Cleanup's Process crashed log
is not intended-death proof. The10 correction asks the waiting fixture to terminate
itself with a PID-specific marker, matching established process fixtures, and
retains the command acknowledgement. It changes stop mechanics, not the following
recovery acceptance. No claim is made that17 ran the later fixture.

Without target instrumentation/Activity, force-stop or forced JobScheduler work
during observation, Android starts a distinct PID. Post-death available-mode
reads reach2 on both devices, followed by the exact system notification and visible
caption. Root viewed4 new GREEN originals and parsed12 XMLs. Each SDK retains41
hashed files, plus3 root metadata/equivalence hashes; older originals are retained
without claiming they were all newly viewed. Capture acquisitions are sequential.

Only after observation does cleanup stop the target, independently cancel/drain
owned work and restore device-only encrypted originals. Full fixture wallet/backend
invariants and original identity/backend checks pass. No private originals were
pulled. Host failure/cleanup paths preserve primary errors and reap owned processes.
The owned reverse mapping is removed. Both final continuity markers are retained.

Source reviews atda0a039d, correction1738159f and diagnostic794d145c report0/0.
Android17 evidence review at47be60d8 reports0/0. Initial local build5m32s, corrected
local2m27s/core114, plus isolated host readiness/clean-stop smoke pass; later marker
correction is compiled by exact-source CI. [Eight objects](production-equivalence.json)
prove unchanged production source/build trees from57→47. Combined final evidence
reviews atcae9abe5 report Standards0/Spec0, including all85 manifested files,
12 GREEN XMLs and the preserved initial failure. This bounded observation is accepted.

This proves autonomous scheduling of an already queued local receipt. Incoming
FCM, gateway binding/observer integration on both backends, locked/Doze/Pixel
conditions and full#32 acceptance remain open. Public signed beta24 is unchanged.
Next work moves toward the actual external transport, with existing Firebase setup
question still pending and no production/account/node mutation authorized here.
