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
