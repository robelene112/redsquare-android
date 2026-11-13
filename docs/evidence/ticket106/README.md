# Existing Vault files and explicit offline copies, issue106

Source `84a95ce073055cfa5a67e96fdbcc7ee6948671b5`, review base
`e290c5bdc185ff212e5b691c3cfa91d5a0c40c33`. This completes the reading slice
of [spec105](https://github.com/robelene112/redsquare-android/issues/105).
No new beta is published. The combined release remains ticket115.

Native Vault discovers standard existing keys from the selected node's NFT
holdings, authenticates their metadata and file commitment, and retrieves the
exact signed file. New retrieval observes current ownership. Explicit encrypted
offline copies survive recreation, work without the node, export through Android
and can be removed locally. Wallet/node namespaces, bounded 20-key pages, readable
filenames, storage usage and shared navigation are preserved. Missing service,
remote access failure and local storage/export failures have distinct feedback.
Temporary export cleanup has a serialized retry action.

## Verification

- All 156 JVM tests pass, including NFT-only wallet import protection, the
  independently signed 4 MiB payload and the reference's parameterized data URI.
- [Android10 run 43413241014](https://github.com/robelene112/redsquare-android/actions/runs/43413241014)
  and [Android17 run 43413324551](https://github.com/robelene112/redsquare-android/actions/runs/43413324551)
  each pass the entire focused set: 10 tests, four classes, all 12 width/theme/font
  cells and byte-exact 4 MiB export. Both checked-out source files match 84a95ce.
- [Original artifact receipts](focused-runs.json) record API-verified ZIP digests,
  byte-exact extracted originals and the exported content hash. Source logs,
  ZIPs, native hierarchies, configuration observations and captures are retained
  outside Git at `/mnt/storage/redsquare-android-artifacts/vault-store-planning`.
- Existing Android10 import run 43410429532 passes all7 import tests and its
  replacement-risk test at 65e383aad. Later product edits are Vault-only;115 still
  requires complete shared-source regression and signed-update continuity.
- Independent Standards and Spec source reviews report zero remaining findings
  through 84a95ce, including the test corrections and their unchanged bounds.
  Rendered normal saved-state and 320/412 dp large-text controls were inspected.
  The initial key capture caught the start of a reveal; it is not settled-ink
  evidence. This is not a new frame-timing or physical smoothness measurement.

Original negative results remain retained: the immutable 7fe59 missing-module
run43399934924; the NFT balance/import regressions; signed-file commitment and
4MiB/parameterized-file rejections; and viewport failures in43410422397/
43410425804 and43411878601/43411882418. The first pair incorrectly awaited Home
while Vault was selected. The second pair tapped Vault below the 200% text fold.
The corrections retain all 12 cells, 48 dp targets, 5s destination wait and original
viewport recovery limits. Three initial434102* dispatches failed checkout because
an abbreviated source ref was supplied; no app tests ran in those dispatches.
Earlier passing partial classes are not assembled into acceptance.

## Boundaries

The pinned official source is 0db0062d873369dc013046ed77c97410b5683b64 and the
independent codec/signature/script-hash runtime is saito-wasm 0.3.15. Fixtures use
published unsafe identities and synthetic tuples, not funded consensus mints.
The owner node's observed saito-js package is 0.3.2; do not call it 0.3.15.
Read-only discovery found Vault at saito.io, but not at the owner node.
No node was activated or changed and no public financial transaction was sent.

Native files are bounded at 4 MiB versus the reference browser's 100 MiB. Existing
signed-envelope/peer limits remain 8/16 MiB. Device files use encrypted 2 MiB parts
with a metadata commit and digest. Remote storage remains readable by its
operator. Expiry/transfer and local removal cannot recall exported files.
Standard uploads, transfer/custom/rental rights and Store follow in 107–114.
Physical/OEM, live provider/node and Play acceptance remain open under 36.
Keep 1/36/55/84/86 open. Preserve the canonical checkout.
