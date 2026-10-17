# Chat GIF native acceptance checkpoint

The bounded #43 integration uses the shared native provider/renderer for private
and group Chat. Selected GIF URLs, exact queries, captions, reply prefixes and
available creator/source credits survive saved drafts, fresh Activity instances
and cancelled queued sends. Existing signed private encryption and group delivery
paths remain; explicit Send, wallet/backend binding and membership checks remain.

| Complete run | Exact source | Actual result |
| --- | --- | --- |
| [Android10 39029829620](https://github.com/robelene112/redsquare-android/actions/runs/39029829620) | e85076aaae54605fcf73ad9f424565a3dcffb46a | SUCCESS,404 PASS plus one existing Profile assumption,95 completion records,29 intentional interrupted outputs |
| [Android17 39029992811](https://github.com/robelene112/redsquare-android/actions/runs/39029992811) | e85076aaae54605fcf73ad9f424565a3dcffb46a | SUCCESS,406 PASS,96 completion records,29 intentional interrupted outputs |

Both runs include core114/0failures/0ignored, release assembly/lint, the complete
application and protected process-recovery suite, both approved public feed
reads, public Chat readiness, immutable signed5-to8 updates and final identity
diagnostic. Public reads/readiness are not live Chat delivery. No live Giphy key
or query was used. Native Chat GIF6/0 takes57.281s/58.695s. The existing Posts GIF
preview3 and posting6 also pass on both SDKs.

The first full runs39027471407/39027473052 remain FAILURE at the private history
pre-death required-text capture, with later tails unexecuted. The fixture-only
correction scrolls the real history list before that observation, retaining all
deadlines, exact protected-state, PID and zero-transmission assertions. Focused
actual-death recovery and both subsequent full runs pass. Full recovery changes
PIDs6166to6529 and10460to10541 while preserving the original signed candidate,
reaction and wallet digests. This existing recovery check is distinct from the
GIF-specific fresh-Activity journeys and does not prove GIF-specific process death.

Focused controlled six-case evidence and independent actual emitted-envelope
checks remain in [queue-and-reply-green/android17](queue-and-reply-green/android17/README.md).
Private recipient decryption, exact quoted reference/mentioned key, both pinned
Chat receivers and duplicate handling pass. Group bodies match both pinned
constructors and signatures. Pinned A/B sanitization and offline Chromium preserve
literal URL/query/caption, quote DOM/reference and owned GIF decode. Native motion
assertions check two known frame colors; browser decode is not browser motion.
Earlier REDs and failed observers retain their actual outcomes and limits.

Full packets retain235/236 hashed records. Their inventories cover396/401 device
files,711/712 parsed XML entries and845/849 PNG-signature entries, including
repeated archive captures. Five unavailable optional diagnostics perSDK remain
explicit. Root viewed three named Chat originals perSDK, with preview scrolling
qualified in each README; remaining PNGs have no new visual claim. Captures are
not atomic screenshot/hierarchy pairs or timing/motion measurements.

[Source equivalence](full-regression/source-equivalence.json) proves seven
production/build Git objects identical across production15d, focused91d, prepared
APK sourcee8b, corrected full sourcee850 and reviewed7723. Whole-child review base
stays06e4ef27f55042f59bb79f3db9a97ebafeff4f78. Integrated source Standards0/Spec0
at7723 includes the new development-preferences rules. Final full-evidence
reviews atbbdb66236 are Standards0/Spec0.

Signed26/0.1.26 is prepared with the established package/certificate and minimum
SDK29. Wrapper0, Gradle PASS4m5s and independent signature/package/hash checks
pass; APK SHA256c7f4c4f00262f35a4a92ea2253b4e78989c0438751fb655df51a66b8627b17a7.
It is now published and independently downloaded/verified; the publisher exited0.
Public page/latest/immutable26 manifest equality, downloaded hash/certificate/
package26/0.1.26/min29 pass, and immutable25 manifest/APK are unchanged.
[Thirteen hashed publication records](signed-preview26-published/README.md) retain
the observation. No rebuild was needed for the fixture-only correction. This checkpoint is not physical
update/data continuity,
live Giphy authorization, Google delivery, production-node or Play acceptance.
Parent #31 and its external #16 dependency remain open.
