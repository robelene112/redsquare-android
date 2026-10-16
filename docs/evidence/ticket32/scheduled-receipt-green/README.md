# Scheduled social receipt acceptance

Bounded local[#32](https://github.com/robelene112/redsquare-android/issues/32) slice.
SocialPushWorker now retains opaque public routing references in WorkManager,
waits for connectivity and retries selected-Archive failures with10s minimum
exponential backoff. It bounds a receipt to7days and reuses the existing fresh
wallet/backend/permission/policy/signed-content/receipt-ledger checks. Cancellation
propagates; the verifier's own timeout is mapped to retry only if the parent stays
active. This last timeout path is not yet exercised by the current native fixture.
No message body or wallet secret is put into scheduler inputs.

| Check | Exact source / result |
| --- | --- |
| First attempted RED10 |67293afb /38979224052 FAILURE, earlier group editor assertion,10/11PASS82.607s; worker case unexecuted |
| Intended RED10 |ee1bc69b /38980190191 FAILURE, old11PASS83.64s; worker1FAIL1.88s, no-op finished instead of retrying |
| GREEN10 |c69a1896 /38981302625 SUCCESS, old11PASS84.608s plus worker1PASS12.005s |
| GREEN17 |c69a1896 /38981305637 SUCCESS, old11PASS96.847s plus worker1PASS11.792s |
| Tracer release/lint |ee1bc69b /38980190232 SUCCESS |

[Failed first attempt](../scheduled-receipt-group-observation-failure/android10/README.md),
[intended RED](../scheduled-receipt-red/android10/README.md),
[GREEN10](android10/README.md) and[GREEN17](android17/README.md)
retain actual conclusions and unexecuted tails. The group observation correction
explicitly changed annotated-value equality to exact full plain-text equality,
retaining the15s wait/final assertion and all persisted text/photo safeguards.
The prior failure's exact predicate-time cause remains unknown.

Review corrections retain the fixture-owned reference before enqueue acknowledgement
and gate installation restoration on actual execution draining after cancellation.
The worker holds the same mutex across its effects and checks cancellation before
starting. Enqueue returns the actual persisted unique-work UUID, including a pending
duplicate. The native case checks both pending and completed redelivery, with
unchanged OS postTime for the latter. Both corrected source reviews at1a62909d
report0 findings. Pre-review minimum local core/debug/test build PASS3m20s; the
small reviewed correction is compiled by both exact-source native runs.

Root viewed all6 new GREEN originals and parsed all6 XMLs. Actual native assertions
include no Activity, same full wallet/backend, zero outgoing writes and scoped
restoration. The fixture uses an independently signed parent and a fresh native
reply; it is a scheduler/Android-delivery observation, not new independent protocol
compatibility proof. Each core report confirms114/0/0.

This accepts local scheduled processing/retry/redelivery only. Whole#32 remains
OPEN: native timeout observation, process recovery/autonomous wakeup, actual
Firebase registration/transport, viable integration for both backends and integrated
acceptance remain. No production deployment, new beta or Pixel result. Source
minimum38981302496 is still pending at this record; do not infer release/lint from
the native runs. Public signed beta24 is unchanged.
