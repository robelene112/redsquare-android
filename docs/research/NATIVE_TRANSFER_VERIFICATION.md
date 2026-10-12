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
