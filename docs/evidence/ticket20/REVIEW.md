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
