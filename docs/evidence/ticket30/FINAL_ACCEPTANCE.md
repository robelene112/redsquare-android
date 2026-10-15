# Chat photo acceptance

Private and group Chat photo selection, captions, preview/removal, explicit Send,
protected drafts, durable queues/cancellation, cached viewing and expansion are
complete. Interrupted preparation and storage failures preserve composed text.
No arbitrary FileShare was introduced. Both complete native runs and both final
evidence reviews pass. Signed21 is published at https://davidengelen.nl/redsquare/.

| Proof | Run/source | Result |
| --- | --- | --- |
| Android10 full | [38742885120](https://github.com/robelene112/redsquare-android/actions/runs/38742885120), e92c41d3 | SUCCESS, 360 PASS plus one existing profile assumption, 85 completion records, 27 deliberate process deaths |
| Android17 full | [38744845607](https://github.com/robelene112/redsquare-android/actions/runs/38744845607), 8796c45b | SUCCESS, 362 PASS, 86 completion records, 27 deliberate process deaths |

Both include core114, debug/test/release assembly, lint, all previous application
and protected recovery gates, both public feed reads, public Chat, cold-wallet
and immutable signed5-to8 update observers, and final diagnostics. Earlier failed
runs remain failed with their actual unexecuted tails, not acceptance evidence.

Photo UI: ten cases pass in81.007s/83.397s. History: two cases pass in54.691s/
55.015s, including growing multipart private/group history and nested original
installation restoration. Android17 exercises the actual modern atomic-write
obstruction, preserves originals/draft, and retries successfully; Android10 does
not exercise that modern fault. Private photo recovery uses actual PIDs7332 to7568
and5111 to5180; group uses7839 to8177 and5286 to5359. Exact queued signed originals,
raw/Relay once-only delivery, captions, pixels, cached history, full wallet,
selected backend and original-installation restoration pass.

Independent pinned browser private crypto/signing/decryption and actual Chat
receiver/dedup establish compatibility. Group A/B browser constructors reproduce
the native signed envelope byte-for-byte. Browser sanitization and offline DOM
pixel checks preserve exact escaped multiline captions and normalized JPEGs.
These controlled checks do not claim live network admission or production writes.

Final packages retain980/988 hashes,363/365 parsed XML files and385/387 PNGs.
Root inspected ten selected original captures on each SDK. Whole source, bounded
fixture corrections and both complete final evidence reviews report Spec0 and
Standards0. No production source changed after dc28f780. Signed source b42e4e2f
is product-identical to both final runs and97ee0106.

Android17's older permission probe emitted one missing-rg warning. Its retained
original snapshot explicitly says false, so the actual temporary grant/revoke
path matches that observed original state. No prior-true device restoration is
claimed. The portable literal-grep correction separately passes syntax/static
false/true checks and focused run38745018833: eight cases plus actual interrupted
policy PID4889 to5001, recovery and exact restoration. See final-full/android17
and portable-mute-green-android17 for the explicit qualification.

Signed21/0.1.21 build PASS3m59s. Downloaded public APK SHA256
1c5fabe0f9062292807e4c330f2d7a87143e914bfdfce59c9a11d89657c0e6ca,
package nl.davidengelen.redsquare, minimum API29, established certificate
6ca1209bcbba661943f7b683d48b2f58493f13851c760c8d80d1521ac0ee79a2.
Direct public page/latest/release/APK GETs return200. Latest matches immutable21;
independent APK hash, apksigner and aapt checks pass. Immutable19 retains its
original hash. Public-only records: signed-preview21-published. APKs stay outside
Git on storage. Unpublished20 remains unpublished; version counters were not reset.

This ticket does not claim GIF provider delivery, closed-app pushes, actual Pixel7
performance/update continuity, keyboard-open layout acceptance or Google Play
acceptance. Those remain separate tickets. No production node, wallet, chain,
service, ingress or store state changed.
