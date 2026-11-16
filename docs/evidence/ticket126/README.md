# Following progress after an author failure, #126

Parent: [#117](https://github.com/robelene112/redsquare-android/issues/117), ST8.
Review base: `3ed6aa2fcc940caea6caf285bf7e0b84bd499c1f`.
Product/source checks: `aa59eb9ed93df50694c686c8bb77349422fcbb5c`.
Final observation fixture: `0b88d268a06e687feac78aa6b461000bd93ab1d5`.

Native qualification is complete with the explicit observation erratum below.

Each followed author owns its bounded Archive reader and cleanup. A failed author
no longer aborts later authors. An unusable physical connection is retired before
the next author acquires a usable connection; shared wallet/endpoint/query policy
and author/stream continuations retain their existing limits. Local request
timeouts remain recoverable, while caller/enclosing cancellation propagates.
All Posts retains its previous outer failure handling.

Successful cards merge into retained history with the existing signed-author
filter, logical identity, cache and reading-position rules. Failed authors remain
retryable through ordinary polling and actual Retry. A partial pass shows
"Some followed accounts could not load", with details that loaded posts remain
available. Error state belongs to its feed cache/view. A partial pull does not
show Feed updated or network-recovery success. The selected node is retained.

## Relevant native and source qualification

| Scope | Whole successful parent | Methods | ZIP files | TAR files |
| --- | --- | ---: | ---: | ---: |
| Android10 | [43821550823](https://github.com/robelene112/redsquare-android/actions/runs/43821550823) | 14 | 96 | 43 |
| Android17 | [43820713096](https://github.com/robelene112/redsquare-android/actions/runs/43820713096) | 14 | 85 | 43 |
| Source/debug/lint | [43819331801](https://github.com/robelene112/redsquare-android/actions/runs/43819331801) | Core224 | 55 | 0 |

Android10 executes the corrected queued-callback observation at0b88. Android17
executes at `af3b0929e5255fcbc43c3a1834d09457ab7c4d17`; its original retained callback
trace independently satisfies the corrected metric through the reviewed bridge.
Both assessments report QUALIFIED_PASS_WITH_QUEUED_CALLBACK_ERRATUM from their first
report. Neither result is presented as an unchanged old-counter pass.

Each SDK completes14 normal methods/five groups with zero native/capture exits,
no skips and every inherited deadline. Both photo traces show20 reached cards,
exactly one queued oversized callback and the unchanged198-to198px cached anchor.
Both new failure orderings retain two cached posts and end with four individually
displayed posts, zero publications, and failed-author attempts increasing2to3
across manual retry. The accepted Android10 trace also records one constructed
oversized payload; the prior Android17 receipt predates that separate field and
retains its raw held/released callback records.

Source checks actually execute224 core tests across44 classes with zero failures
or ignored tests. Gateway is explicitly FROM-CACHE. Actual debug/AndroidTest
assembly and separate lintDebug pass: zero errors,95 warnings and three hints.
These focused checks execute neither release/performance qualification nor a
whole-app signed delivery.

The targeted method exercises both sorted-author failure orderings, separately:
a real connection drop for the first author, and an explicit peer rejection for
the last. Two cached posts remain, the healthy author's new signed post appears,
and a misleading archive author label cannot admit another signer's post. A real
pull retains partial failure without Feed updated; Retry after peer repair admits
the recovered author's post. Wallet, backend and zero-publication checks remain.
The connection-drop case records a subsequent successful callback on a different
authenticated physical connection.

The complete focused scope has14 normal methods across five classes: the new
method covers both orderings; all eight existing Following methods retain their
original180s class bound; the existing photo continuation, two deadline-recovery
and two cancellation methods remain additive. The unchanged photo assertions
retain all20 eligible signed cards, the existing2px cached-anchor tolerance and
original request/UI/class deadlines.

## Exact source bridges and observation erratum

Source/debug/lint uses aa59. Subsequent differences are confined to three
AndroidTest files. Product/core/resources/build/harness and owned-peer fault
behavior are identical:

- FollowingPreviewTest replaces exactly one expected partial-error label,
  "Node unavailable", with the approved truthful partial-account message. Its
  separate assertion after actual server closure/recreation still requires Node
  unavailable. Content, cached history, identity and all deadlines remain.
- FollowingPartialFailureTest waits for an optional list node within its original
  20s readiness predicate, rather than throwing before a newly selected cache has
  attached. The exact three-row goal and every subsequent assertion remain.
  Extra failure diagnostics retain only synthetic public request metadata.
- PhotoPagesPreviewTest corrects the observed overflow population. The original
  counter incremented when a response was constructed, including held bodies that
  were never queued. The corrected observation freezes queued reply/release events
  over the unchanged16MiB receive cap and retains constructed counts separately.
  Its fewer-than256-record check rejects a truncated observation window.

This is a reviewed queued-callback counting erratum, not an unchanged old-counter
pass. Numeric bounds remain one ordinary oversized callback, one through four for
minimum-page large rows, and zero for fitting multistream responses. No assertion
permits an additional queued oversized callback. All card, signer, cached-position,
identity, zero-write and time limits remain. A preparation-only response is not
claimed to have reached the app. The actual frozen callback trace is independently
recomputed for each accepted SDK; an older source can only qualify through that
exact reviewed bridge, never by assembling successful failed-parent prefixes.

## Retained failed and cancelled observations

Originals are retained externally at
`/mnt/storage/redsquare-android-artifacts/following-resilience126`, with source-bound
API metadata, full logs, server-digest-verified ZIPs, exact extracted ZIP/TAR bytes
and nonacceptance receipts:

- 43818161636@1614a654e reproduces the first-author starvation: the healthy result
  never completes the original20s three-row goal while the first author fails.
- 43819326016@aa59 passes the new two-ordering method, then the old partial-error
  label fails its original15s wait. This whole parent remains failed.
- 43819329042@aa59 fails the new predicate's immediate assertExists while the
  selected list has not attached. The20s wait is not extended by its correction.
- 43820283411@e133 is explicitly cancelled before acceptance after that known
  readiness correction; no successful prefix is reused.
- 43820280364@e133 completes the new method and all eight original Following
  methods, then fails the original constructed-payload overflow count. Its exact
  trace has two17,861,191-byte author responses held with queued=false and only
  one release with queued=true. That discrepancy motivates the retained counting
  erratum. The failed native parent remains excluded.



## Evidence and delivery limits

Both independent review axes report zero remaining source/native/packet findings
and accept both complete SDK parents and source evidence with the explicit queued-
callback observation qualification. All123 indexed copies and the exhaustive
125-file packet match original bytes and hashes. All236 accepted-parent ZIP files and86 TAR
capture files are verified against original archives and server digests. The
packet copies native logs/exits/capture statuses, photo/failure/recovery captures,
source receipts, the exact source bridge and all eight retained parent receipts.
Its exhaustive copy-index links every copied file to original bytes and SHA256.

These owned emulator/protocol/storage checks do not establish physical/OEM,
live community/provider or whole-app release acceptance. No production node or
provider changes or public transactions occurred. Public beta remains0.1.131 until
#131 completes integration, original release/performance/update goals and the
combined signed delivery. Continue127–130 sequentially, then131; external release
gates remain open under#36.
