# Vault access and ownership, ticket108

**In progress.** Ticket108 remains open. The signed combined beta belongs to115;
public beta127 has not changed. Review base is
`acf61b62e5370afa7afc16bc6e2eeabc82db1422`.

## Confirmed slices

The custom-file read tracer first failed on missing **Access file** in
[43479123027](https://github.com/robelene112/redsquare-android/actions/runs/43479123027).
At source `fbfdc0da1a0f20e38c524feacd76d6bc77b98c50`, the complete
wrong-input, correction, offline-copy and exact export journey passes on
[Android17](https://github.com/robelene112/redsquare-android/actions/runs/43483542857)
and [Android10](https://github.com/robelene112/redsquare-android/actions/runs/43484168280).
The original captures were inspected; artifact and capture members were compared
byte-for-byte. Digests and counts are in [focused-runs.json](focused-runs.json).
The expanded validation/account checks require their own later verification.

The access-preset native tracer fails on the missing **Change access rules**
action in [43484668804](https://github.com/robelene112/redsquare-android/actions/runs/43484668804),
source `2fb7b5522a474274e2d0bceaae595f84318602a1`. At source `586a6db22079daddd9989f946e2297efbf139c5c`,
[Android17](https://github.com/robelene112/redsquare-android/actions/runs/43486418905)
reported success, but its
[Android10 counterpart](https://github.com/robelene112/redsquare-android/actions/runs/43487982290)
found the modal still open at the later header-ink check. The original tracer
could observe the underlying review before modal disposal. It now requires the
selected clickable preset, actual disappearance of the rules modal and returned
review within the original5s before proceeding. Neither old run constitutes
complete preset acceptance. The corrected three-case advanced diagnostic then
passes on both SDKs at `cfb23950d209d886a1e868ff40424d4eca5639e6`:
[Android10](https://github.com/robelene112/redsquare-android/actions/runs/43489430460)
and [Android17](https://github.com/robelene112/redsquare-android/actions/runs/43489426106).
Each includes actual modal dismissal/selection, numeric/invalid custom source
checks before provider requests, corrected unlock/offline/export, and the encrypted
whole-key reservation/reopen/uncertainty case. All65/67ZIP and22capture members are
byte-exact. This is a complete three-case diagnostic, not whole-ticket acceptance.

The permanent transfer review tracer fails on missing **Transfer key** in
[43491190725](https://github.com/robelene112/redsquare-android/actions/runs/43491190725),
source `6cc37e56cca856fff937c26f91fafa775a58d276`. The four-case advanced diagnostic then passes on both SDKs at
`73f8b694e0d04258ff1b06cc4e2bf295f82e6dc3`:
[Android10](https://github.com/robelene112/redsquare-android/actions/runs/43493658324)
and [Android17](https://github.com/robelene112/redsquare-android/actions/runs/43493652504).
It includes whole-quantity/deposit/recipient review, closing and reopening the
reserved candidate, and explicit disposal of an unsent candidate without a
broadcast. The original rendered review was inspected. Actual authenticated
send/reconciliation is not accepted by that review-only journey.

The next native tracer uses the real Android credential prompt, an owned peer
that receives the signed transfer and disconnects, Activity recreation, and a
read-only recovery check. Its synthetic block re-leafs the received payload and
retimes the independently verified WASM block layout with a published fixture
creator. It is a controlled selected-node observation, not funded consensus or
production-provider acceptance. Both cases in that class pass on
[Android10](https://github.com/robelene112/redsquare-android/actions/runs/43496017808)
and [Android17](https://github.com/robelene112/redsquare-android/actions/runs/43496013421)
at `48c79b2e899d6f0442c395c33336dad877fdb1ca` (same product source as73f8b694e).
Exactly one received signed transfer is retained across recreation and then
recognized in the supplied selected-chain block by read-only checking. The old
current-access control disappears afterwards.

The next authentication-context tracer fails at
[43498122200](https://github.com/robelene112/redsquare-android/actions/runs/43498122200),
source `6c945c2f72b12f7c8b774b67926382af8c802792`: changing account during the real
PIN prompt produces a generic ownership warning instead of explaining the
changed account/node. The other two native cases still pass. The correction uses
a distinct context-change outcome, preserves the retained candidate and exposes
actual unreadable transfer storage at the library instead of enabling a new
financial action against unknown reservations. The complete three-case class (including both context changes) passes on
[Android10](https://github.com/robelene112/redsquare-android/actions/runs/43500319832)
and [Android17](https://github.com/robelene112/redsquare-android/actions/runs/43500315346)
at `d10dfa8cb3820a2984b3578327ad1a18b6c7c2b2`. Those account/node changes during
real PIN authentication emit no transfer and retain an explicitly discardable,
unsent candidate. All59/61ZIP and23capture members are byte-exact.

## Rental foundation

`test-fixtures/reference/vault-rental-reference.cjs` extracts and executes the
pinned official Store transfer hook and Vault message-builder bodies using
hash-checked acorn8.19.0. Actual WASM0.3.15 verifies signed paths, FIRST undelegated
selection, allowed/denied Archive owner changes, and creator/renter eligibility
before, exactly at, and after expiry. A later signed delegation preserves the
first renter. Body-builder adapters are not a fully initialized client or signed
request-wire oracle; the fixture states that limitation explicitly.

Three public core cases pass for signed-path reading/appending, role selection,
invalid signatures/file bindings/unsupported constitutions, actual checkout/read
message bodies and authentication of the original master-locked signed file.
Existing standard/custom file-reader cases pass with the shared parser. Current
Archive ownership and real provider acceptance still require runtime observation.
The native rental-master mint/upload/read/export and three12-cell zero-motion
dialog sweeps are implemented as owned tests and await native verification.
Store's actual order, checkout and fulfilment flow remains114.

Independent `saito-wasm@0.3.15` vectors pin upstream
`0db0062d873369dc013046ed77c97410b5683b64`. They cover script commitments,
original JSON numeric spellings, witness merging, bound creator/renter/expiry
presets, exact standard/custom/rental mint bytes and whole-key transfer bytes.
The numeric regression includes subnormal values; Java's mandatory two-digit
format is shortened only when the single digit round-trips to the same double.
Both old standard-mint vectors and their large-file facts remain intact.

The transfer reference executes the actual wallet factory and WASM full-block
serializer. Matching signatures and block hashes alone cannot hide a changed
original input coordinate. These are synthetic selected-chain compatibility
checks, not funded consensus transfers or production-provider acceptance.

## Remaining before closure

Complete the native rental-master and dialog viewport runs, exercise any uncovered
compatibility limits, and preserve current access observations. Complete native
coverage on both SDKs, inherited upload/read regressions, viewport checks and
independent Standards/Spec review. Update115's frozen catalogue additively before
its full-app proof. No partial successful tail counts as ticket acceptance.
