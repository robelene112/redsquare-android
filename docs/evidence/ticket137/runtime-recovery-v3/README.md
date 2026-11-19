# Library recovery runtime checkpoint v3

Fixed product and workflow source: fbe105980a53da6e5ed620ccc08c882062ab503a.
The prospective [contract](contract.json) and unmodified [verifier](verify.py)
pass against each complete original parent. Independent actual-archive
[Spec](spec-review.txt) and [Standards](standards-review.txt) reviews report zero
findings. This checkpoint is not completion of137.

| Profile | Complete parent | Outcome |
| --- | --- | --- |
| Android10, SDK29, 4KiB | [44442937365](https://github.com/robelene112/redsquare-android/actions/runs/44442937365) | PASS |
| Android17, SDK37, 4KiB | [44442941869](https://github.com/robelene112/redsquare-android/actions/runs/44442941869) | PASS |
| Android17, SDK37, 16KiB | [44442946424](https://github.com/robelene112/redsquare-android/actions/runs/44442946424) | PASS |

Every parent contains all243 core tests in48 classes, five service tests, four
Rust contracts and exactly one normal native terminal pass. Actual installed
app/test hash records match the build hash records. The native case exercises
JNI, Android Keystore storage, serialized confirmed-rejection recovery,
provisional-member exclusion from later ciphertext, authenticated historical
key recovery, archive-only backup and a separate fresh operational generation.
The original180-second native and30-second archive bounds are unchanged.

Android10 retains a first INVALID_HEAP provisioning boot and accepted second
boot. The application test itself ran once. Recorded APK hashes bind the
installed artifacts; this small component packet does not retain independently
re-downloadable APK binaries or establish full signed-update acceptance.

Original whole ZIPs, CRC/digests, raw bytes, runtime TARs, run metadata and complete
logs remain under `/mnt/storage/redsquare-android-artifacts/spaces137-runtime/`
in `recovery-v3-10`, `recovery-v3-17` and `recovery-v3-17-16kb`. The three original
archive indexes and frozen-verifier assessments are included here. Earlier
unsuccessful observations and the narrower v2 packet remain retained.

Claims are limited to the library/JNI/Keystore and same-process contracts. This
is not native create/join UI, independent Android process recovery, OEM coverage,
whole137 acceptance or activation of a live Saito service. New content/client
source after this fixed revision requires its own affected verification.
