# Red Square Android first release: specification review draft

Status: synthesized from the app-wide grill on 2026-10-08. Testing boundaries
await the owner's check before publication through `to-spec`. GitHub Issues
will hold the authoritative specification; this review draft is not a ticket
tracker. No Android application or preview build exists yet.

## Problem Statement

Red Square users need a fast, professional Android app that preserves familiar
Saito social and Chat behavior. The existing browser interface does not provide
the intended native mobile experience. Users need to retain their non-custodial
identity, use either supported backend, and keep reading or composing through
connection failures. The owner needs a development loop that produces reviewable
phone builds without repeated uninstalling or losing a wallet.

## Solution

Build Red Square in Kotlin for Android 10 and newer, with a shared Saito wallet
and connectivity foundation supporting distinct Red Square and Chat features.
Preserve existing Saito capabilities within the agreed scope, including ordinary
reposts and quote posts. Open directly into the feed with a locally created
wallet and a visible import option. Support the owner's server and Saito's
server, preserving identity while respecting different available histories and
moderation rules. Deliver the app through sequential, independently verifiable
slices, beginning with wallet compatibility and a live feed from both backends.

## User Stories

1. As a new user, I want the app to open directly into the feed, so that I can begin reading without a setup wizard.
2. As a new user, I want a wallet created locally without waiting for block confirmation, so that identity setup is seamless.
3. As an existing Saito user, I want a visible wallet import option, so that I can retain my identity.
4. As a user, I want my wallet keys to stay on my device, so that I retain control of my identity and funds.
5. As a user, I want one active wallet shared by Red Square and Chat, so that my identity is consistent.
6. As a user, I want my wallet and local work preserved through ordinary updates, so that reviewing a new build is safe.
7. As a user, I want compatible wallet exports, so that I can recover my identity elsewhere.
8. As a user replacing a wallet, I want file and key-pair validation before changes, so that invalid imports leave my current identity intact.
9. As a user importing a wallet, I want a preview distinguishing restore from replacement, so that I understand the operation.
10. As a user replacing a funded wallet or one with an unknown balance, I want a fresh backup required first, so that I can recover the outgoing identity.
11. As a user replacing a verified zero-balance wallet, I want a recommended backup that I can skip, so that replacement remains practical.
12. As a user replacing a wallet, I want names and pending work explained before confirmation, so that I understand what belongs to the outgoing identity.
13. As a user whose import fails or is interrupted, I want the previous wallet retained or recovered, so that an error does not destroy my identity.
14. As a user, I want to start posting and chatting before making a backup, so that first use remains seamless.
15. As a user, I want a dismissible backup reminder and recovery guidance, so that I understand how to protect my identity.
16. As a wallet owner, I want to see my SAITO balance and receiving details, so that I can use the wallet.
17. As a wallet owner, I want to send SAITO while connected, so that I can make transfers intentionally.
18. As a wallet owner, I want to confirm recipient, amount and fee before device authentication, so that I can catch transfer mistakes.
19. As a wallet owner, I want device authentication for transfers and exports, so that sensitive actions require my consent.
20. As a user, I want the owner's backend selected initially, so that first use follows the intended default community.
21. As a user, I want to choose between the owner's backend and Saito's, so that I can choose their history and moderation.
22. As a user switching backends, I want to keep my wallet and identity, so that I do not need another import.
23. As a user during an outage, I want my backend choice retained, so that the app does not silently change communities.
24. As a reader, I want the selected backend's general feed, so that I can browse available posts.
25. As a reader, I want a Following feed, so that I can focus on accounts I follow.
26. As a reader, I want threads and replies, so that I can understand a conversation.
27. As a reader, I want profiles and their posts, replies and likes, so that I can explore other users.
28. As a user, I want to follow accounts, so that I can find their posts again.
29. As a reader, I want cached content displayed immediately and refreshed in the background, so that launches feel fast.
30. As a reader, I want cached posts available offline, so that a connection failure does not make the app empty.
31. As an author, I want to compose posts and replies, so that I can participate in Red Square.
32. As an author, I want drafts saved locally, so that navigation or an outage does not discard my work.
33. As an author, I want saved drafts to remain unsent until I press Send, so that I control publication.
34. As an author pressing Send offline, I want a visible cancellable queue, so that my intent is preserved until connectivity returns.
35. As an author switching backends, I want queued posts to remain tied to their original backend, so that they reach the intended destination.
36. As an author, I want retries to preserve identity and avoid duplicate posts, so that recovery behaves predictably.
37. As a user, I want likes and ordinary reposts, so that I can engage with existing Saito content.
38. As an author, I want quote posts with commentary or images and the original post displayed, so that I can add context when sharing.
39. As an author, I want to edit my own post text, so that I can correct it.
40. As an author, I want to delete my own posts, so that I can remove them from normal app views.
41. As a user deleting a post, I want an accurate explanation of remaining historical copies, so that deletion is not misleading.
42. As an author, I want image attachments in posts and replies, so that I can share visual content.
43. As a reader, I want image viewing, so that attachments remain usable on a phone.
44. As an author, I want Giphy search and selection, so that I can share GIFs.
45. As a viewer, I want YouTube playback inside the app when tapped, so that I can watch without disruptive autoplay.
46. As a user, I want to edit my banner, bio and profile picture, so that my profile represents me.
47. As a user, I want an optional Saito username registration flow, so that I can choose a recognizable name without delaying first use.
48. As a user, I want post and people search, so that I can find available content and identities.
49. As a user, I want personal account muting across both backends, so that changing servers does not undo my preferences.
50. As a user, I want blocking to also hide incoming Chat messages from that account, so that stronger personal controls are clear.
51. As a user, I want short explanations for mute, block and conversation mute, so that I understand their different effects.
52. As a user reporting a post, I want the selected backend named as the destination, so that I know which moderators receive the report.
53. As a user reporting a post, I want submission status distinguished from moderator review, so that the app does not claim an action it cannot verify.
54. As a user, I want in-app notifications, so that I can see replies, mentions and social activity.
55. As a user, I want optional push notifications while the app is closed, so that I can return to replies, mentions and Chat messages.
56. As a user, I want a skippable notification-permission request when I first open Chat or Notifications, so that first launch stays focused on the feed.
57. As a user, I want Android notification categories and an in-app settings shortcut, so that I can control replies, mentions, private chats and group chats.
58. As a private Chat user, I want a generic New message notification by default and opt-in previews, so that notification text respects my privacy choice.
59. As a Chat user, I want one-to-one private conversations compatible with Saito encryption, so that I can message existing users.
60. As a Chat user, I want group conversations clearly labelled as unencrypted, so that I understand their privacy model.
61. As a Chat user, I want to create groups and invite people, so that I can organize conversations.
62. As a group member, I want to view membership and leave, so that I can manage my participation.
63. As a group admin, I want to remove members and appoint admins under Saito's existing rules, so that group management interoperates.
64. As a Chat user, I want image and GIF messages, so that conversations support existing visual media.
65. As a Chat user, I want conversation muting that retains visible messages, so that I can silence notifications without blocking people.
66. As a Chat user sending offline, I want a cancellable backend-bound queue, so that messages resume on the intended server.
67. As a Chat user, I want available network history combined with local cached history, so that recoverable messages remain useful.
68. As a user importing a wallet on another device, I want clear limits on history recovery, so that I do not expect unavailable messages to reappear.
69. As a user, I want a small red-dot outage header, so that connection state is visible without blocking the app.
70. As a user reconnecting, I want a brief green-dot Back online message before the header disappears, so that recovery is clear.
71. As a user, I want outages distinguished from a selected-backend failure, so that the status describes the actual problem.
72. As a user, I want light and dark themes following Android by default, so that the app fits my phone settings.
73. As a user, I want an English interface with straightforward punctuation, so that the app's text is consistent.
74. As an author, I want my language and punctuation preserved, so that interface copy rules do not rewrite my messages.
75. As the owner reviewing on a Pixel 7, I want an interactive cached feed within two seconds of cold launch, so that speed is measurable.
76. As the owner reviewing on a Pixel 7, I want an interactive first-launch feed within five seconds on stable Wi-Fi, so that initial use is also measured.
77. As the owner, I want a stable review link, build notes and in-place APK updates, so that I can review work from my phone.
78. As the owner, I want a review-build update notice, so that I can find the latest artifact easily.
79. As the owner, I want sequential feature delivery with verification and code review, so that each increment is understandable.
80. As a future maintainer, I want shared identity and connectivity with distinct Saito feature boundaries, so that adding Stack later does not require rebuilding the foundation.

## Implementation Decisions

- Native Android in Kotlin; Android 10 is the minimum intended OS. The primary physical review device is a Pixel 7 running Android 17, as reported by the owner. Select the UI toolkit and native integration mechanism through the initial feasibility work rather than presenting them as already proven.
- Display name: Red Square. The stable application identifier, signing identity and version progression must be established before review installations. Signing must support the intended later Google Play transition.
- Shared foundation owns wallet identity, compatible signing, persistence, backend sessions and identity-scoped work. Red Square and Chat own distinct native feature behavior. This is not an arbitrary remote-module execution system.
- Select only https://saito.davidengelen.nl or https://saito.io. The former is the fresh-install default. Persist selection through restarts and outages. Imported peers cannot introduce or select other backends.
- Separate backend feed/cache state sufficiently to preserve each server's history and moderation. Personal mute/block choices belong to the active identity and apply across both servers. Do not promise cross-device synchronization of every preference.
- Preserve published Saito transaction compatibility and inspect current deployments explicitly. Support both ordinary reposts and quote posts; older/current module differences are integration work, not implicit feature exclusions. Do not assume the archived repository equals the current official deployment.
- Wallet creation is local. First-launch reading does not wait for setup or backup. Demonstrate fresh unfunded-wallet posting and Chat interoperability rather than assuming that construction paths guarantee delivery.
- Wallet replacement follows the verified Snake safeguards: preview and validation first; explicit replacement confirmation; fresh outgoing export for positive/unknown balances; optional strongly recommended export for verified zero; backup proof bound to the identities and selected file; names/pending-work acknowledgement; durable recovery and saved/live identity verification.
- Isolate queued work, drafts and private history by originating identity. Never sign an outgoing identity's work with an imported replacement key. Carry signed-work behavior over deliberately from the Snake safeguards and make remaining pending state understandable.
- Sending SAITO requires the selected backend connection, recipient/amount/fee confirmation, then supported system authentication. Export also requires system authentication. Never silently weaken these requirements when authentication is unavailable. Define setup/retry/recovery behavior before shipping those flows.
- Posting and Chat queues are durable and backend-bound, with Queued status and cancellation before sending. Switching backends pauses work for the other backend. Drafts are not queued until Send. Retrying must retain the operation's identity and avoid duplicate visible effects. SAITO transfers are not automatically queued offline.
- An outage preserves feed position and composition. Recovery is shown only after the corresponding failed condition clears; an internet return alone is insufficient when the selected backend is still unreachable.
- Match private Chat encryption and existing group membership rules. Group conversations are visibly unencrypted. A full compatible wallet backup may carry useful conversation/keychain state but is not a complete local message-history backup.
- Recover available history from the chosen backend or compatible peers and combine it with identity-scoped local cache. Preserve cached messages through ordinary updates/outages. Do not describe the rotating chain or archive as permanent storage.
- Account mute hides posts and notifications while Chat remains accessible. Block additionally hides incoming Chat messages from that account. Conversation mute silences notifications and retains messages. Explain these choices where selected and managed.
- Reports target the selected backend. Distinguish locally requested/submitted/acknowledged status from moderator review. The current optimistic browser wording is not proof of durable moderator acknowledgement; define truthful status from observed capabilities.
- Optional notifications cover replies, mentions, private Chat and group Chat. The first Chat/Notifications visit offers a skippable permission request. Categories use Android controls with an in-app shortcut; private content previews require explicit opt-in. The push delivery mechanism is an implementation decision and must work with the accepted privacy model and both backends.
- Images, Giphy selection and tap-to-play YouTube are included. Determine bounded attachment handling from the compatible existing formats and verify it on native clients. Arbitrary live file transfer is a separate Saito integration, not added by image/GIF support.
- General and Following feeds, profiles, search and social interactions respect the selected backend's available records. Search must state relevant availability limits rather than imply exhaustive network history.
- Interface copy is English and contains no em dashes. Preserve arbitrary user content verbatim. Use translation-ready resources and system/light/dark theme choices.
- Source inspection alone does not authorize node upgrades, production database changes or live test transactions. Any necessary server-side change gets a separate scoped ticket, isolated verification and the existing operations procedure. Block production remains deferred.
- Review delivery initially uses signed APKs, a stable link, build notes and an update notice. Ordinary Android installation consent remains part of the development loop. Google Play is the intended public distribution channel; its account and release requirements are checked when publishing work begins.

## Testing Decisions

Proposed boundaries for the owner's check before publishing this specification:

1. **Primary application boundary:** test externally visible Android flows through the app against controlled Saito peer/archive/relay fixtures. Verify displayed state, persisted recovery and emitted compatible operations, not individual UI implementation methods.
2. **Protocol and wallet boundary:** use a small set of reference compatibility vectors from the pinned Saito implementation for key derivation, signatures, transaction serialization, encryption and full wallet import/export. This lower boundary is justified because UI success cannot prove cryptographic or backup compatibility.
3. **Failure and persistence behavior:** exercise process interruption, invalid imports, failed saving/rollback, update/restart continuity, backend outages, queued-send cancellation/retry and identity replacement at the same application boundary. Verify recovery preserves the outgoing wallet and never changes the signing identity of existing work.
4. **Cross-client behavior:** verify applicable posts, replies, reposts, quotes, edits/deletes, profiles, usernames and Chat against representative existing Saito clients and both backend contracts. Use isolated staging or controlled peers for automated writes. Read-only production checks are separate from any explicitly authorized live write test.
5. **Physical-device acceptance:** review install/update, wallet continuity, native UI responsiveness and notifications on the owner's Pixel 7. Verify Android 10 correctness separately. Emulator results do not establish Pixel performance.
6. **Performance boundary:** measure an interactive cached feed within two seconds of a cold launch, and first-install interactive feed within five seconds on stable Wi-Fi, on the Pixel 7. Record build, device/OS, connection conditions, cache state and timing start/end. Define repeatable run counts and pass criteria before claiming either target.
7. **Closed-app notifications:** verify actual delivery and tap destinations with the app backgrounded/closed, permission denial/skip, system categories, conversation/account controls and private-preview settings. A successful push API call is not proof of a displayed notification.
8. **Release/update boundary:** verify artifact package/signing/version compatibility and retained identity/local data with an ordinary in-place update. No private wallet data or signing secrets belong in artifacts, reports or source control.

There are no existing Android test seams. Snake provides prior art for wallet
validation, balance-dependent backup gates, outgoing-identity isolation and
interruption recovery. Adopt its observable guarantees, not its browser/game
implementation. Keep live integrations separate from repeatable routine tests.
Feature tickets must name their applicable subset of these boundaries.

## Out of Scope

- iOS implementation; a later app would be a separate Swift project.
- Enabling block production as an app prerequisite or automatically changing the node's consensus configuration.
- User-configured backends beyond the two approved domains.
- Automatic backend failover during outages.
- Simultaneously active multiple wallets in one installation.
- Automatic offline SAITO transfer submission.
- Guaranteed permanent network history, complete recovery of every old Chat message, and complete-history phone migration/backup in this release.
- Implementing Stack or all other Saito modules in the first release; the shared foundation must permit future native features.
- Arbitrary live FileShare integration by virtue of accepting Chat image/GIF support.
- React Native, Ralph loops and parallel ticket implementation.
- Automatic public release or production node upgrades as a side effect of creating this specification.

Quote posts are explicitly included. Unfinished research is not an exclusion.
Do not add other unsupported exclusions to the agreed scope without explaining
the product effect to the owner.

## Further Notes

- The initial review milestone combines wallet creation/import and real feed access to both supported backends. Earlier diagnostic/protocol slices may establish evidence, but a placeholder feed does not complete that milestone.
- One specification can capture the app-wide scope. Implementation is divided into small complete paths that fit a fresh agent context and have genuine blocking edges. A single coding attempt is not the acceptance strategy for this release.
- Focused feature grills remain useful when a material product decision emerges, such as a protocol limitation that changes user behavior. They should not repeat the settled app-wide interview or ask the owner to research technical facts.
- Read-only research establishes that the owner's September 16 upstream revision is behind the inspected September 29 production branch, while saito.io serves additional newer behavior. Its exact deployed Git revision is unestablished. Current official code retains the redesigned quote interaction, so an update alone is not a verified quote-display fix. Assess maintenance separately in staging while preserving Snake; app work proceeds against explicit compatibility baselines. See [version provenance](../research/SAITO_VERSION_PROVENANCE.md).
- Account registration, signing custody, hosting details and store readiness must be made concrete in their delivery tickets. No review URL or APK is currently available.
