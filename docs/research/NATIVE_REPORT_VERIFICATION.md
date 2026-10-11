# Native reports verification

Ticket #22, review base9ecb97b. Parent #1 accepted seams1–4: actual Android UI
against an authenticated controlled binary peer; independent pinned transaction
validation; actual recovery/failure; cross-client source compatibility.
No additional testing boundary or production report is authorized or required.

First vertical slice: name the selected reporting destination before consent,
submit signed type12 application RPC only to that backend, accept correlated
handler acknowledgement without claiming moderator review or removing the post.
ReportPreviewTest is introduced before product changes. Local :app:assembleDebugAndroidTest passes; shell syntax/diff checks pass.
Device RED pending. An initial helper parameter-order compile error was fixed
before runtime testing and is not counted as a behavioral RED.
Source contracts: NATIVE_DISCOVERY_MODERATION_CONTRACT.md,
NATIVE_MODERATION_CONTRACTS.md, NATIVE_TRANSACTION_BROADCAST.md.
