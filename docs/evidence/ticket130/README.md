# Manual received-file retention and removal, #130

Parent [#117](https://github.com/robelene112/redsquare-android/issues/117),
owner-approved enhancement. Review base: `30bb852d4109c64a61e749d59c615b401a62df3f`.
Both independent Standards and Spec axes accept this source and complete
relevant native evidence with the stated operational limits.
All accepted source/native checks execute exactly
`1e5066800bf5a48eb72693266fd9d9d170d525c1`. No fixture/source bridge is needed.

## Delivered behavior

Settings → Storage opens Received files for the selected wallet/node. Received
copies stay until deliberate removal, including after export and app restart.
Each row shows its name, original size and actual encrypted payload storage;
the total counts only those retained payload bytes, including encryption overhead.
Chat records and external exports are explicitly excluded. Unknown/unreadable
legacy records produce a qualified partial total rather than an invented owner.
Legacy counterparties are recovered from existing wallet/history/runtime context;
new encrypted metadata also records its public account/node/contact attribution.

Remove requires a destructive confirmation explaining the loss of this app copy
and preservation of chat history/external copies. Active, pending and outgoing
transfers are excluded. A per-copy export lease prevents deletion during an
actual save; release is observable so the action becomes available automatically.
Deletion rechecks authoritative selected identity/node, writes a durable encrypted
removal journal and unlinks only that transfer's payload paths. Metadata and
historical delivery phase remain. Partial removal counts its actual remaining
bytes, stays locally unavailable and requires a deliberate retry. Chat never
claims a partial removal is complete or offers Save for unavailable bytes.

Disk scans/decryption/deletion run on IO, outside Compose/main-thread work. A
single bounded lazy list keeps the header, totals and actions reachable at large
text. Stable row keys, shared short content/item transitions, explicit 48dp action
heights and per-file accessible labels preserve the surrounding design language.
Saved task state contains public owner/node context only. A different context
cannot inherit a destructive confirmation or remove the previous account's data.

## Complete relevant native qualification

| Scope | Complete primary | Outcome |
| --- | --- | --- |
| Android10 | [43868275196](https://github.com/robelene112/redsquare-android/actions/runs/43868275196) | 21 normal completions; one intentional process-death start separately classified |
| Android17 | [43868275286](https://github.com/robelene112/redsquare-android/actions/runs/43868275286) | Same complete goals |
| Source/debug | [43868274993](https://github.com/robelene112/redsquare-android/actions/runs/43868274993) | Actual debug/test assemblies and lint; explicit cached core/gateway results |

Each SDK completes 19 ordinary methods in 16 groups plus two normal cold-process
recovery/restoration methods. The deliberate process-death start is not a normal
completion. Ordinary class bounds remain 180 seconds, original action goals
remain, and all six inherited test/host-process sources compare byte-for-byte
with the #129 base. Focused and ordinary full Fileshare include the new classes.

The lifecycle journey uses a genuine five-byte encrypted received payload with
an independently known 41-byte one-frame format. It exports exact bytes through
the actual controlled document provider, then preserves the legacy metadata
schema. Native Settings navigation verifies manual retention, Cancel, confirmed
removal, task recreation and the retained Chat placeholder with exact history
and unsent draft. Save is absent after removal; exported bytes remain exact.

The adverse journey holds the actual external-provider export opening while its
copy lease is owned. Removal is disabled and automatically re-enabled on release.
A filesystem unlink adapter then fails only the real encrypted inbox remainder:
41 payload bytes are removed while an eight-byte encrypted frame leaves 40 bytes.
The UI counts 40 and explicitly reports incomplete removal, survives recreation,
then removes the remainder only after a fresh confirmation. It verifies exact
external-export bytes, wallet options, three other-identity/node/active payloads
and a genuine encrypted Vault copy, including its decoded bytes, remain intact.
The adapter changes IO outcome only; business state and UI are not replaced.

Both 320/412dp ×100/130/200% text ×Light/Dark cover 12 layout cells per SDK.
Every named file/action/confirmation remains reachable, Remove/Cancel/Remove copy
have at least 48dp bounds, and scrolling/confirmation retain the five-second goal.
Actual UiAutomator-driven motion has no Compose test-rule replacement. Real
window recomposers observe 0/1 system duration scales, and retained frames show
immediate zero-motion removal versus a normal fade. Both movies include the
before/confirmation/final states; settings restore independently.

Complete inherited regression coverage retains real picker/draft availability,
held preparation/acceptance, browser send/receive, actual notification Cancel,
unknown-length streaming, denied notifications, both original Chat file viewport
sweeps, actual process death/recovery/restoration, and larger unpaced receipt.
No public transactions, payments or node/provider operations are exercised.

## Source checks and exact originals

The current source result contains 224 core tests in 44 classes, zero failures or
ignored tests. Core and push-gateway tasks are explicitly FROM-CACHE. Debug/test
APK assembly and lintDebug actually execute; lint reports 0 errors, 95 warnings
and 3 hints. These are source/debug checks, not signed-release or whole-app proof.

All original logs, metadata and server-digest-bound ZIP/TAR archives remain at
`/mnt/storage/redsquare-android-artifacts/received-storage130`. All 1220 ZIP entries
and 1029 TAR members across 14 receipts compare byte-for-byte with extracted
originals. The packet index binds 873 copied files to their exact originals and
SHA256 values. Android10 has 137 ZIP entries/292 captured members; Android17
126/322; source55/0. Structural PNG CRC/decompression and native XML checks pass.
The frozen assessor passes on its first execution for each accepted primary.

## Preserved unsuccessful observations

- 43859381299@4a4bd74b3 fails setup: the test created payload before initializing
  metadata, unlike genuine reception. The fixture order was corrected. This is
  not the native missing-feature reproduction.
- 43860362013@c910d4f5b is the actual adverse missing-feature case: native Settings
  has no Storage action. Original failed output and captures remain preserved.
- 43859308507 used a moving HEAD source argument and was canceled immediately;
  its metadata shows zero artifacts. It has no source/byte acceptance claim.
- 43862055997@55d6f9438 is canceled when a newer same-scope dispatch supersedes it;
  its actual canceled result and 54-entry artifact remain, not a native pass.
- 43862472008@b9fd2440d completes the initial lifecycle case but fails a viewport
  observer that forces a scroll parent for an already visible fixed total.
- 43863934379@3463aa21a completes lifecycle and partial/export cases but its
  viewport observer matches both background-feed and task lists. The selector
  now explicitly scopes the actual dialog. 43863934035 at the same source
  reaches the removed-file card while the native XML still says Loading messages;
  all original history/draft observations now share the existing ten-second goal.
- 43865645680/43865645927@8ae0d752e fail the added motion observation because a
  Compose test-rule context has no real-window MotionDurationScale. Verification
  moved to a separate actual-window native class. Both0/1 goals remain required.
- 43866901943/43866901706@8c665860c pass the preceding new cases but fail the
  storage row's real 40dp height against the 48dp goal. The product now explicitly
  sets 48dp minimums. Earlier passed prefixes are diagnostic only, not acceptance.
- 43865105811@ad45e48aa is a preserved intermediate successful source result,
  superseded by the current-source result after the48dp product correction.

No failed/canceled parent becomes successful and no passing prefixes are stitched.

## Limits and next delivery

This accepts the complete relevant owned-emulator/peer/provider/IO scope. It does
not certify physical/OEM behavior, live community/provider operations, funding,
server schemas, Play distribution or source-only Vault rental deployment. No
Vault files are hosted on the owner's node by this local retention feature.

Whole-app qualification, actual signed update and combined beta delivery remain
#131. Public beta is still 0.1.131 at this ticket boundary. Legacy release gates
and their existing issue owners remain open.
