# Play data and deletion inventory, 2026-10-15

Read-only release preparation for [#36](https://github.com/robelene112/redsquare-android/issues/36).
Inspected native source at `8f832110ed204ed20b0cc48821a0cd18f0c3a5b4`.
This records implemented behavior, not a privacy policy, Play declaration,
legal determination or completed deletion service. No runtime user data, node
state, credentials, network endpoints or production databases were inspected.
The parent [account-deletion policy note](PLAY_ACCOUNT_DELETION_SCOPE_2026_10_15.md)
explains why a noncustodial wallet alone does not establish an exemption.

## Local data

The application declares `allowBackup="false"` in
[AndroidManifest.xml](../../app/src/main/AndroidManifest.xml).
The principal file stores below use `noBackupFilesDir`. These choices are not
an implemented account-deletion operation or proof of secure physical erasure.
`PublicStringStore` writes ordinary JSON strings with atomic commits and bounds;
it does not encrypt them. `EncryptedLocalFile` uses AES-256-GCM with an Android
Keystore key and filename-bound associated data. Device encryption of a local
record says nothing about encryption of its published network payload.
Sources: [PublicStringStore.load/save](../../app/src/main/kotlin/nl/davidengelen/redsquare/feed/PublicStringStore.kt),
[EncryptedLocalFile.load/save](../../app/src/main/kotlin/nl/davidengelen/redsquare/saito/EncryptedLocalFile.kt).

| Category | Existing storage, off-device boundary and deletion behavior |
| --- | --- |
| Wallet identity and imported options | [AndroidWalletStore.loadOrCreate/loadExisting/replace](../../app/src/main/kotlin/nl/davidengelen/redsquare/AndroidWalletStore.kt) encrypts `wallet.v1` and temporary replacement recovery records. Private signing material remains in this local path; the public key and signatures leave the device through peer authentication and signed actions. `replace` verifies the replacement and removes its temporary recovery record, but does not sweep other identity-scoped data. Its narrow incomplete-initialization cleanup deletes unpublished partial files and an old storage-key entry only in that state. No user-facing destruction of an established wallet or comprehensive Keystore cleanup is demonstrated. |
| Wallet import and export documents | [WalletImport.documentChosen/confirm](../../app/src/main/kotlin/nl/davidengelen/redsquare/WalletImport.kt) validates a selected document before guarded replacement. [WalletExport.save/discard](../../app/src/main/kotlin/nl/davidengelen/redsquare/WalletExport.kt) writes and reads back the user-selected document after authentication. [SaitoWallet.exportJson](../../core/src/main/kotlin/nl/davidengelen/redsquare/saito/SaitoWallet.kt) includes the private key in JSON; this exported file is not encrypted by the exporter. A chosen Android document provider may be remote. Failed/cancelled export cleanup attempts `DocumentsContract.deleteDocument`, with errors caught; successful exports and the original import file are not automatically deleted. Provider backups and other copies are outside demonstrated app control. |
| Unpublished posts, replies, quotes and author actions | [PostDraftStore.load/save/saveComposer](../../app/src/main/kotlin/nl/davidengelen/redsquare/feed/PostDraftStore.kt) encrypts text, normalized image sources, signed candidates, baselines and delivery flags, scoped by wallet/backend and target. Editing alone does not publish the draft. `cancelQueued` removes the queued candidate from that saved work and retains editable text/images. `saveComposer` can replace completed composition with an empty saved record. Neither is whole-account deletion or recall of a transaction already submitted. No general discard-all or expiry sweep is demonstrated. |
| Unpublished profile work | [ProfileDraftStore.save/clearAvailable/startNew](../../app/src/main/kotlin/nl/davidengelen/redsquare/feed/ProfileDraftStore.kt) encrypts changes, candidate, revision and signed baseline/superseding snapshots per wallet/backend. `clearAvailable` clears the exact confirmed work to an empty record while preserving revision; `startNew` retains the newest signed profile baseline. Network publication occurs separately through [ProfileEditorState.send](../../app/src/main/kotlin/nl/davidengelen/redsquare/feed/ProfileEditorScreen.kt). No account-wide draft erasure or timed expiry is demonstrated. |
| Private Chat history, drafts and reactions | [ChatHistoryStore.save](../../app/src/main/kotlin/nl/davidengelen/redsquare/chat/ChatHistoryStore.kt) retains authenticated original ciphertexts per wallet/backend/conversation and deliberately merges old history rather than replacing it with empty server results. [ChatWorkStore.prepare/cancelQueued/saveDraft](../../app/src/main/kotlin/nl/davidengelen/redsquare/chat/ChatWorkStore.kt) and [ChatReactionStore](../../app/src/main/kotlin/nl/davidengelen/redsquare/chat/ChatReactionStore.kt) encrypt work and delivery state locally. Cancelling queued work restores editable message text; it is not remote deletion. No conversation/history destruction or automatic retention deadline is demonstrated. |
| Group Chat state, history and work | [GroupChatStore.load/save/prepareLeave/cancelQueued/stopStaleAction](../../app/src/main/kotlin/nl/davidengelen/redsquare/chat/GroupChatStore.kt) encrypts retained creation/control/message/reaction originals, membership, group-key material when present, drafts and attempts per wallet/backend/group. Leaving changes membership; cancelling restores draft; stopping a stale action retires that attempt. These are not demonstrated deletion of retained history, recipient copies or remote records. [EncryptedChatRecord.save](../../app/src/main/kotlin/nl/davidengelen/redsquare/chat/EncryptedChatRecord.kt) removes obsolete part files only after verified replacement, not the current logical history. No timed history deletion is demonstrated. |
| SAITO transfer safeguards | [TransferStore.reserve/recordInclusions/pending](../../app/src/main/kotlin/nl/davidengelen/redsquare/saito/TransferStore.kt) encrypts wallet-wide signed payment reservations, backend and inclusion observations plus a guard. Every saved candidate remains reserved; observation does not release inputs. Payment transactions leave the device when deliberately submitted through [WalletTransfers](../../app/src/main/kotlin/nl/davidengelen/redsquare/WalletTransfers.kt). No user deletion or automatic expiry of reservations is demonstrated. Clearing these safeguards would need financial safety design, not a generic cache purge. |
| Username work and proofs | [UsernameRequestStore.save/retire](../../app/src/main/kotlin/nl/davidengelen/redsquare/registry/UsernameRequestStore.kt) encrypts per-wallet/backend signed registration attempts; `retire` retains a retired record and conflict proof rather than deleting network registration. [RegisteredNameStore.save](../../app/src/main/kotlin/nl/davidengelen/redsquare/registry/RegisteredNameStore.kt) encrypts wallet-scoped verified name proofs and source backend. No local wipe-all or remote unregistration is demonstrated. |
| Feed, thread, profile and search caches | [AndroidFeedCache.save](../../app/src/main/kotlin/nl/davidengelen/redsquare/feed/AndroidFeedCache.kt), [AndroidProfileCache](../../app/src/main/kotlin/nl/davidengelen/redsquare/feed/AndroidProfileCache.kt), [AndroidProfileDetailsCache](../../app/src/main/kotlin/nl/davidengelen/redsquare/feed/AndroidProfileDetailsCache.kt) and [AndroidSearchCache.save](../../app/src/main/kotlin/nl/davidengelen/redsquare/feed/AndroidSearchCache.kt) retain public signed content in unencrypted bounded stores. They are backend-scoped; following-feed caches additionally include wallet identity. Count/byte limits can evict entries, but no time-based deletion or user clear-all is demonstrated. The search cache stores posts, not query strings. Search text filtering is local; exact username lookup is a separate remote Registry request in [SearchScreen](../../app/src/main/kotlin/nl/davidengelen/redsquare/feed/SearchScreen.kt). |
| Author edit/deletion proofs | [AndroidPostRevisions.confirmDeletion/apply](../../app/src/main/kotlin/nl/davidengelen/redsquare/feed/AndroidPostRevisions.kt) stores a public revision containing the original post and verified deletion command, then suppresses it in normal local views. This deliberately retained proof survives eviction from individual view caches. It is not erasure of the original bytes. |
| Personal following, mute/block and conversation mute | [FollowingState.toggle](../../app/src/main/kotlin/nl/davidengelen/redsquare/feed/FollowingState.kt) saves public-key lists in an unencrypted wallet-scoped local store; toggle removes individual follows. [AndroidPersonalControls.save](../../app/src/main/kotlin/nl/davidengelen/redsquare/moderation/AndroidPersonalControls.kt) encrypts wallet-scoped mute/block/conversation choices shared across backends, with temporary recovery records removed after commit. [PersonalControls.setMuted/setBlocked/setConversationMuted](../../app/src/main/kotlin/nl/davidengelen/redsquare/moderation/PersonalControls.kt) can reverse individual choices. These paths do not transmit a remote follow/block transaction. No aggregate personal-data wipe or expiry is demonstrated. |
| In-app activity and read markers | [NotificationHistoryStore.save](../../app/src/main/kotlin/nl/davidengelen/redsquare/feed/NotificationHistoryStore.kt) retains up to 300 public signed events; [NotificationReadStore.mark](../../app/src/main/kotlin/nl/davidengelen/redsquare/feed/NotificationReadStore.kt) retains up to 2000 public event IDs per wallet/backend. Both use unencrypted bounded stores. Marking read changes local markers, not the published events. No clear-all or time-based deletion is demonstrated. |
| Preferences and UI state | [AndroidBackendStore.save](../../app/src/main/kotlin/nl/davidengelen/redsquare/saito/AndroidBackendStore.kt) persists the selected backend and removes its transaction marker after success. [AndroidAppearanceStore.save](../../app/src/main/kotlin/nl/davidengelen/redsquare/ui/RedSquareTheme.kt), [NotificationPermissionPrompt.recordOffer](../../app/src/main/kotlin/nl/davidengelen/redsquare/NotificationPermissionPrompt.kt) and [MainActivity](../../app/src/main/kotlin/nl/davidengelen/redsquare/MainActivity.kt) persist appearance, permission-offer and wallet backup-reminder choices in preferences. The permission-offer flag is not remote push-subscription consent. No general preferences reset is demonstrated. |

Wallet replacement, backend switching, a hidden post, marking activity read and
queue cancellation must not be described as account deletion. No complete native
account-deletion entry point or coordinated local-and-remote deletion transaction
is demonstrated in these inspected production paths. This is a source finding,
not a claim about the operating system's uninstall behavior or forensic erasure.

## Data that leaves the device

| Category | Existing transmission and actual demonstrated control |
| --- | --- |
| Backend connection and queries | [SaitoBackend](../../core/src/main/kotlin/nl/davidengelen/redsquare/saito/SaitoBackend.kt) limits production selection to `saito.davidengelen.nl` and `saito.io`. [SaitoPeer.authenticate/request/submit](../../app/src/main/kotlin/nl/davidengelen/redsquare/saito/SaitoPeer.kt) uses the wallet public identity, challenge signatures, peer requests and signed transactions. Archive criteria contain requested references/keys; Registry requests can include a username. A server necessarily receives the connection and request, but its access logs, backups, IP retention, operator roles and deletion controls were not inspected. Selecting a server does not demonstrate migration or removal from the other. |
| RedSquare social content | [RedSquarePosts](../../core/src/main/kotlin/nl/davidengelen/redsquare/saito/RedSquarePosts.kt), [RedSquareActions](../../core/src/main/kotlin/nl/davidengelen/redsquare/saito/RedSquareActions.kt), [RedSquareLikes](../../core/src/main/kotlin/nl/davidengelen/redsquare/saito/RedSquareLikes.kt) and [RedSquareAuthorActions](../../core/src/main/kotlin/nl/davidengelen/redsquare/saito/RedSquareAuthorActions.kt) create or validate signed posts/replies/quotes/reposts/likes/author commands. Public payloads include identity, references, timestamps, text and attached media where applicable. [AuthorEditState.send](../../app/src/main/kotlin/nl/davidengelen/redsquare/feed/AuthorEditState.kt) submits a signed delete command and confirms only after observing that exact command and successful selected-Archive absence of the original. This establishes normal-view removal on that backend, not erasure from chain/history, independent archives or recipients. No account-wide social deletion sweep is demonstrated. |
| Profile and Registry | [SaitoProfiles.message](../../core/src/main/kotlin/nl/davidengelen/redsquare/saito/SaitoProfiles.kt) publishes a complete signed Profile snapshot with public bio/image/banner fields. Setting a changed field to JSON null removes it from the new snapshot; [ProfileEditorState.removeImage/send](../../app/src/main/kotlin/nl/davidengelen/redsquare/feed/ProfileEditorScreen.kt) exposes image removal and submission. This does not retract earlier snapshots. [UsernameState.register/run](../../app/src/main/kotlin/nl/davidengelen/redsquare/registry/UsernameState.kt) and [SaitoRegistry](../../core/src/main/kotlin/nl/davidengelen/redsquare/saito/SaitoRegistry.kt) support signed username registration and registrar proofs. No Registry identity deletion, historical Profile erasure or cross-backend deletion authority is demonstrated. |
| Chat and Relay/Archive | [submitChatTransaction](../../app/src/main/kotlin/nl/davidengelen/redsquare/chat/ChatSubmission.kt) submits the original transaction and a Relay request to the selected peer. [PrivateChat.create](../../core/src/main/kotlin/nl/davidengelen/redsquare/saito/PrivateChat.kt) encrypts private message content; public signed routing identity/recipient metadata remains. [GroupChat.message/create/leave/removeMember](../../core/src/main/kotlin/nl/davidengelen/redsquare/saito/GroupChat.kt) uses unencrypted signed group content and membership controls. [archivedChatOriginal](../../app/src/main/kotlin/nl/davidengelen/redsquare/chat/ChatArchiveOriginal.kt) can verify exact retained server availability; that is not recipient delivery or permanent retention. Group removal/leave does not demonstrate deletion of past messages. No remote Chat erasure operation is demonstrated. Actual chain inclusion and Archive lifetimes must be established separately; submission alone is not proof that every message is confirmed on chain or permanently recoverable. |
| Reports and moderation | [ReportState.send/save](../../app/src/main/kotlin/nl/davidengelen/redsquare/feed/ReportState.kt) persists the exact signed `RedSquare` / `flag tweet` intent and attempted/acknowledged/error flags in a local unencrypted public store, then sends it to the selected backend. [ReportAction](../../app/src/main/kotlin/nl/davidengelen/redsquare/feed/ReportAction.kt) shows the destination before submission. Acknowledgement does not demonstrate moderator review or removal. No report withdrawal, report-retention deadline, moderator case database deletion or operational escalation process is demonstrated. |
| Remote images, video and selected documents | [PostImageLoader.bytes/load](../../app/src/main/kotlin/nl/davidengelen/redsquare/feed/PostImageLoader.kt) resolves relative paths against the selected backend and fetches absolute approved image URLs directly, with a bounded memory bitmap cache. Embedded data images need no separate image request. [PostVideo](../../app/src/main/kotlin/nl/davidengelen/redsquare/feed/PostVideo.kt) opens the provider embed after user interaction and offers an external YouTube link; see [VideoBrowser](../../app/src/main/kotlin/nl/davidengelen/redsquare/feed/VideoBrowser.kt) for WebView requests. These providers can receive requests independently of Saito. Provider-side retention or deletion is not demonstrated by closing the view. Android document-provider export is a separate, potentially off-device private-key boundary described above. Giphy integration remains unfinished under #16/#31 and is not an implemented data flow established by this inventory. |

The configured owner endpoint is the intended publisher-operated option, but this
native-source inventory cannot establish its legal controller, deployment logs,
backup retention, database-deletion procedures or control over network copies.
`saito.io` is a separately selected operator, not demonstrated to be a service
provider acting on this publisher's instructions. Peers, recipients and independent
archives likewise have no demonstrated deletion agreement or publisher control.
Do not classify those relationships in a privacy policy without further facts.

## Existing notifications versus planned push

[ChatNotifications.notifyChat](../../app/src/main/kotlin/nl/davidengelen/redsquare/chat/ChatNotifications.kt)
posts a local Android system notification after authenticated message retention,
subject to current wallet/backend, personal controls, group membership and system
permission. Its visible content is `New message`, uses private visibility and
opens MainActivity. Auto-cancel removes a tapped Android alert, not its message.
The inspected [manifest](../../app/src/main/AndroidManifest.xml) and
[app dependencies](../../app/build.gradle.kts) do not configure an FCM receiver,
Firebase messaging dependency or push-token registration flow. No existing FCM
token/subscription inventory or delivery/deletion claim follows from these paths.

[#32](https://github.com/robelene112/redsquare-android/issues/32) remains future
implementation. Any gateway/FCM design must separately inventory token,
wallet/backend subscription binding, authorization material, queue/log retention,
revocation, token rotation and deletion on request. The
[observer research](SOCIAL_PUSH_GATEWAY_OBSERVER_2026_10_15.md) does not implement
that infrastructure. Android alert permission or the local `offered` preference
must not be treated as proof of informed remote subscription choice.

## Proposed request process, not implemented

1. Publish a clearly identified app/developer deletion-request page, reachable
   from app Settings and independently on the web. Explain wallet destruction,
   funds/access consequences and independently retained records before consent.
   Preserve the existing backup/authentication safeguards; never request a private
   key, wallet backup or seed by email or upload.
2. Define a secure ownership-verification route, such as a narrowly scoped signed
   challenge where appropriate, without promising that this design is approved.
   Offer an accessible alternative for lost-device cases. Resolve impersonation,
   support evidence and minimal request logging before implementation.
3. Identify every publisher-held record and actual operator. Specify which remote
   records can be removed, which normal-view tombstones can be submitted while
   keys remain available, and which contracted providers must receive a request.
   Do not silently submit transactions or contact independent operators.
4. Design and verify a coordinated local cleanup only after explicit consent:
   stop eligible queued work safely, preserve required recovery/financial guards,
   clear relevant records and keys with a documented result, and treat exported
   documents as separately managed copies. This flow does not exist yet.
5. Supply a result distinguishing completed deletion, failed actions and retained
   categories with their basis, retention duration and escalation route. Resolve
   independently retained network data with Play policy rather than assuming a
   disclosure itself grants an exception.

Unresolved publisher facts: legal entity/name, privacy and deletion contact,
responsible backend operators and provider agreements, request verification and
completion process, response timeframe, justified retention schedule, production
log/backup behavior and Play's classification of this transferable network
identity. No contact address, processing deadline or legal retention basis has
been invented. #36 remains open; this proposal performs no deletion or submission.
