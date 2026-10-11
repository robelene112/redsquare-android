# Ticket 20 paired final acceptance

Search posts and people is verified on Android10 and Android17. All nine native
Search journeys pass on each, including navigation, available offline history,
backend isolation, partial retry/paging, exact/known-prefix people, historical-name
qualification, save failure/repair, and retained author edits/deletions.

| Checkpoint | Source | Reported cases | Search | Public feed reads |
| --- | --- | --- | --- | --- |
| [Android10](https://github.com/robelene112/redsquare-android/actions/runs/38147131217) | 7b4dd14 | 169 PASS + one existing API30+ profile assumption | 9 PASS, 54.238s | Owner 10.869s; official 14.748s |
| [Android17](https://github.com/robelene112/redsquare-android/actions/runs/38145373388) | 6c489bf | 170 PASS | 9 PASS, 58.789s | Owner 9.678s; official 7.672s |

Each has44completion records/14actual process-death stages in the wider suite,
core55/0/0, debug/test/release assembly and lint, signed5-to-8 updates and cold
wallet/feed continuity. The nine Search Activity journeys do not establish actual
Search process-death. Public Search metadata checks on each backend/SDK return
ordinary100rows/90posts/0ignored and quotes4rows/0commentaryposts/0ignored. They
establish read compatibility, without exhaustive history or chain inclusion.

Each SDK folder preserves119verified hashes, original screenshots/hierarchies,
raw logs and independently countable totals. Root individually viewed15Search
originals and separately parsed15XMLs per SDK, plus two public feed originals per
SDK. Both final evidence reviews report zero findings or closure blockers.
The checkpoint READMEs retain their review-pending status as historical snapshots;
this paired summary and ticket19/REVIEW.md record completed final review.

Successful checkpoints have identical production/core/build/reference/scripts/
workflow and Search/public acceptance assertions. An existing posting import
fixture correction accounts for the code difference, permitted by WORKFLOW.md's
outstanding-SDK companion rule. Both prior failures remain retained separately;
the final pass does not prove the first scheduling cause.

No new phone APK, Pixel7 timing, arbitrary power-cut, actual Search process-death,
live transaction broadcast or external Registry readiness is claimed. Username
registration ticket19 remains OPEN on its demonstrated external prerequisite.
