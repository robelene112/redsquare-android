# Android CI timing

Read-only investigation, 2026-10-09. Source inspected at `d54d9de1db7d1eaa169af5d93b914fd8467b8492`. No workflow, application, node, wallet or artifact changes; no runs dispatched. Durations below come from first-party GitHub Actions job/step timestamps, rounded to seconds.

## Measured cost

| Run | Scope | Java/cache setup | Runner SDK setup | Source build | Android 10 | Android 17 | Job total |
| --- | --- | --- | --- | --- | --- | --- | --- |
| [37943159613](https://github.com/robelene112/redsquare-android/actions/runs/37943159613), successful | Full | 25s Java + cache restore | 29s | 4m04s | 9m23s | 14m19s | 28m49s |
| [37960235937](https://github.com/robelene112/redsquare-android/actions/runs/37960235937), successful | Full | 36s Java + cache restore | 49s | 3m02s | 8m44s | 13m05s | 26m23s |
| [37982268510](https://github.com/robelene112/redsquare-android/actions/runs/37982268510), successful | Posting | 46s Java + cache restore | 41s | 1m50s | 1m53s | Skipped | 5m23s |
| [37984759300](https://github.com/robelene112/redsquare-android/actions/runs/37984759300), successful | Posting | 23s Java + cache restore | 30s | 2m04s | 2m02s | Skipped | 5m07s |
| [37985523535](https://github.com/robelene112/redsquare-android/actions/runs/37985523535), in progress when sampled | Posting | 1m10s Java + 31s cache restore | 1m23s | 2m34s | Started 20:19:24 UTC | Pending skip | Not complete |

The last run reached device testing 5m56s after job start, versus 3m02s in 37984759300. This extra 2m54s breaks down into 47s Java, 20s explicit cache restore, 53s runner SDK setup, 30s source build and 24s other setup steps. The timestamps demonstrate variation; they do not establish a cache, network or runner contention cause. Device stages include image installation, boot and all checks, not only instrumentation execution.

## Concrete scope and repeat-work issues

- [Workflow line 87](https://github.com/robelene112/redsquare-android/blob/d54d9de1db7d1eaa169af5d93b914fd8467b8492/.github/workflows/android-proof.yml#L87) omits `posting-android17` from Android 10 exclusions. That scope therefore runs both SDKs sequentially. Other Android 17 scopes correctly exclude Android 10. Fix this immediately or replace the growing exclusion lists with one validated scope-to-SDK mapping: `full` means both, suffix `-android17` means only 37.0, all other listed scopes mean only 29.
- Every push to main starts `full`, while an explicit `posting` dispatch uses a different concurrency group. Both builds can run for the same source revision. Recent [push 37985522981](https://github.com/robelene112/redsquare-android/actions/runs/37985522981) and dispatch 37985523535 began together; the push failed during its full source-build step. This confirms duplication, not the failure's cause. Consider making push checks source-only and reserving full runtime acceptance for explicit final dispatch. This changes automatic coverage and must be recorded; focused green still cannot close a ticket without full acceptance.
- The explicit legacy cache restore took 0–1s in the sampled runs. Removing it cannot explain or recover minutes. Java's Gradle cache plus the explicit current cache both restore overlapping caches, but the evidence does not establish that changing cache strategy would improve reliability or wall time. Defer that optimization.

## Minimal full-run parallelization

Keep one source-build job with existing core tests, debug/test assembly and full-only release/lint checks. Upload only its diagnostic APK, test APK and a SHA-256 manifest identifying `github.sha`; retain source reports separately. A dependent device job uses a two-element SDK matrix for full scope, a single element for focused scopes, `fail-fast: false`, and at most two concurrent jobs. Each checks out the same SHA, downloads this run's APK artifact into the exact existing `app/build/outputs/apk/...` paths, verifies hashes, then runs unchanged `scripts/ci-android-device` in its own hosted runner. Upload evidence under distinct names per SDK and fail the workflow if either required SDK fails.

This is supported by GitHub's [dependent job outputs](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/pass-job-outputs), [matrix jobs, failure handling and concurrency](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/run-job-variations), and [artifact sharing](https://docs.github.com/en/actions/tutorials/store-and-share-data). Separate runners preserve ADB isolation; two concurrent emulators in the current runner would require serial-specific rewrites throughout the harness and are a larger change.

Device jobs still need command-line tools, emulator, system image, Python and build-tools 35.0.0: `scripts/ci-review-update` invokes `apksigner` and `aapt`. They do not need a Gradle dependency restore or another source build. Keep KVM, emulator memory/core limits, scoped `.dev` mutations, controlled peers, process-death checks and signed-update checks intact. Public reads stay gated by `TEST_PUBLIC_FEED_READ`; focused posting exits before that separate read stage ([harness](https://github.com/robelene112/redsquare-android/blob/d54d9de1db7d1eaa169af5d93b914fd8467b8492/scripts/emulator-check#L93)). A true public-read input on posting currently does not add public reads, so do not claim it does.

From 37960235937, perfect overlap would save the 8m44s Android 10 stage. Extra device-runner setup and artifact transfer reduce that saving; approximately 18–20 minutes instead of 26 minutes is a planning estimate, not measured acceptance. Total runner minutes may increase. Queue limits and public-server variability can erase some wall-time benefit.

## Verification before adopting

1. Validate every declared scope selects exactly its intended SDKs, including push/default full, posting and posting-android17. Reject unknown scope values rather than accidentally falling through to partial checks.
2. Prove both device jobs consume identical source-SHA/APK hashes, retain separate evidence on failure, and neither failure cancels the other. Missing or corrupt transferred APKs must fail before install.
3. Run focused posting on each SDK, then one full dispatch with public reads enabled. Compare case counts, process-recovery stages, core/release/lint outcomes, both approved public reads, cold launches and signed updates against the current harness. A matrix success without this evidence is insufficient.
4. Measure observed end-to-end and per-stage timings after the change. Do not remove process isolation or weaken timeout/success gates merely to meet the estimate. Physical Pixel timing remains separate from emulator CI.

## Applied first correction

The next workflow change keeps source/core/debug/test/release/lint checks on
push and gates device stages to explicit workflow dispatch. Full Android10/17
acceptance remains required at ticket completion. It also excludes
`posting-android17` from the SDK29 stage. Device-job parallelization is still
a proposal, not implemented or verified. No emulator timeout or success gate
was relaxed. Verify the actual source-only push and later full dispatch.

## Split full scopes, 2026-10-10

Commit7dcea19 adds `full-android10`, mapping to the unchanged harness `full`
scope and excluding SDK37.0. Existing `full` still runs both SDKs sequentially;
`full-android17` still runs only37.0. This smaller option allows concurrent same-
commit full runs without the APK-transfer architecture proposed above. Each
runner separately checks core/source/debug/test/release/lint and its complete
device suite. It may duplicate build minutes; no byte-identical runner APK claim.
All28 declared scope SDK selections were checked, YAML parses and whitespace
checks pass. Both supplemental review axes0 actionable.

Full pair38056259800 (Android10) and38056261794 (Android17), same7dcea19 SHA,
was dispatched with public_feed_read=true. Actual both-SDK acceptance, cases,
recoveries, public reads and observed elapsed savings remain pending.

First split pair outcome:10 run38056259800 build PASS then browse7/8 with one
reply visibility fixture failure.17 run38056261794 was superseded/cancelled,
not accepted. Corrected final pair38056769062/38056770920 at the same4513c4b
SHA, public reads enabled, is pending. No measured full wall-time saving yet.
