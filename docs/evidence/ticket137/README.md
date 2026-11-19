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
