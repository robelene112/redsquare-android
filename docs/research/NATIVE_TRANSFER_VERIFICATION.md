# Native wallet balances and authenticated transfers

Ticket #23, delivery22 of35. Claimed after #22 closure; review base
`2fe1fa51ed8d0c0e250d77e7c2f4198809418fdd`. Implementation is in progress.

## Accepted testing boundaries

Parent specification #1 already authorizes Android application flows with
controlled services (seam1), independent pinned protocol/wallet vectors (seam2),
failure and persisted recovery through that application boundary (seam3), and
cross-client compatibility (seam4). These are the boundaries for this ticket.
No new test-seam approval or live transfer authorization is inferred.

First tracer: open the native Wallet against a selected-key synthetic balance
snapshot, observe literal7.5 SAITO, and copy the displayed receiving address.
This uses seam1 and contains no spend. Capture the original screen and separately
observed hierarchy. Next cycles cover unavailable/invalid balances, independent
funded bytes, and authenticated controlled sending/recovery. Do not bulk-write
all tests before their corresponding implementations.

No transfer, inclusion, physical Pixel, production-wallet or final acceptance
claim exists yet. Public snapshots are selected-server observations, not an
independent UTXO or finality proof. See NATIVE_SAITO_TRANSFER_CONTRACT.md and
NATIVE_SAITO_TRANSFER.md for researched source and earlier reference limits.

## Balance/receive first cycle

Local first test APK assembly passed (2m19s). Hosted focused Android17 run
38184572679 at5c173f5 passed core55/source/debug/test assembly, then produced the
expected native missing-action RED (1 failure,8.688s). Root inspected the original
PNG and separately parsed XML; see ticket22/balance-red/android17.

Minimal implementation now adds exact checked integer snapshot parsing, a
separate balance/receive dialog and clipboard copy. The import-risk indicator
uses the same strict parse, with invalid/unknown remaining UNKNOWN. Numeric UTXO
locations are canonicalized before uniqueness checks. Balance reads validate the
active key before requesting the exact selected endpoint. Display is a fresh
selected-server observation, never a current-spending authorization. Receiving
address remains independently usable. Native GREEN is pending.

Focused native GREEN38184994485 at48e8c44 passes one case14.8s and core55/source/
debug/test assembly. Root inspected the original balance/receiving PNG and
separate XML, including clipboard overlay. Local minimal build passed3m21s.
Both first-slice source reviews found no production defect; current-handoff
wording and primary-preserving fixture cleanup were corrected subsequently.
The new dialog's source label distinguishes it from the feed's Server label.

## Independent funded-payment first cycle

payment-reference.cjs independently reproduces the earlier executed literal
signature,621-byte candidate and752-byte routed frame using pure pinned WASM and
Elliptic. No initialized wallet, private node state or real funds are accessed.
The native single-vector test compiles and first fails at the declared but
unimplemented funded factory, then passes after minimal construction. Both local
builds47s; raw logs are in ticket22/payment-reference. The zero-payment restore
contract is unchanged. Payment restoration/authentication/reservation/inclusion
remain separate unfinished cycles; byte compatibility is not consensus admission.

## Funded restoration and review fixes

The next independent funded-restoration case observes an unimplemented-reader
RED (46s), then both funded cases pass (49s). It checks unchanged funded bytes
and rejects them through the existing zero-payment reader. Restoration validates
complete canonical framing, funding, recipient/change, exact metadata and author
signature. Input coordinates remain unsigned; this is not an external UTXO proof.
See ticket22/payment-restore for raw logs and retained new-case XML.

Constructor review: Spec0; Standards no hard breaches, two improvements (shared
Normal serialization, early oversized-memo guard). Both are implemented. Slip
lists also become defensive/unmodifiable. After fixes all57 core tests pass,
0 failures/errors/skips (49s), including all existing independent zero-payment
vectors. Bounded source-fix/restoration reviews both report 0 findings. No authenticated
Android send or whole-ticket acceptance is inferred.

## Reviewed authentication/send first Android cycle

Focused Android17 run38186051050 at610b608 passes core57/source/debug/test
assembly and the retained balance case, then fails at the missing Send SAITO
native action (2 cases,1 failure,20.268s). Root viewed the original PNG and
separately parsed its XML. Evidence: ticket22/authenticated-send-red/android17.
This is a behavior RED, not a fixture/compilation failure.

The minimal implementation adds a foreground review, live funding and peer
readiness, real device authentication, selected-wallet/backend checks at physical
socket enqueue, and encrypted wallet-wide pending reservations. Signed candidates
are saved and read back before transmission. Saving or sending uncertainty never
releases an input. The two-file creation guard fails closed on partial storage;
records are bounded and never silently truncated. Import treats retained or
unreadable transfer state as outgoing pending/unknown work. Socket enqueue does
not prove inclusion. No automatic monetary resend or offline queue exists.
Native GREEN, independent verification of its actual emitted frame, cancellation,
recovery/retry, inclusion and final acceptance are still pending.

First implemented run38186822190 at860b5e1 passes core57/source/debug/test and
balance, then stops at a fixture scroll call on the fixed Review footer
(2 cases,1 failure,35.847s). Root views PNG and separately parses XML: populated
fields, actual IME covering the footer. The correction dismisses the actual IME
and positively waits/clicks that fixed action; no acceptance assertion is removed.
Source push38186821983 independently finds a NewApi lint error for BigInteger.TWO.
Use valueOf(2) without changing Android10 minimum. See
ticket22/authenticated-send-first-failure/android17. No authenticated send claim.

Next run38187253163 at75002a7 passes source/core57/balance and reaches the real
system PIN flow plus Pending/zero-available screen, but the actual peer-frame
wait fails (2 cases,1 failure,50.622s). Root inspects3 originalPNG+separateXML;
the credential PNG is black, and the separate hierarchy contains
the actual system PIN controls before entry. No frame/GREEN acceptance.

Spec reviews of860b5e1 and75002a7:0 actionable findings. Standards finds one
delivery issue and one fixture safeguard: immediate cancellation after enqueue
can discard queued frames, and PIN cleanup needs positive verification/primary
failure preservation. Both fixes are implemented: bounded graceful socket
completion (never a payment ACK), standard controlled-peer close response, and
protected setup/clear with explicit insecure-state check. Neither the source
contract nor this failed run proves immediate cancel was the sole runtime cause.
See ticket22/authenticated-send-second-failure/android17 and the official
[OkHttp5.4.0 queue/close/cancel contract](https://github.com/square/okhttp/blob/parent-5.4.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/WebSocket.kt).

Corrected focused run38187897889 at0b0d75d:2PASS44.412s, core57/source/debug/test
pass. Source push38187897999 also passes release/lint. Root individually views
4originalPNG and separately parses4XML. No frame before real PIN authentication;
one actual type4 frame after it; Pending/zeroavailable and positively cleared PIN.
All retained11hashes: ticket22/authenticated-send-green/android17.

Pure verify-payment-frame.cjs independently validates that actual762-byte frame
with pinnedWASM/Elliptic: originalsyntheticinputcoordinates/types, literalpayment
625000000/fee10000000/change115000000, canonicalmetadata, authorsignature,
WASMdeserialize/reserialize and actualroutinghop. Framehash
abcd8ee6a0350f7db661c021f0fbaf3d952f965f1077005c631e3b91e23abd9e.
Both source-fix review axes0findings. No consensus admission, realfunds, inclusion,
Android10, physicalPixel, failure/recovery orwhole23acceptance is inferred.

## Real authentication cancellation coverage

One new native regression case exercises the already implemented cancellation
policy through the real Android credential screen: Back cancels before sending,
then reopening shows the same wallet, all7.5 SAITO available and no Pending item
or controlled-peer frame. Both real-auth cases share protected emulator PIN
setup/positive cleanup and the same external synthetic funding service.
Local test APK assembly passes1m22s. Hosted execution is pending; this is new
coverage of existing behavior, not a claimed missing-behavior TDD RED. No
production source changes or acceptance assertions removed.

Focused cancellation run38188796688 at9a8b043 passes3cases49.799s and core57/
source/debug/test. Source push38188796707 independently passes release/lint.
Both bounded source axes0findings. Root inspects2 original PNGs and separate
XMLs; all5 hashes retained in ticket22/auth-cancel-green/android17. Real PIN
cancellation/reopen shows same identity, all7.5 available, no Pending/no frame
and positively cleared PIN. No process-death, retry, inclusion or final proof.

Both bounded cancellation evidence review axes also report0 findings, including
independent5-hash/log/original-screen/separate-XML inspection.

## Pending reopening and exact authenticated retry tracer

The next test extends the successful native send journey without removing its
existing assertions. Recreate the Activity, reopen the same wallet and require
Pending/zeroavailable with still exactly one controlled-peer frame. Then choose
Retry transfer, review the exact original recipient/amount/fee/backend/memo and
require a fresh real system PIN before a second byte-identical signed/routed
frame. The first runtime run is pending; Retry is not implemented yet.
Activity recreation is not process death. Final retry acceptance also requires
inclusion/unknown-history reconciliation and backend/identity/storage failure
cases; none is inferred from this first controlled fresh-unspent snapshot.

Local retry-tracer test APK compilation passes1m18s. Both bounded test-source
review axes report0 findings; focused38189355347 atb117ddf is pending.

First retry-tracer run38189355347 atb117ddf passes core57/source/debug/test and
retained cancellation/balance, but stops atline67 after unconditional Back while
waiting for Review (3cases/1failure59.925s). Source push38189355162release/lint
passes. Root original PNG/separateXML shows Wallet with no transfer form or
keyboard in the later failure capture;3hashes retained in
ticket22/retry-fixture-failure/android17. This is a fixture interruption, NOT the
expected missing-Retry behavior RED. The immediately preceding window/IME state
was not captured, so sole runtime cause is unproved. Correction observes actual
IME or visible Review, presses Back only for a positively observed keyboard,
then waits for it gone and the unchanged fixed Review action visible.

The corrected fixture test APK compiles1m22s. No acceptance assertions or
production behavior changed; hosted rerun remains pending.

Corrected focused38189921586 atea0df3d reaches genuine missing-Retry RED at
line116 (3cases1failure54.444s): initial authenticated send, real Activity
recreation, same wallet/Pending/zeroavailable/originalbackend and exactly1frame
all pass; no automatic resend. Cancellation and balance pass. Core57/source/
debug/test pass; sourcepush38189921312release/lint passes. Both fixture-fix review
axes0findings. Root views2originalPNG+separateXML;5hashes in
ticket22/retry-red/android17. Successful retry, processdeath/inclusion stillunproved.

Minimal explicit retry now retrieves the strict encrypted candidate only for
its original selected backend, presents the same review and requires a fresh
device grant. Fresh funding is re-read before review and after authentication;
every exact original positive input must still be eligible. At physical enqueue
the saved/live wallet, saved/live backend, operation authority and the exact
retained candidate are checked under the existing lock. It never re-signs,
creates another candidate/reservation or automatically sends. Unknown/missing
inputs retain Pending and reservations. Local build/nativeGREEN are pending.
This first fresh-unspent controlled cycle is not final inclusion/reorg policy
or whole-ticket acceptance.

Minimal retry debug and test APK assembly passes2m37s. Hosted GREEN and
bounded source/evidence reviews remain pending.

Corrected minimal retry focused38190583142 atf37feba:3PASS66.944s/core57/source/
debug/test pass; sourcepush38190582887release/lint also passes. Both bounded
minimal-source axes0findings. Root individually views3originalPNG+separateXML;
9hashes retained in ticket22/retry-green/android17. Samewallet after Activity
recreation, Pending/zeroavailable/originalbackend, exactly1frame through reopening
and before fresh real PIN, exactly2frames after it with byte-identical retry.
PIN positively clears; cancellation/balance pass.

Root also independently verifies the actual retained first762byte frame with
purepinnedWASM/Elliptic (0.42s): literalinputs/payment/fee/change/metadata/author
signature/fullreserialization/routinghop. Hash5005caf258c18ab4b09be34eba8946270f4bd7535d9a3a35c2eb403957c7f055.
Second identity follows the native byte-equality assertion; no separate second
artifact. No processdeath/reorg/inclusion/admission/finality/real funds or whole
acceptance. Broader failure/recovery/inclusion and receivingQR cycles remain.

Both bounded retry evidence axes through849a960 report0 findings. The next
cycle covers existing pending-payment behavior across actual process death at
the already accepted application/recovery seams1 and3. Preparation uses only
the disposable `.dev` emulator identity, selected owner backend and synthetic
funding. A real reviewed/PIN-authenticated first frame is checkpointed as public
bytes before deliberately killing the process. The host verifies termination
and a different cold process. Cold recovery never resets wallet/backend/work,
must emit nothing automatically, then requires exact review and fresh real PIN
before a byte-identical explicit retry. The host clears the disposable PIN and
positively verifies the native secure-state flag even if either phase fails.
This is existing-policy coverage, not a fabricated missing-feature RED. Runtime
and bounded source/evidence reviews remain pending; no inclusion proof follows.

The first new test compile fails52s due to a fixture reference to payment.id;
the public signature is payment.transaction.id. Corrected test APK compilation
passes1m28s, and host Python/shell syntax checks pass. This compilation error
does not establish a missing runtime behavior. No production source changed.

Process baseline focused38192305814 atd0db1e3 PASSES: ordinary3PASS47.058s,
actualdeath4785→4877/cold1PASS8.964s,positivecredentialcleanup1PASS0.043s;
5completed cases/3records/1actualdeath. Core57/source/debug/test and separate
sourcepush38192305645release/lint pass. Root4originalPNG/separate4XML and pure
pinnedWASM/Elliptic verification of bothactual762byteframes pass. Samewallet/
originalbackend/candidate/noauto/exactreview/freshrealPIN/exactretry/clearedPIN
are established. Bothframehashes73550896...;25hashes retained in
ticket22/process-green/android17. No inclusion or complete failure acceptance.
Baseline Spec0; Standards0hardbreaches/1DuplicatedCode improvement. Review-stage
extraction shares only existing controlled funding/observed-IME helpers across
the two journeys, retaining their independent assertions. Test APK compilation
passes1m21s; runtime and bounded fix/evidence reviews remain pending.

Next protocol tracer uses the already approved seam2: the public native full-
block reader and exact saved-payment matcher against an independent synthetic
WASM/Elliptic block. This checks complete content/header/signature/hash binding
and permitted output-coordinate assignment. It does not establish consensus
eligibility, UTXO admission, current selected-chain ancestry or finality.
The isolated pure reference runner executes successfully0.38s:1010bytes,
height2000002,time1700000010000,creatorpublishedunsafe3,parentab32,
hashb33d2fc4aee4333b6cb1e395c0b852aa3b1aa268e21702b3819b34b2a3eab289,
singlepaymentleaf5844cda6... . Pinned WASM deserialize/serialize assigns output
height2000002/ordinal0 while preserving signed payment and original inputs;
exact full representation is independently constructed and compared. Creator
preimage209bytes is signed/verified with pure WASM and Elliptic. All-relevant
lite equals full here; this does not resolve mixed-SPV proof gaps. No initialized
Saito wallet/node/private state or live block fixture is used. The first native
case is written against an explicit unimplemented reader; compiled behavior
RED is observed49s at the unimplemented public reader, after successful
compilation. Minimal bounded full reader/root/header/creator/exact payment
matcher then passes the same case49s, and the full core suite passes58/0/0/0
in18s.8hashes in ticket22/block-reference-first. No further cases were bulk
written; odd/system/SPV/largeinteger/mutation/ancestry cases remain unproved.
Bounded source/evidence review is pending.

Helper extraction focused38193034021 at1818ea7 also PASSES: ordinary3cases
70.424s,actualdeath4698→4814/cold1case9.897s,positivecleanup1case0.062s;
5/3/1/core57/source/debug/test. Sourcepush38193034164release/lint passes.
Both prior bounded source-fix/baseline-evidence axes0remainingfindings. Root
4separateXML,2fullyviewedoriginalPNG and2toolstrips provenbyteidentical to the
fullyviewedPending original; no cause inferred. Bothactual762Bframes separately
pass pureWASM/Elliptic with identicalhash421370ca... .22hashes retained in
ticket22/process-shared-green/android17. No inclusion or wholeacceptance.

Next single seam2 regression exercises changed original funding coordinates
under an unchanged payment signature/block header/root. The independent pinned
generator changes the first positive input block2000000→2000001; WASM retains
the creator hash and payment leaf/signature while observing the changed input.
The native matcher must refuse it as the saved original payment. This covers
existing exact-coordinate policy; no missing-behavior RED is assumed. Independent
execution succeeds (pure WASM/read-only/networkdisabled, no walletinit), and
both native positive and altered-input cases pass29s/2tests/0failure/error/skip.
4hashes retained in ticket22/block-input-coordinates. No production change or
fabricated RED; no other mutation cases are added. Bounded review is pending.

First full-reader/source/evidence review throughd99c779: Spec0; Standards0hard
breaches/1DuplicatedCode improvement. Its sourcepush38193713102release/lint/
core58 passes. The review-stage fix shares only raw transaction preimage bytes
between the two real binary consumers (Archive envelope and full-block leaf),
leaving each caller's validation/type/trust policy unchanged. The two block
cases also share their fixture reader. Full core after fix passes59/0/0/0 in50s;
all14originalXML/16hashes in ticket22/block-preimage-review-fix permit independent
recount. Source-fix/input-mutation/evidence review remains pending. No Android
inclusion/currentchain/consensus or wholeacceptance is inferred.

Both bounded original-input/preimage-fix axes through172ac46 report0findings;
all4+16hashes and independently summed59corepasses verified. Next sole seam2
existing-policy regression uses three entries [GoldenTicket2,Normal0,Fee1], with
opaque nonUTF8dataff008041 in the first and empty93byteFee last. Pinned Rust
computes the odd Merkle root from a zero-root seed, then pure WASM/Elliptic signs
and verifies the resulting creator preimage. Exact1200byte serialization and
payment logicalordinal1 are independently checked. Reference executes0.63s,
root0c055916.../hash6ed747ba... . Native case/runtime remains pending. This is
all-entry content binding, NOT consensus eligibility of the synthetic system
entries, UTXO admission, selected-chain membership or finality. No production
change or fabricated missing-behavior RED is assumed.

Odd-entry native regression passes3/0/0/0 in31s. Four original hashes in
block-odd-entries retain the reference/log/XML/counts. Sourcepush38194173861
at172ac46 also passes release/lint/core59. Bounded odd source/evidence review
remains pending; no current-chain/status proof follows.

Odd-entry review at82ed842: Spec0; Standards0production breaches, one handoff
clarity correction and one shared header-signing judgment. Current handoff now
consolidates actual status; the narrowly shared signing helper reproduces the
complete independent descendant JSON byte-for-byte.

Next seam2 ancestry tracer: independent482B descendant tipbf72deac... connected
to the odd payment block. Pinned Rust computes the root and pure WASM/Elliptic
verify the exact creator preimage/signature/hash/full serialization. Native
compiled unimplemented-reader RED1failure47s ->same-caseGREEN1PASS48s. Next
existing-policy unrelated valid payment ancestor cannot prove membership;
positive+orphan2PASS28s. Eight hashes in chain-reference-first. Public observer
limits16blocks/32MiB; unknown on missing/malformed/mismatching coverage, no
negative transaction result or reservation release. HTTP elapsed/per-body bounds,
Android status/reorg/persistence and complete acceptance remain separate.

Both bounded ancestry/helper/handoff reviews at8035be7 are clear; sourcepush
38195258684 passes release/lint/core62. Next seam2 prior-inclusion check uses
independent Fee-only alternate at covered height2000002/hash66e1534d... and
signed child2000003/hash34a627c2... . Compiled missing-check RED3cases/1failure47s
->minimalGREEN3PASS35s. Sole subsequent existing-policy missing-parent regression
returns UNKNOWN; all4PASS28s. Eight hashes in chain-reorganization. No old-height
coverage means no reorg claim; no unreserve/automatic send. HTTP time/body bounds,
durable Android status and whole acceptance remain pending. Review is pending.

Review at a97f122: Spec1unsafe PRESENT shortcut, Standards0hard breaches and
2duplication judgments. Independent altered-input case reaches PRESENT RED
5cases/1failure30s ->removalGREEN5PASS48s. Current exact inclusion now belongs
exclusively to observe(payment); prior check only proves displacement or UNKNOWN.
Review-stage shared verified walk and Fee-only generator preserve entire JSON
byte-for-byte. Fullcore66PASS49s;24 hashes/all15 original XMLs in chain-review-fix.
Sole opaque-body mutation baseline4PASS30s also retained there; signed header
cannot authenticate changed unrelated opaque data. No fabricated RED/production
change for that baseline. Sourcepush38195650210 at a97f122 passes. Both bounded
source-fix/evidence reviews remain pending. Android/HTTP/persistence/QR/final
acceptance remain separate.

Both bounded source-fix/evidence axes at a467b7a are clear; sourcepush38196103869
passes release/lint/core66. Next sole native seams1/3 test sets up the existing
independent unsafe1 candidate in disposable dev/emulator storage, restoring
prior wallet in finally, and uses fresh unsafe3-signed headers over copied
independent content. It must show Included/no Pending/no Retry, exact original
backend/same identity and no automatic frame through Activity recreation.
First compile1m52s fails missing direct test crypto dependency; pinned test-only
BC1.86 fixes compile1m38s. Inspected incomplete fixture backup construction is
replaced with validated known private-scalar public-key checks before runtime;
current fixture compile1m18s. Terminal status assertions added afterward await
hosted compile/runtime. Four preparation hashes retain logs and pure verifier
dry-run on known reference. No native missing-Included RED or actual block
compatibility/runtime/UI acceptance is claimed yet. HTTP/UI integration is absent.

Preparation review at3a41bf1: Spec1repeat cold terminal gates; Standards1protected
identity-bound fixture cleanup. Corrected journey repeats no Pending/no Retry/
original backend/zero snapshot availability after recreation. Initial replacement
is protected, cleanup restores only exact prior/fixture options and positively
checks persisted restoration, preserving primary/suppressing cleanup failures.
Test APK with all corrected assertions compiles1m20s; one original hash in
inclusion-test-review-fix. Empty snapshot availability does not independently
prove reservations. Source-fix review/native missing-Included RED remain pending;
no product change or reproduced restoration failure is claimed.

Both bounded test-source-fix axes at4bbf039 are clear; sourcepush38197067577
passes. Focused Android17run38197165531 reaches genuine missing-Included RED:
4reported/3existingPASS/1failure at StatusTest.kt:64 after20s,95.232s stream.
No completion/death is counted. Root original PNG and separate XML show Pending,
Retry, zero available, original owner backend and independent amount/recipient.
Four hashes in inclusion-ui-red/android17 retain actual failure. No successful
reopening/runtime-block verification follows. The next minimum implementation
adds bounded selected-server block reads and ephemeral exact current inclusion;
durable prior observations/reorg/QR/whole acceptance remain separate.

Minimum selected-server HTTP/current exact inclusion UI integration compiles
debug/test APKs2m25s; one original hash in inclusion-ui-build. Per-block8MiB,
shared retained bodies32MiB, monotonic20s observation budget and per-call remaining
timeout10s maximum; no redirects/fallback/send or reservation release. Inclusion
is ephemeral fresh exact proof only. Native GREEN remains pending.

Bounded current inclusion review4bbf039...5fee7e1: Standards0hard breaches/0
heuristic findings; Spec1explicit observation-copy gap. Review correction preserves
Included and adds selected-server/status-can-change text with positive checks on
both sides of recreation in the existing journey. Debug/test compile2m34s; one
hash in inclusion-copy-review-fix. First native GREEN38197933430 at5fee7e1 remains
running; copy/source-fix review/runtime acceptance remains pending.

First unchanged-test native Included GREEN38197933430 at5fee7e1 passes ordinary
4cases74.089s, existing death4716→4810/cold1PASS11.421s/credential cleanup1PASS0.055s:
6completed/3records/1death/no assumptions, core66/release/lint. Root2new original
Included PNG/separate2XML show status/originalbackend/no Pending/no Retry; native
sameidentity/zero frames/recreation assertions pass. Actual1200B inclusion and482B
child independently pass pinnedWASM/Elliptic/header hash/Rust recomputed root/
exact full serialization; exact reference candidate is only in the inclusion.
18hashes/rawcountable logs in inclusion-ui-green/android17. Current selected-chain
membership is established by the native observer/test, while pure checks are only
synthetic content compatibility, not consensus/finality or real-funded admission.
Both copy-fix review axes atdf2a012 clear; new-copy runtime38198454529 is pending.
Empty snapshot zero alone remains non-proof of reservations; durable Unknown/
reorg observations, receivingQR and full acceptance remain pending.

FirstGREEN evidence review at242ee6c: Standards0; Spec1active handoff retains
superseded pending claims. The active section is now consolidated to latest
verified core/native state, genuinely pending new-copy runtime and unfinished
durable Unknown/reorg/QR/final acceptance. Chronology preserves prior pending
states as dated history; evidence/rawcounts are unchanged. Fix review pending.

Both handoff-consolidation axes through eca0d63 are clear. Qualified-copy native
GREEN38198454529 atdf2a012 passes ordinary4cases73.189s and existing pending death
4810→4908/cold1PASS9.841s/credential cleanup1PASS0.055s:6/3/1,no assumptions,
core66/release/lint/source. Root firstoriginalfullyviewed/secondtoolstrip proven
byte-identicalfullPNG34f13bb7...; separate2XML and positive nativegates retain
Included/observedselectedserver/canchange/noPending/noRetry/originalbackend/identity/
zeroframes through recreation. Actualnew1200B/482B separately pass pinnedpure
WASM/Elliptic/headerhash/Rust-recomputedroot/fullroundtrip/exactcandidate/parent.
18hashes/rawcountable logs in inclusion-copy-green/android17. New next single
native cycle extends this journey only after this GREEN: fresh snapshot lists
all original positive7.5SAITO inputs while block coverage becomes unavailable
across another recreation. It must retain last inclusion as current UNKNOWN,
show zero available from reservations and send nothing. Current production has
no durable last observation, so test preparation precedes runtime RED. No new
Unknown/reservation/reorg/QR acceptance claimed yet.

Next historical-status test APK compiles1m18s; one original hash in history-test-
preparation. Positive input coordinates2000000/7/index2 Normal5SAITO and
2000001/9/index0 RouterOutput2.5SAITO are eligible under served80640 window at
2000003. New current UNKNOWN/last observation runtime RED remains pending.

Historical tracer prep review01b0bd0: Spec2/Standards2 for fresh positive-snapshot/
missing-block observability and stale active compile wording. Same journey now
requires positive actual HTTP boundary counters before terminal assertions;
newly advertised tip2000004/literal64c hash with no body prevents an old block
cache from establishing current ancestry. Original positive eligible7.5 inputs,
identity/backend/lastseen/currentUnknown/no frames gates remain. Corrected test
APK compiles1m21s/one hash in history-test-observation-fix. Production untouched;
corrected native RED/fix review remain pending. Earlier38199323112at01b0bd0
still runs the weaker precursor without these positive observation guards.

Both tracer-sensitivity correction axes through8b9a237 are clear. Earlier weaker
38199323112 at01b0bd0 reaches precursor missing historical label RED:4reported/
3earlierPASS/1failure StatusTest.kt:113 after20s;95.707s stream, no completed
success/death stage. Root original history failurePNG/separateXML showPending/
Retry/zero/originalowner/3.25/independentrecipient, no lastseen.4hashes in history-
precursor-red/android17. This lacks later fresh-positive/newtip404 guards, so no
new boundary/reservation acceptance follows. Corrected-source native missing
history runtime38199796443 at8b9a237 is running; production remains untouched.

Corrected strong history38199796443 at8b9a237 reaches genuine RED:4reported/
3earlierPASS/1failure at126/20s,92.419s. Positive actual own-key7.5 snapshot/new
2000004 tip404 counters and native zero availability pass before missing current
UNKNOWN/lastseen. No completion/death follows. Root separate XML shows Pending/
Retry/originalowner/zero/3.25; original PNG tool only a strip, qualified accordingly.
Four hashes in history-ui-red/android17. Minimum encrypted last-observation storage
and separate current UNKNOWN UI now implemented without releasing inputs or sending.
First compile2m43s; primary API inspection catches API31 longValueExact before
runtime. Checked positive bitLength<=63/toLong preserves min29 compatibility.
Corrected debug/test/release lint passes5m52s;2hashes in history-ui-build. Native
unchanged-test GREEN and bounded source/evidence review remain pending.

Unchanged history GREEN38201245157 at4bfe312 passes ordinary4cases78.183s,
existing separate pending process-death/cold1PASS9.996s/cleanup1PASS0.051s,
6/3/1/no assumptions/core66/release/lint. Root full original UnknownPNG/separate
XML and positive native fresh7.5/newtip404/zero reserved/identity/backend/no frames
gates pass;10hashes in history-ui-green/android17. Both bounded source axes flag
one authority-before-lock ordering gap allowing old Activity's delayed history
commit after a newer save. Source-inspected, no scheduler runtime reproduction.
Minimal same-lock current check+record fix builds debug/test2m53s; one hash in
history-review-lock-fix. Fix review and same unchanged-test native rerun pending.

Lock-correction Standards review confirms source race fixed and finds one evidence
index mismatch: original counts index transfer-process/ paths but files were
flattened. Retained original bytes now occupy the indexed subfolder; same10hashes
with corrected relative paths. No tests/source/rawcounts changed. Spec review and
fixed-source native38201839598 remain pending.

Both history correction/evidence axes through17b11c0 clear. Same unchanged native
38201839598 at0ab4966 passes ordinary4/82.386s and separate pending death/cold1/
9.867s/cleanup1/0.057s:6/3/1,core66/release/lint. Root full separate UnknownXML and
native positive fresh7.5/newtip404/zero reservations/identity/no frames pass; original
PNG tool strip qualified. Ten hashes/indexed paths in history-lock-green/android17.
This is normal recovery verification, not source scheduler-race reproduction.
Next sole cycle extends native journey with independently copied Fee-only alternate
2000002 and signed2000003 child, literal positive originalinputs, explicit reorg
label/no current Included/no Unknown/no Pending/no automatic frame through another
recreation. Product unchanged; test compile and native RED remain pending.

Reorganization test APK compiles1m40s, then supplied alternate-block capture is
moved before missing-label assertion for independent failure-fixture checking;
current test APK compiles1m30s. Two original logs/hashes in reorganization-test-
preparation. Production unchanged; native missing-label RED remains pending.

Both reorg tracer axes through3b5933d clear; Standards notes optional post-GREEN
test-step duplication cleanup. Native38202667461 at3b5933d reaches genuine
missing-reorg RED4reported/3PASS/1fail108.245s at156/20s after actual alternative-
height request/native zero and priorIncluded/Unknown gates. Root full separateXML
Unknown/lastseen/zero/3.25/originalowner/Retry; originalPNG tool strip qualified.
Actual two482B Fee-only alternatives independently pass pinned pure WASM/Elliptic
creatorSig/hash/Rust recomputedroot/fullroundtrip and exact2000002->2000003 parent
link; neither contains the payment. Eight hashes in reorganization-ui-red/android17.
No consensus/finality/real-money claim. Minimum shared bounded HTTP current
inclusion+covered-displacement observer and qualified reorg UI now implemented;
original historical proof/reservations unchanged/no automatic frame. Debug/test
compile and same unchanged-test native GREEN pending.

Minimum reorg debug/test assembly passes2m56s; one original hash in reorganization-
ui-build. Bounded source/evidence review and same unchanged-test native GREEN
remain pending. Read-only foreground/auth audit independently checks parent#1:
Home-as-revocation is not an agreed requirement; lifecycleScope persists stopping
and Android10 credential UI can notify success before finishing. No blanket stop
cancellation or new policy is added; actual Home/callback ordering unmeasured.
Official sources: [lifecycle coroutines](https://developer.android.com/topic/libraries/architecture/views/coroutines-views),
[Android10 credential source](https://android.googlesource.com/platform/packages/apps/Settings/+/refs/tags/android-10.0.0_r1/src/com/android/settings/password/ConfirmLockPassword.java#441).

Both bounded reorg production axes through2a1e2b0 clear. Unchanged nativeGREEN
38203874802 at2a1e2b0:ordinary4/81.001s, separate pending death4775->4887/cold1/
10.186s/cleanup1/.055s,6/3/1/core66/release/lint. Root firstoriginalfullyviewed,
secondtoolstrip byte-identicalfullPNGdf59e385...; separate2XML/passgates qualified
reorg/identity/backend/no currentIncluded/Unknown/Pending/reserved0/no frames
through recreation. Earlier positive missing-block UNKNOWN still passes. Actual
two482B alternatives independently pass pinnedpureWASM/Elliptic/hash/Rustroot/
roundtrip/exactparent.16hashes in reorganization-ui-green/android17. Next sole
cycle exposes distinct literal fresh4.15change at2000002/ordinal1/index1/type0
while exact original inclusion returns, requiring a fresh0.25/fee0.1 recipient
review with no authenticated send. Product unchanged; test build/native missing
form RED pending. Post-GREEN reopening helper extracts actual repeated native
steps per optional Standards judgment, preserving all original gates.

Fresh change-form test APK compiles1m22s/one hash in change-form-test-preparation.
Product untouched; bounded source/evidence review and native missing-form RED
remain pending. No new-change spend/cancellation/reservation wire claim.

Change tracer Spec0/Standards source0,2stale doc sentences corrected: completed
reorg removed from current next-list; successful1m22s compile acknowledged in
reorg GREEN README. Native38204586012 atf3ba43a reaches genuine missing Recipient
field RED at188:4reported/3PASS/1failure87.681s, after actual change snapshot/4.15/
Included/noRetry and earlier full status gates. Root full original PNG/separate
XML confirms4.15 and absent new fields/review;4hashes in change-form-ui-red/android17.
Minimum form availability now allows new review alongside old records when fresh
nonreserved availability is positive. Funding/authorization/store/send path
unchanged. Debug/test build and same unchanged-test native GREEN pending.

Minimum new-form debug/test assembly passes1m30s/one original hash in change-form-
ui-build. Only form visibility/Review availability changes for saved records with
known positive nonreserved funds; all funding/auth/store/send code unchanged.
Bounded source/doc-fix review and unchanged native new-review GREEN pending.

Both bounded form/source/doc-fix axes through880f080 clear. Native38205158508
at880f080 reaches exact change-funded0.25/fee0.1/independentrecipient/owner review
and zero frames, then cleanup fails at199 because Done matches two stacked
dialogs. Ordinary4reported/3earlierPASS/1failure89.933s; no successful completion
or actual death follows. Root full reviewed originalPNG/separateXML confirms
review,6hashes in change-form-fixture-interruption/android17. This is review-only,
not authenticated change spending. Fixture scopes Done to the confirmation
dialog, retaining every acceptance assertion. Corrected build/runtime pending.

Dialog fixture instrumentation APK compiles1m28s/one hash in change-form-dialog-
fixture. Production unchanged; corrected native completion pending.

Corrected unchanged-product native38205777578 atf7d04ce PASSES ordinary4/86.001s
plus separate existing pending death4816->4926/cold1/10.263s/cleanup1/.055s,6/3/1,
no assumptions/core66/release/lint. Root full new-review PNG/separate XML agrees
with native exact0.25/fee0.1/recipient/owner and fresh4.15/currentIncluded gates;
zero frames before/after qualified Done dismissal. All prior status/recreation
checks remain passing. Ten indexed hashes in change-form-ui-green/android17.
This is review-only, not authenticated change spending. Both bounded selector
axes throughf7d04ce clear. Next sole native cycle receiving QR at accepted app
seam: isolated public literal address, actual failed balance request, full copied
address and displayed-raster decoding through ZXing before/after recreation.
No network condition gates receiving. Same-library raster decoder is not a
second independent implementation or physical camera acceptance.

QR initial draft test build1m52s, inspection corrected guessed balance label and
image-description screenshot text wait before dispatch; corrected build1m23s.
Two hashes in receiving-qr-test-preparation. Test-only ZXingcore3.5.4; product
unchanged, no QR view/native RED or raster decoding acceptance yet.

Both bounded QR tracer/evidence axes throughd1b9559 clear. Native38206389868
atd1b9559 reaches genuine missing QR atPreview299:5reported/4PASS/1failure98.838s
after actual503/unavailable/full literal receiving address/clipboard gates. Root
full originalPNG/separateXML confirms noQR;4hashes in receiving-qr-ui-red/android17.
Minimum view adds full-address QR after Copy, independent of balance connection,
with ZXingcore3.5.4/H/four-module quiet zone and opaque blackwhite pixels at actual
192dp density-derived dimensions. Cache tied to identity/density; no resampling
filter, logo or external service. QR payload is raw address, matching pinned A/B
[deposit](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/ui/saito-crypto/overlays/deposit.js#L75).
[Official encoder](https://github.com/zxing/zxing/blob/zxing-3.5.4/core/src/main/java/com/google/zxing/qrcode/QRCodeWriter.java),
[release](https://github.com/zxing/zxing/releases/tag/zxing-3.5.4),
[publisher license](https://raw.githubusercontent.com/zxing/zxing/zxing-3.5.4/LICENSE).
Core is maintenance-only, not an actively developed SDK. Original license is
retained in app assets; debug/test build/native raster GREEN pending. No physical
scanner, dark-theme or independent-second-decoder claim.

QR debug/test assembly passes2m59s; debug APK contains exact tracked attribution
and complete original publisher-license suffix. Two hashes in receiving-qr-ui-
build. Unchanged-test native raster/reopening GREEN and bounded source reviews
pending; no release-runtime/physical scanner/dark-theme/whole acceptance claim.

Both bounded minimum QR source/evidence axes throughf62caad clear. Unchanged
native38207157113 atf62caad PASSES ordinary5/73.267s plus separate existing pending
death4729->4893/cold1/9.25s/cleanup1/.056s:7/3/1,no assumptions,core66/release/lint.
Actual503/unavailable/full literal address/clipboard and displayed-node raster
decoding repeat before/after Activity recreation; guarded complete-wallet restore
is part of the successful case. Twelve indexed hashes in receiving-qr-ui-green/
android17. Root full both originalPNG/separate2XML: first clear whole QR view,
reopened ordinary Android clipboard overlay across lower white QR boundary/footer;
both separate XMLs record overlay. Nonatomic captures, no timing-cause inference.
Decoder checks the displayed Compose node, not composited device screenshot; no
second-decoder/physical-scanner/dark-theme claim. Existing monetary/status/recovery
checks pass; no new QR-related transmission or private material.

Next sole native tracer: actual unsecured emulator, live selected-peer review,
Authenticate and send must explain screen-lock setup for transfers, without
sending/reserving/queuing. Current product incorrectly reuses export-specific
setup text. After correct setup message, test requires app foreground/unsecured
state, closes/reopens transfers and observes fresh7.5/noPending/zero frames.
Product unchanged; test build and missing-transfer-copy native RED pending.

Missing-lock setup tracer APK compiles1m27s/one hash in auth-setup-test-preparation.
Product unchanged; bounded tracer/evidence review and native RED pending.
Current handoff consolidated around latest state, canonical chronology retained.

Both bounded tracer/evidence axes through615cbcc were clear, but native38207970800
stops before the copy assertion:6reported/5PASS/1failure85.896s atPreview218,
unsupported controlled-server receipt guard after review-only authenticate/use.
Root full originalPNG/separateXML shows exact6.25/.1/recipient/owner review with
no setup message requested. Four hashes in auth-setup-fixture-interruption/android17.
Source verifies the signed server challenge then enqueues the response before
immediate cancellation; receipt is not a guaranteed authenticate return condition.
No particular scheduler/cancellation cause is inferred. Test-only correction
observes actual successfully sent signed challenge instead; all original product
acceptance assertions remain. No product change or genuine setup-copy RED yet.

Corrected setup tracer APK builds1m27s, one hash in auth-setup-fixture-build.
Product unchanged; corrected native RED and bounded fixture reviews pending.

Both bounded corrected fixture axes throughc513197 clear after the current
handoff build sentence correction. Native38208760225 atc513197 reaches genuine
copy RED atPreview223:6reported/5PASS/1failure98.927s. Actual unsecured device,
exact6.25/.1/recipient/owner review/signed server challenge enqueue/zero type4
gates pass before Authenticate and send; original fully viewed PNG/separate XML
show export-specific setup guidance instead of transfer wording. Four hashes in
auth-setup-ui-red/android17. No setup/reopening completion or later death stage.
Minimum fix uses a dedicated transfer_auth_setup resource only for this branch;
export copy and all authentication/funding/store/send logic unchanged. Build and
unchanged native GREEN pending.

Minimum setup resource debug/test APKs compile2m36s, one hash in auth-setup-ui-build.
2026-10-12 05:47 UTC bounded read-only/no-redirect verifiedHTTPS host GETs to both
approved origins return options genesis_period80640 and identical92-byte fresh
height1112506/cf5320... zero-row snapshot for public unsafe scalar2. Sanitized
reports/probe3hashes in current-public-endpoint-read. Dated host schema readiness,
not native publicUI/positive funding/block-route/consensus/admission. No private
wallet, production writes or real funds accessed. Native setup GREEN pending.

Both minimum setup source/evidence axes throughdd2a98f clear. Unchanged native
38209545407 atdd2a98f PASSES ordinary6/71.565s plus separate existing pending
death4741->4880/cold1/8.664s/credentialcleanup1/.031s:8/3/1,no assumptions,
core66/debug/test/release/lint. Unsecured setup case observes exactreview, correct
transfer guidance, still-unsecured/appforeground, sameauthor/reopened fresh7.5/
noPending/zero type4. Root full2originalPNG/separate2XML, reopened normalIME
over lowerform/footer qualified. Twelve indexed hashes in auth-setup-ui-green/
android17. This setup case is not itself the separate pending-death driver.

Post-GREEN narrow public-UI field-entry helper shares the actual repeat in three
prior journeys; assertions/authentication remain local. Next one native tracer
removes actual HTTP funding after valid6.25/.1/recipient/owner review, uses real
systemPIN, observes fresh options+empty snapshot, demands truthful funding/
connection guidance with zero type4, then restores funding and requires sameauthor/
reopened7.5/noPending/no automatic queue. Existing safety should already reject
before reservation; current generic copy incorrectly blames server availability.
Product unchanged after setup fix; test compile/native result pending.

Funding loss tracer APK compiles1m22s, one retained hash in funding-lost-test-
preparation. No product delta; native result and bounded tracer/evidence reviews
pending. Prior setup GREEN evidence retained with exact indexed8/3/1 counts.

Both bounded tracer/setupGREEN axes throughcfab5a6 clear. Native38210314527 at
cfab5a6 reaches genuine failure-guidance RED atPreview290:7reported/6PASS/1failure
102.77s after exactreview/realPIN/fresh phase options+empty snapshot/healthy peer/
zero type4. Root original fullPNG/separateXML shows server-availability guidance
although actual funding is gone. Four hashes in funding-lost-ui-red/android17.
No restored-funding/reopening/no-reservation completion or later death stage.
Minimum product change only updates transfer_send_failed funds/connection text;
XML syntax parses locally. Hosted same-test GREEN includes complete compiler/core/
release/lint checks; no duplicate local APK build is claimed for this string-only
change. Funding/auth/store/send logic unchanged.

Both funding-copy axes through5a87b1f clear. Unchanged native38210905877 at5a87b1f
PASSES ordinary7/113.703s plus separate existing pending death5013->5141/cold1/
9.994s/cleanup1/.052s:9/3/1,no assumptions,core66/debug/test/release/lint. Actual
review/realPIN/freshlost funding/zero type4/truthfulguidance then restored7.5/
sameauthor/noPending/zero frames prove no false reservation or queue. Root full
2originalPNG/separate2XML; reopened normalIME overlays lowerform/footer. Twelve
indexed hashes in funding-lost-ui-green/android17. Separate pending death remains
distinct from this funding failure case.

Next sole tracer faults a newly owned one-byte orphan transfer pending file ONLY
in the guarded .dev emulator, after validreview. RealPIN/actual recovery warning/
appforeground/zero type4 must precede disabledAuthenticate; recreation must retain
identity/warning without review fields/actions or automatic sends. Restoring only
the owned known-empty fixture baseline then reopening requires fresh7.5/noPending/
no frames. Primary-safe exact-owned-fault cleanup and PIN cleanup remain guarded.
This is not a product policy to delete lost real candidates for recovery. Current
storage layer already blocks transmission; UI leaves Authenticate enabled and
can show new review fields after load failure. Product unchanged. Hosted compiler/
source checks gate native RED; no separate local test APK build claimed.

Both bounded tracer/prior funding GREEN axes through8b65fa2 clear. Native
38211648332 at8b65fa2 reaches genuine action-state RED atPreview348:
8reported/7PASS/1failure119.37s. Actual exactreview/realPIN/recoverywarning/
appforeground/healthy peer/zero type4 precede enabledAuthenticate failure.
Root full originalPNG/separateXML confirms warning and enabled button. Four
indexed hashes in storage-recovery-ui-red/android17. No completed ordinary
runner, blocked reopening/restoration or later pending-death stage is claimed.
Minimum UI fix gates Authenticate/Retry/Review on readable records and hides new
fields during recovery. Store/auth/funding/reservations unchanged; no candidate
deletion recovery policy. Hosted compiler/core/release/lint and unchanged native
GREEN pending; no duplicate local APK build claimed for this small UI delta.

Unchanged storage GREEN38212349387 at8879c82 PASSES ordinary8/126.952s plus
separate existing pending death5061->5154/cold1/10.007s/cleanup1/.052s:
10/3/1,no assumptions,core66/debug/test/release/lint. Root full3originalPNG/
separate3XML; disabled action is proved by Compose and action-parent, not enabled
text-child XML. Reopened warning/Done clear, ordinary IME below; restored form
7.5/empty fields but IME overlays lowercopy/footer. Exact owned empty fault repaired,
sameauthor/noPending/zero frames. Fourteen indexed hashes in storage-recovery-ui-
green/android17. Both bounded minimum fix/RED axes0; GREEN evidence review pending.

Next ONE native journey uses the independent pre-existing scalar1 signed candidate,
real encrypted reservation and exact original7.5 inputs. Owner->Official->recreate
Official->Owner must retain0available/pending/originalserver; Retry disabled away
from original backend, noautomatic frames. One controlled HTTP origin is shared,
logical persisted backends/binary endpoints are separate; not real cross-host proof.
Actual SAF imports scalar2 then scalar1 after fresh controlledzero+explicit backup
skip+riskack; outgoing transfer-work warning is required. Incoming sees no old
candidate; restored original wallet must regain pending/reservations/backend without
send. Primary-safe full opaque prior-wallet restore and exact-owned SAF cleanup.
No auth grant/send bypass or live funds. Native existing behavior may already pass;
no artificial RED required. Harness ordinary budget240s now includes this additional
bounded two-import journey (prior8cases126.952s); no failed-timeout retry rationale.
Hosted compiler/source/native result and bounded source/evidence reviews pending.

Both bounded storage GREEN axes verify14hashes/exact10/3/1/original3PNG/XML. Each
review finds2new isolation fixture issues: cross-UID direct provider-file write
and cleanup short-circuiting. Root cancels38213237595 at9f1f997 after successful
hosted Build/verify; both runtime steps skipped. One indexed sanitized run.json in
isolation-fixture-review-stop, no native RED/permission reproduction claimed.
Corrected harness provisions only literal unsafe1/2 complete default backups under
providerUID; testpositivestat beforeSAF/hashguarded cleanup. Wallet restore and each
owned file cleanup attempted independently, primary/suppressed errors preserved.
No product/acceptance change. Corrected source/evidence review and native pending.

Corrected isolation GREEN38213646885 atda64451 PASSES ordinary9/142.677s plus
separate existing pendingdeath5255->5353/cold1/9.42s/cleanup1/.043s:11/3/1,
no assumptions/core66/debug/test/release/lint. Both256-byte provider fixtures
positively observed; exactknown hashes/independently attempted filecleanup/full
prioropaque walletrestore complete. Both correction axes have0residual hard/Spec
findings, one optional repeatedcleanup judgment. Root full6originalPNG/separate
6XML: native0/Pending/3.25/originalserver/enabledowner-disabledawayRetry then
incoming0/emptyform/nooldPending and restoredoriginalPending. Allfooter captures
unobstructed; selectedbackend header is hidden bydialogs, established byruntime
header/persistedselection gates. SAFimports proved bynativejourney, not separate
importPNG. Twenty-one indexed hashes in backend-wallet-isolation-green/android17.
One sharedHTTPorigin qualified; two logical/binarybackends. Noauth/send/livefunds.

Post-GREEN review refactor moves the identical independent primary/suppressed
cleanup loop from Transfer/Username/Search tests into one narrow shared native-test
helper; allguards, ownedoperations and assertions remainlocal/unchanged. No new
cleanup framework or product code. Next read-only integration extends the existing
ONE opt-in OwnerLiveFeedTest after its authenticated livefeed/searchchecks: actual
selectednativebalance0/full receivingaddress and funding0(no pending) through
realHTTPS, no debugoverrides/review/auth/type4. Both backend runs use freshly
cleared isolateddev wallets; no positivefunding/blockroute/inclusion/admission claim.
Returntofeed/sameauthor/backend preserved. Harness collects balance/funding original
PNG+XML and sanitized public-wallet metadata, failurepair optional; existing1test
runnercount retained. Hosted compiler/core/release/lint and final same-source
fullAndroid10/fullAndroid17 with public_feed_read=true will gate acceptance.
Whole-ticket two-axis source/evidence review pending; no duplicate localAPKbuild.

Final whole-ticket source review identified a stale current-handoff label for the
older funding-loss run: relabeled historical, actuallatest isolation11/3/1 retained.
The older post-#22 next-ready note now points to Currentwork rather than falsely
saying #23 unclaimed. Documentation only; af7ed3b production/test/harness inputs
and currentfull10/full17 dispatches are unchanged. Final reviews still pending.
