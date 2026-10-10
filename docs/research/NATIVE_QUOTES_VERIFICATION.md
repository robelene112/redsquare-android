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
ticket12/first-red. First minimal implementation is in progress; no completion
claim.

First publication green:38026569765 atc0f356a, Android10 one case6.867s.
Actual public frame independently passes pinned WASM/Elliptic outer/original
and routing-hop checks. Both unmodified A/B constructor/template graphs render
commentary and original identity but omit original text; saved HTML/source hashes
and negative controls establish this partial interoperability limitation. No DOM,
network inclusion or current-deployment visual proof. Inspected native image
shows both texts, with the original's last characters clipped at the bitmap edge.
Review found a wallet-unavailable Quote navigation bug and weak feed relation
assertion; fixes plus a stronger accessible nested-original assertion prepared.
Current commentary-retweet protocol tracer actually fails locally26s before
implementation, then passes39s after retaining its signed quote identity.
Current archive discovery/original lookup Android tracer added; runtime red pending.

Discovery red38027130575 atf889755: stronger publication case passes; current
retweet discovery times out20s (two cases24.645s total). Log and failure image
retained ticket12/discovery-red. Bounded current stream and independently signed
original lookup implemented; local core/debug/test pass2m1s. Actual green pending.
Standard Gradle task caching enabled with rolling hosted task-output cache;
source/toolchain input checks remain Gradle-owned and emulator cases still run
afresh. No speed improvement measured yet.

Discovery green38027654477 at8c60406: Android10 two cases9.894s.
Both review axes0 new actionable findings for that slice. Downloaded logs pass;
current-retweet UIAutomation bitmap still shows Loading original despite passing
original assertions. Cause unverified; excluded from visual acceptance. Next
quote captures use official Compose captureToImage (forceRedraw/PixelCopy).
Independent invalid-original tracer actually fails27s, then passes32s after
retaining independently authenticated commentary while withholding the invalid
original. Profile discovery tracer and empty quote histories in legacy controlled
servers prepared; actual profile red pending.

Profile red38028351952 at58d0da3: two cases28.323s, current flow reaches
Profile but finds no quote atline65; publication still passes. Compose
forceRedraw/PixelCopy images now visibly show current commentary plus complete
original in feed, and the empty profile. Actual hosted build1m24s restores
compilation/resource outputs FROM-CACHE; emulator cases still execute. Saved
logs/images ticket12/profile-red. Minimal profile quote stream and safe shared
cursor for sparse/full streams implemented; green pending.

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
