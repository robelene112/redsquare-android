# Social push: smallest local seam, 2026-10-15

Read-only research for [#32](https://github.com/robelene112/redsquare-android/issues/32)
while root owns #41. Proposal only; this note claims no implementation,
background delivery or backend integration. No live service, configuration,
wallet/database access, probe, build or CI occurred.

## Existing usable code

[RedSquareFeed.readReplies/readMentions](../../core/src/main/kotlin/nl/davidengelen/redsquare/saito/RedSquareFeed.kt)
already verify signed posts. Mentions recognize bounded signed `data.mentions`,
exact public-key tokens and usernames independently reverified against registrar
proof. Addressing alone does not qualify. Replies exclude commentary retweets.

[NotificationsScreen](../../app/src/main/kotlin/nl/davidengelen/redsquare/feed/NotificationsScreen.kt)
adds parent-author eligibility, neutral wording when only addressing is known,
self-exclusion, signature dedup and fail-closed personal controls.
[History](../../app/src/main/kotlin/nl/davidengelen/redsquare/feed/NotificationHistoryStore.kt)
and [read IDs](../../app/src/main/kotlin/nl/davidengelen/redsquare/feed/NotificationReadStore.kt)
are wallet/backend scoped. Reuse these contracts; history persistence is not a
system-alert delivery ledger.

No social system publisher, Replies/Mentions channels or FCM receiver exists.
[ChatNotifications](../../app/src/main/kotlin/nl/davidengelen/redsquare/chat/ChatNotifications.kt)
provides local identity/backend/policy checks and permission handling, but its
PendingIntent opens MainActivity without a destination. Social cold/warm tap
routing is new work. Preserve [PostNavigation.save](../../app/src/main/kotlin/nl/davidengelen/redsquare/feed/PostNavigation.kt)
before navigation and reject stale identity/backend targets without switching.

## Public candidate source and backend gate

A public included-transaction observer can propose signed social candidates.
[RedSquare onConfirmation/indexing](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/redsquare.js#L1882-L1900)
and [Archive reads](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/archive/archive.js#L714-L838)
support a concrete gate: selected backend, `field1:RedSquare`, exact `sig`,
`flagged_ne:1`, requested `limit:1`, then strict signed-content/target matching.
Archive clamps this requested limit to10; reject unrelated/malformed rows rather
than infer a one-row result. See the later [observer trace](SOCIAL_PUSH_GATEWAY_OBSERVER_2026_10_15.md). For replies,
verify the parent's signed author before saying it replies to the owner.
Root mentions require inspecting public content, not only first-recipient indexes.

This is available Archive visibility under that backend's filtering at read time,
not proof of every dynamic moderator rule or permanent acceptance. Absence can
mean indexing delay, pruning, removal or outage; retain bounded pending/retry
state rather than accepting a chain-only candidate. Track cursor/reorg/dedup and
bounded catch-up explicitly. No undocumented node hook or Relay fan-out is needed
for this block-confirmed social subset; coverage/latency still need verification.
[SaitoFullBlock](../../core/src/main/kotlin/nl/davidengelen/redsquare/saito/SaitoFullBlock.kt)
verifies bounded content but exposes only payment containment, not generic social
transactions. A usable scanner remains implementation work.

## Implementable before Firebase setup

Extract shared social eligibility from the screen. Implement a versioned opaque
wake-up reference, durable receiver/work coordination, exact backend lookup,
fresh local policy checks, independent alert dedup and identity-bound tap route.
At the accepted app/controlled-peer seam, inject the transport boundary and verify
actual system display/taps, denial, muted/stale/forged/duplicate events and process
recovery. This establishes local behavior, not FCM delivery.

A bounded local gateway slice can verify signed nonce/expiry registration, retain
opaque references and process controlled public candidates through the same
backend gate, with FCM sending replaced only at its external boundary. No user
wallet secrets are required; public reads use its own service identity, not a
user impersonation. Firebase configuration/IAM, deployment and real
closed-app/locked/Doze delivery on both backends remain separate prerequisites
and acceptance under the [maintained requirements](NATIVE_CLOSED_APP_PUSH_REQUIREMENTS_2026_10_15.md).
