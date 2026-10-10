# Native quote verification

Ticket #13; review base `1b7f0f6`. Accepted boundaries are #1 seams1–4:
observable Android flows with controlled compatible peers, independent signed
vectors, recovery/failure and cross-client representations. The primary-source
contract is NATIVE_QUOTES_CONTRACT.md; no live write is authorized here.

First tracer publishes one quote with commentary, exact signed original plus
current embedded presentation, then reads both commentary and original in feed
and the quote's own conversation. Peer checks request, reference, recipient
ordering, both nested representations and one transmission. Image composition
is completed by #15. Local debug/test assembly passes57s. Actual Android10 red in38025957239 atabb9d85: missing Quote action,
4.833s. Downloaded log and inspected inside-activity image retained under
ticket12/first-red. This was the initial red checkpoint, before the following implementation.

First publication green:38026569765 atc0f356a, Android10 one case6.867s.
Actual public frame independently passes pinned WASM/Elliptic outer/original
and routing-hop checks. Both unmodified A/B constructor/template graphs render
commentary and original identity but omit original text; saved HTML/source hashes
and negative controls establish this partial interoperability limitation. No DOM,
network inclusion or current-deployment visual proof. Inspected native image
shows both texts, with the original's last characters clipped at the bitmap edge.
Review found a wallet-unavailable Quote navigation bug and weak feed relation
assertion; both were fixed in the following discovery slice.
Current commentary-retweet protocol tracer actually fails locally26s before
implementation, then passes39s after retaining its signed quote identity.
The following discovery checkpoint exercises archive/original lookup.

Discovery red38027130575 atf889755: stronger publication case passes; current
retweet discovery times out20s (two cases24.645s total). Log and failure image
retained ticket12/discovery-red. Bounded current stream and independently signed
original lookup implemented; local core/debug/test pass2m1s. The following checkpoint establishes focused green.
Standard Gradle task caching enabled with rolling hosted task-output cache;
source/toolchain input checks remain Gradle-owned and emulator cases still run
afresh. No comparable before/after speed benchmark is claimed.

Discovery green38027654477 at8c60406: Android10 two cases9.894s.
Both review axes0 new actionable findings for that slice. Downloaded logs pass;
current-retweet UIAutomation bitmap still shows Loading original despite passing
original assertions. Cause unverified; excluded from visual acceptance. Next
quote captures use official Compose captureToImage (forceRedraw/PixelCopy).
Independent invalid-original tracer actually fails27s, then passes32s after
retaining independently authenticated commentary while withholding the invalid
original. The following profile checkpoint also uses empty quote histories in legacy
controlled servers.

Profile red38028351952 at58d0da3: two cases28.323s, current flow reaches
Profile but finds no quote atline65; publication still passes. Compose
forceRedraw/PixelCopy images now visibly show current commentary plus complete
original in feed, and the empty profile. Actual hosted build1m24s restores
compilation/resource outputs FROM-CACHE; emulator cases still execute. Saved
logs/images ticket12/profile-red. Minimal profile quote stream and safe shared
cursor for sparse/full streams implemented before the following green.

Profile green38028808825 at721e397: Android10 two cases5.954s. Downloaded
log and inspected Compose profile image retain both complete texts in
ticket12/profile-green. Review caught stale shared social counts when normal
profile history succeeded but the quote stream failed; observation now happens
at each partial publication. Added quote draft reopening/root isolation, original
candidate uncertain-send reconciliation, failed/unavailable-original Retry and
backend-isolated verified original cache regressions. Existing historical/dual
and embedded-only public vector readers checked directly. Local initial batch
core/debug/test assembly passes1m41s; expanded runtime checks pending.

Recovery batch38029461509 at68c2a27: Android10 seven cases23.284s, five
pass including failed/unavailable Retry, backend-original cache isolation and
partial profile counts. Two fixture assertions fail: disabled fields have no
SetText action, and assertTextEquals on the empty editable field includes its
placeholder. Corrected to disabled commentary node and editable empty hasText,
retaining the recovery/isolation requirements. Log retained recovery-fixture-red.
Actual quote candidate process termination/cold reconciliation extends the
existing host/process boundary with a separate public checkpoint; compiled55s,
runtime pending. No process-death acceptance claimed yet.

Final source8778664 fixes process-check original-PID verification, matching
the established social harness rather than rejecting a replacement process.
Both final source/fixture review axes have zero outstanding findings through
that candidate. Full Android10/17 plus both public read checks dispatched
38029859030; no runtime completion claim until downloaded evidence passes.

Full-first38029859030 at8778664 stops in Android10 quote suite: six ofseven
pass14.898s, including corrected draft/uncertain-send cases. Current discovery
assertion tried to scroll before awaiting the independent original lookup. The
inspected post-failure Compose capture shows both texts; it does not establish
precise lookup timing. Added the same bounded nested-original readiness wait
already used by other quote regressions. No production change. Earlier browse8,
Following8, posting15, queue6 and social8 cases plus their three process-recovery
stages pass. Quote process stage and later wallet/live/update checks were not
reached; Android17 skipped. Log/image retained full-first-lookup-race.
