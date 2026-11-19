# Ticket137: functional Spaces

Status: implementation in progress. Parent134 and native navigationB are
approved. Prerequisites133/135 are delivered in beta142. This packet records
incremental contracts, not completion of137 or permission to activate a node.

## Library checkpoint, prospective runtime contract v1

Before functional native integration, run the owned MLS interface on these
three isolated emulator profiles with an exact source commit:

| Scope | SDK | Runtime page size |
| --- | --- | --- |
| `spaces-crypto` | 29 | 4096 |
| `spaces-crypto-android17` | 37 | 4096 |
| `spaces-crypto-android17`, `android17_16kb=true` | 37 | 16384 |

The required native identity is
`SpaceMlsRuntimeTest#actualJniAndKeystoreRecoverAdmittedHistoryWithoutWritingAnUnprotectedKeyring`.
One normal completion, zero failures, assumptions or skips, native exit0 and
the original180-second class bound are required. The capture bound remains30s.
The whole workflow parent must succeed, including source build and all core
contracts. Preserve each unsuccessful parent and its original artifacts;
passing prefixes cannot supply acceptance.

The original archive must retain the source checkout/build metadata, native
instrumentation transcript and exit code, capture status, built APK hashes,
and `spaces-mls-evidence.tar` containing `spaces-mls-evidence/runtime.json`.
Verify the archive digest and CRC before extraction. Compare actual installed
product and test APK hashes in runtime.json with the original build hashes.
Require the declared SDK/page size, an actual emulator ABI, positive encrypted
file and snapshot lengths, and the observed Keystore round trip and history
availability before/after the authenticated grant. No secret snapshot, private
key or message text belongs in that evidence.

The runtime case uses separate real synthetic wallet/MLS identities, an actual
native library call and Android Keystore file encryption. It demonstrates
serialized reload in one process. It proves no rendered UI, real node service,
distinct-process recovery, physical/OEM compatibility or whole application
acceptance. The ELF build independently checks all four ABI LOAD alignments
at least16384. Actual16KiB runtime success is a separate required observation.

Host contracts independently exercise available-history admission, a removed
member's inability to read actual future content while a remaining member can,
restoring an unjoined device without changing its package, and wallet/MLS leaf
binding. Bouncy Castle1.86 is a test-only independent RFC9420 implementation:
it joins the native Welcome, derives the exporter to decrypt an actual record,
and opens the native private history message. An unbounded foreign package
must be rejected even if its wallet binding is valid. These contracts do not
substitute for Android runtime execution.

## Remaining issue acceptance

The genesis journal and crypto source are an incomplete foundation. Still
required before137 can close: complete signed control/content ordering and
permission replay, durable application repository/outbox and migration,
explicit invitation/create/join native flows, two independent processes with
encrypted content and original operation recovery, denial/lost acknowledgment/
duplicate/reversed/wrong-scope cases, source service with isolated rehearsal
and readiness/rollback, and direct comparison with approved nativeB.

The [protocol](../../SPACES_PROTOCOL.md) and
[quality plan](../../quality/SPACES_QUALITY_PLAN.md) define the remaining
contracts. Existing Chat compatibility and replacement-device recovery must be
resolved explicitly. No live service, community file storage or release is
established by this checkpoint.

## Local foundation observations

Checkpoint20ff337a8 has independent source Spec review with zero hard findings.
Standards identified an interrupted toolchain installation recovery defect; the
follow-up uses per-component, archive-hash-bound completion receipts and adds
native-library outputs to JVM test inputs. Independent follow-up review has
zero remaining findings and five isolated installation/re-entry checks. The
strict JSON extraction duplication is a nonblocking locality tradeoff while the
journal is still growing. Upstream license files retain original bytes and
whitespace, as verified by their source manifests.

The complete local core suite passes47 classes/238 tests, zero failures or
skips, after declaring the native binary as a test input. The following actual
pinned-tool re-entry build passes the three Rust contracts and six affected JVM
methods. Original failed lifetime admission, initial compile/setup failures and
successful logs remain outside Git under the task's retained evidence root.
These observations precede, and cannot replace, the three required native
runtime parents. No functional137 acceptance is claimed.

## Runtime routing correction, prospective v2

Original parents44428040942/44428044844/44428048521 use source/workflow467ce2c5a.
The newly added focus choices were missing from the workflow's explicit SDK
exclusion lists, so those parents select both SDK steps. Preserve them as
incorrectly routed observations, not acceptance of the declared one-profile
parents. A two-condition workflow-only correction excludes the17 choice from
the10 step and the10 choice from the17 step. Existing scopes and all native
goals,180/30s bounds, evidence and tests remain unchanged.

Fresh parents use the exact same product/test source467ce2c5a with that corrected
workflow revision. Record both checkout and workflow identities independently.
The required profiles, original complete-parent rule, exact one native case per
parent, six affected JVM cases and three Rust contracts are unchanged. No failed
prefix or extra selected SDK supplies acceptance of a fresh parent.

## Accepted limited runtime checkpoint

Whole fresh parents44430860880/44430865020/44430869595 meet the unchanged
library contract for Android10, Android17 and actual16KiB Android17. The actual
installed product and test APK hashes match their respective original build
hashes. Both independent actual-evidence reviews report zero findings after
checking all original ZIP/TAR bytes and outcomes. See runtime-foundation for
assessments and original artifact receipts. Whole original ZIPs and build logs,
including the incorrectly routed earlier parents, remain under the retained
`spaces137-runtime` evidence root. No UI, Android process-loss, deployed-service,
OEM or whole137 acceptance follows from this limited checkpoint.

The subsequent source adds the signed invitation/admission chain and a new,
unactivated standalone service. Three affected journal cases and four service
cases pass locally, including real HTTP, exact receipt replay and an actual
hard-killed service followed by a different process. These are service/component
observations, not the still-required complete Android community journey.
