# Personal controls review

Ticket [#21](https://github.com/robelene112/redsquare-android/issues/21), parent
[#1](https://github.com/robelene112/redsquare-android/issues/1), review base
`99e58c6a49f5a10ee630f948ad3a18c9f5fb5592`.

## Standards

Whole source review through `13943cc`: zero remaining documented-standard
violations or actionable Fowler heuristic findings. Owner-scoped encrypted
choices apply across approved backends, filter social views and quoted originals
without deleting history, preserve independent Mute/Block and guard recovery.
Anonymous reading is permitted only where no saved choices require an owner.
Actual SAF identity verification preserves candidate-bound authenticated backup,
opaque wallet options, acknowledgement, real restoration and independent cleanup.
Bounded first-launch fixture correction through `58426cd` also has zero findings.

Earlier actionable findings were resolved with native evidence: anonymous
cached reading versus unknown-owner policy withholding; repeated native Mute
interaction; anchor-removal/recreation failure skipping active rollback; and
stale handoff wording. The durable previous-policy guard and independent
restoration address the sequencing defect. Tool-enforced issues are not repeated
as review findings.

## Specification

Whole source/fixture review through `13943cc` and first-launch correction through
`58426cd`: zero actionable missing/wrong implementation or scope-creep findings.
Live ticket and parent specification were read. Shared personal state is ready
for dependent notification and Chat tickets; those delivery features are absent
and are not claimed complete by this ticket.

## Verification status

Focused genuine RED/GREEN history is retained in the neighboring evidence
folders and NATIVE_PERSONAL_CONTROLS_VERIFICATION.md. Six ordinary journeys pass
run38158906379 at2473000 in95.631s. Actual pending-save process death passes
run38159358286 atdd40a2c: six ordinary cases95.961s, one actual interrupted stage,
PID4992 gone with positive encrypted guard metadata, one independent cold case
7.083s inPID5082 with unchanged wallet/backend and no recovery reset. Root viewed
all four process originals individually and separately parsed four XMLs; all
16 process-evidence hashes were independently reviewed.

Expanded run38160012441 at13943cc has seven passes and one pre-Activity fixture
error in101.282s. The corruption/repair journey passes all gates and has two
individually inspected original PNG/XML pairs. Identity ran first on a fresh
focused installation and attempted loadExisting before normal app creation.
The correction opens actual first-launch Wallet UI and requires displayed and
saved identity to agree. It never directly creates/resets the wallet or weakens
later backup/import/restoration assertions.

Corrected focused run38161237509 ataa91ff2 passes all8ordinary cases111.163s,
including wallet roundtrip and positive actual PIN removal. The separate actual
death is PID5044→5131; cold1PASS6.427s with same identity/backend and no reset.
Both final fixture review axes have zero findings. Root individually inspected
six identity/corruption and four process original PNGs, then separately parsed
all ten XMLs retained in identity-green. Final paired full Android10/17 acceptance
remains pending. No physical device, power-loss, live write, final ticket
acceptance or new preview APK is claimed.

Final same-source full acceptance started at781bd1a: Android10run38161791197 and
Android17run38161793299, with separately enabled public-feed reads. Results and
both SDK artifact inspections are pending. Source afteraa91ff2 is unchanged.

Final Android10 run38161791197 at781bd1a stops in existing browse verification:
eight cases, seven PASS and one fresh offline profile timeout at300 in35.513s.
Root viewed both original failure screenshots and separately parsed both XMLs;
full10-browse-failure retains six hashes. The in-scope profile header fills the
small screen; its post is awaited before scrolling the lazy list. Fixture-only
correction scrolls first and retains the exact displayed-post assertion. No
production source or timeout change. Remaining full stages were not reached.
Android17run38161793299 is still active. Compile/review and Android10 retry are
next; #21 remains open.

Bounded781bd1a...ce1acdc Spec review has zero findings. Standards identified
one source-supported fixture readiness risk: Posts appears before asynchronous
cached history is loaded, so immediate lazy-list search can run too early.
Correction waits bounded15s for the existing enabled Profile Refresh after
Posts, then scrolls and asserts the exact reference post. That Refresh is
disabled during history loading and enabled after cached history is published.
No production change or weakened assertion. Intermediate full10run38162365495
atce1acdc was explicitly cancelled during setup to avoid testing the superseded
fixture. Source push38162360677 is separate. Recompile/review then final full10
retry are next. Original full17run38161793299 remains independent.

Readiness correction local native-test compile PASS1m13s. Bounded fix reviews
and the corrected full Android10 retry remain next. No final acceptance yet.

Full Android10 retry38162586525 ataa2cf36 reports8browse cases,7PASS/1failure
in34.331s. Cached root scroll/display and its positive capture pass. The next
old pre-scroll wait fails at307 on Replies. Root viewed3originals and separately
parsed3XML; full10-replies-failure retains8hashes. Failure header fills viewport;
outer launcher is after Activity closes, not app evidence. Offscreen reply/cache
existence is not independently proved. Fixture now consistently waits for
enabled Profile Refresh, scrolls lazy list, asserts exact Posts/Replies/Likes
content; bounded15s unchanged/no production mutation. Recompile/review/full10
retry remain required; original full17run38161793299 remains active. Source push
38162581617 passes; #21 remains open.

Consistent offline-tab readiness/scroll correction compiles PASS1m12s.
Both bounded reviews and corrected full Android10 retry remain next.

Full Android17run38161793299 at781bd1a PASS:179allPASS/46completion records/
15actual interrupted stages; core55/0/0, release/lint, signed5→8/cold continuity
and both public reads. Personal8PASS82.867s; actual pending-save death10423→10513
and independent cold1PASS6.65s. Root viewed all25ordinary+4processoriginalPNGs
and separately parsed29XML; both public screen originals viewed with current
notification-history post. Publicowner1PASS8.524s/official1PASS7.055s; ordinary
100rows/91posts/0ignored, quotes4rows/0posts/0ignored onboth. Durable final-full/
android17 retains162hashes/allraw countable completion/death logs, recomputed
identical179/46/15. Evidence reviews and final Android10 acceptance remain.

Android10full38163120112 atf0c6191 passes browse/posting/queue/social/quotes then
author-post has8reported/7PASS1failure58.215s. Image-author deletion waits at151
for a saved attempt after a possibly disabled Confirm delete tap; source renders
the control before asynchronous durable draft load settles. Root originalPNG
shows confirmation/no saved status, with no legacy XML; exact tap enabledness/
scheduling cause unproved. full10-delete-confirmation-failure retains3hashes.
Fixture correction waits bounded20s for enabled Confirm delete, asserts shown/
enabled, captures separate PNG/XML then taps once. All later acceptance gates
remain; case-specific in-scope failure PNG/XML preserves primary exceptions.
No production/cache/peer change, timeout extension or final acceptance claim.

Author confirmation fixture compiles PASS1m10s. Both bounded review axes
and focused Android10 author verification are next; then rerun outstanding
Android10 full scope. Android17 acceptance is preserved at unchanged product
source, with its durable evidence review also next. #21 remains open.
