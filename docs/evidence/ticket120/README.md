# Unfinished text recovery, #120

Parent: [#117](https://github.com/robelene112/redsquare-android/issues/117).
Review base: `ae7c5ae9c961750f2e15112c8cd757ed1e3ff13e`.
Current native source: `bbc3a1cf2dc46ebafdf652b6b3e91e677539deb9`.
Source/core/lint source: `3bb29ee769a6969e08d89c9e65de3b81e08e60b2`.
Their only differences are two AndroidTest fixture corrections: exact wallet
equality now has a private fixed failure message, and the actual pinned Retry is
checked visible/enabled and tapped without requiring a scroll ancestor. Product/
core/resources/build/workflow/scripts are byte-identical. Both source review axes
accept this fixture-only bridge, without changing goals or deadlines.
Both independent final source/evidence reviews accept #120 with zero remaining
findings. Complete relevant native verification is accepted with the qualifications
below. Final whole-app qualification and beta delivery remain #131 work.

Current post/reply/quote and biography text uses a separate Android Keystore
encrypted checkpoint for the exact wallet, selected backend and writing context.
It is anchored to the existing explicit draft. A periodic IO writer checkpoints
recent text during continued editing, without blocking input or waiting for a
shutdown callback. Active-editor ownership and generation retirement reject older
writers. A confirmed explicit draft operation clears the recovery checkpoint.
The original explicit-draft and signed/queued candidate formats remain unchanged.
Recovery never grants publishing permission or causes publication.

Normal recovery does not claim an explicit Saved draft. An actual checkpoint
failure exposes Warning/Retry beside the writing header and preserves current
editable text and previous work. ViewModel closeables retain the writer through
Activity recreation and close it when the owner is cleared, including cancellation
while initial loading is acquiring the resource.

## Actual adverse observations and intermediate checks

- Post red `43746535491`, source `6f20287ca639478bf66adab7d9657108fe8771e7`:
 49characters become an empty native composer in a new process after backgrounding.
- Post-only green `43747908540`, source `e225c5aa4b3e4ad738db860b21cc2d36dd660bf1`:
 the exact text returns with unchanged explicit draft, wallet/node and zero publications
 to the owned peer. Original/recovered PIDs3342/4163 are distinct.
- Bio red `43749242236`, source `9ba4ae405ff525eada1eda3dff1e26520fe8c56e`:
 the cold native Bio field is empty instead of the52characters entered earlier.
- Bio-only green `43750883200`, source `0fe820532852c4397e60a0fef28c5417a50e254f`:
 exact text returns in both the actual preview and editable field after new-PID
 recovery, without saving the explicit profile draft or publishing.
- Continued-typing red `43752110988`, source `9ea19ec0ea3f4e0f008026fe6f83ba24ae2b01aa`:
 after1.8seconds of native SetText updates40ms apart, the actual durable checkpoint
 still contains the pre-loop text. This assertion fails before deliberate death.
- Continued-typing green `43753348329`, source `e0c922325a6f7bb7d9a19fe753013ea0136e7919`:
 actual encrypted progress reads `Continuous 120 edit 37, still typing` after38
 updates/1832ms, then the final text also recovers in a new process. This establishes
 progress during native control updates, not physical keyboard performance.

These focused greens are intermediate observations, not final-source acceptance.
Complete originals and digest/extraction receipts remain outside Git.

## Review and current-source verification

Independent Standards and Spec source reviews accept bbc3a1cf2 with zero remaining
findings. Standards found a fixture cleanup gap: captured publishing-agreement
state was not restored after a failed assertion. The final fixture independently
restores and verifies exact base/bak/new/pending bytes or original absence while
preserving the primary failure. Both reviews confirm the correction.

Source/debug attempts `43755780634` atde402 and `43756299553` at84c were replaced
while incorporating these review changes. Their original logs/ZIPs are retained;
neither application assertions nor deadlines were retried or weakened, and neither
parent supplies acceptance. Source/debug `43756601784` passes at3bb:224 recorded core tests/44 suites, zero failures/ignored; lint0 errors,94 warnings and3 hints. Cache reuse is explicitly qualified in the source summary.

First complete composer17 `43758160749` at ef fails in the new storage-failure
fixture: it requested a scroll ancestor for the now-pinned Retry control. The
complete failed parent and151 raw files/222 capture members are retained, without
prefix acceptance. The correction checks the same real control is visible/enabled
and taps it, preserving all subsequent persistence and dismissal assertions.

Final complete composer includes eight separate process modes: post, post-saved,
reply, quote, profile, profile-saved, continuous and storage-failure. Each retains
the phase bound of 120 seconds, exact current wallet/backend, actual process termination,
distinct recovered PID, exact editable text, original explicit draft and zero owned
peer transactions. Three actual Android encrypted-storage methods add stale writer/
retirement and identity/node isolation, preserved unreadable bytes/read retry, and
unchanged signed queued candidate and publishing permission. Existing native
composer and full Social scopes retain these checks additively.

## Accepted complete relevant scopes

| Scope | Android 10 | Android 17 |
| --- | --- | --- |
| Composer and all new recovery checks |43759927503|43759927888|
| Profiles, including original video precondition |43759927652|43759927462|
| Agreement and queued Posts |43759927565|43759927747|

Every complete parent succeeds at exact bbc. Per SDK, composer completes eleven
ordinary and sixteen process methods, with eight separately verified intentional
starts. All eight current native text fields match their exact expected input and
all recovered PIDs differ from their original process. Original explicit Saved
drafts, wallet/node and zero publication in the recovery journeys remain required.

Profiles completes twenty ordinary and one process method on Android17, with two
intentional starts. Android10 completes nineteen ordinary and one process method,
with two intentional starts and exactly one inherited assumption:
`ProfilePublishingPreviewTest#newerProfileStillBlocksOlderRetryAfterPublicCacheSavingFailsAndEditorNavigation`.
Its unchanged source requires API30+ for the `.new` obstruction scenario; API29 has
a different AtomicFile backup strategy. No generic skip allowance or new exception.
Agreement/queue adds two normal completions per SDK, preserving original signed
candidates and deliberate approval. Across the six parents:99 normal completions,
20 verified intentional starts and the one named assumption,120 invocations.

Both independent reviewers freshly verify source/status, ZIP digest and full logs,
all653 raw files and688 captures, complete hash indexes and every source-bound
assessment assertion. Source adds55 indexed originals. Both review axes reproduce
the exact two-line source bridge and core/lint reports. `reviews-final.json` records
acceptance and qualifications; the external assessment tool and its hash are in
`assessment-tool.json`. Selected actual cold restore and failure/Retry screenshots
are retained beside the complete indexes; full originals remain external.

## Boundaries

Final whole-app qualification and combined beta delivery belong to #131. Public
beta remains0.1.131. Physical/OEM, live service/provider and Play gates remain open.
No production node/provider change or public financial write occurred. Whole
originals, temporary collectors and assessments remain external under
`/mnt/storage/redsquare-android-artifacts/unfinished-text120`.
