# Scheduled social receipt: intended Android10 RED

Run[38980190191](https://github.com/robelene112/redsquare-android/actions/runs/38980190191)
FAILURE at ee1bc69b4dfb630a32b57e18229f59fcca336917. All11 existing social cases
PASS. The separate one-case scheduled-receipt fixture FAILS in1.88s at108:
Unavailable Archive receipt finished as SUCCEEDED. The deliberately nonprocessing
worker executed successfully without invoking the receiver, so it failed the
required retry behavior. This is the intended RED, not a scheduler crash.

Its later failed-read, pending-retry capture, recovery, visible alert, duplicate
postTime and saved-state/no-write assertions did not execute. Do not infer them
from this failure. Fixture finally performs cancellation/execution drain before
scoped restoration; no cleanup failure appears in the raw stack. There is no
independent restoration JUnit phase to count. Root viewed the original launcher
failure PNG and parsed its XML. Existing eleven cases retain their raw log, not
a duplicate copy of their previously reviewed screenshots. Full raw artifacts
remain under /mnt/storage/redsquare-android-artifacts/push32-durable-corrected-red-38980190191.

Core114/0/0 and selected native source/runtime setup passed. Android17 was skipped.
The failed prior attempt38979224052 remains separately retained and never reached
this case. The intervening fixture predicate explicitly changed from annotated
equality to exact plain-text equality, preserving all characters,15s wait, final
assertion and persisted-draft/photo checks. No runtime cause for that old mismatch
is established. Source check38980190232 at the exact tracer source SUCCESS includes release/lint;
its metadata is retained in source-check-run.json. Source checks and actual
transport acceptance remain distinct.

This proves only sensitivity of the controlled native scheduler/Android boundary
to missing processing. No FCM, killed-process receiver, backend gateway, Pixel
or complete#32 claim. SHA256SUMS covers retained originals/logs/metadata.
