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
source `6cc37e56cca856fff937c26f91fafa775a58d276`. The implementation and actual
send/reconciliation still need their native verification.

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

Finish durable transfer recovery, the native review/authorization/inclusion flow,
rental path/delegation handling and current access observations. Complete native
coverage on both SDKs, inherited upload/read regressions, viewport checks and
independent Standards/Spec review. Update115's frozen catalogue additively before
its full-app proof. No partial successful tail counts as ticket acceptance.
