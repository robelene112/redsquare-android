# Play release package

Prepared for [#49](https://github.com/robelene112/redsquare-android/issues/49),
child of [#36](https://github.com/robelene112/redsquare-android/issues/36).
This is a reviewable offline package, not a submitted or approved release.
Google documentation was rechecked on2026-10-18. The accepted native regression
is [issue48](../evidence/ticket48/NATIVE_ACCEPTANCE.md); actual packaging evidence
is [issue49](../evidence/ticket49/README.md). Candidate 33 application inputs
match accepted48. Subsequent native50 changes the unconfigured Settings
descriptions, verified in its focused journey and complete Android17 run
39106585481. Android10 run39106583903 fails at a fixture's initial Wallet lookup before
its failed-save import scenario. After a bounded fixture correction, complete10
recheck39111106756 passes. Combined whole50 review reports0/0, and ordinary35 is
published and independently verified; publication bookend1ead reports0/0 and50
is CLOSED. Historical AAB33 retains its original source. Build a fresh bundle after
the remaining native/configuration changes before final store preparation.

## Artifact and signing decision

Use `scripts/review-build --bundle` for a fresh reserved version. It creates
`.scratch/play-builds/<code>/redsquare.aab`, a locally derived `universal.apk`,
actual manifest/certificate/tool outputs and public `manifest.json` metadata.
The same protected key, version counter and lock serve ordinary/performance APKs.
Release uses stable package `nl.davidengelen.redsquare`, Android10+ (min29),
target36, no debugging/shell profiling and `REVIEW_BUILD=false`. The bundle does
not offer the website review updater. Independent Release Push/Posts-Giphy/
Chat-Giphy options remain explicit. This candidate has all three unconfigured;
its metadata records only configuration booleans, never option values.

AAB signing uses JDK jarsigner, followed by strict verification, bundletool
validation and actual APK generation. Tool/version/hash are recorded. This
proves local packaging/signature validity, not device installation, Play App
Signing enrollment, an accepted Console upload or Google-generated split APKs. The actual64-bit
libraries pass16KiB load-alignment checks and the derived APK passes SDK16KiB
ZIP alignment. Raw RELRO-end layout observations are retained separately; their
nonzero remainders alone left16KiB-runtime behavior unverified, not a proven crash.
Subsequent native51 now passes the existing431-identity .dev suite on actual
16384-byte/x86_64 Android17 revision7, with separate signed ordinary35/performance36
wallet/startup/isolation proof. See [the scoped runtime results](../evidence/ticket51/README.md).
This does not establish all JNI entry points, ARM64, physical or Play-generated
installation, and does not change historical AAB33's source. Broader compatibility
remains a separate gate. See [Android page-size guidance](https://developer.android.com/guide/practices/page-sizes).
See [Android command-line builds](https://developer.android.com/build/building-cmdline)
and [bundletool](https://developer.android.com/tools/bundletool).

Before enrolling, the publisher must choose and verify Play App Signing using
an identity compatible with existing directly distributed installations. The
current certificate is `6ca1209bcbba661943f7b683d48b2f58493f13851c760c8d80d1521ac0ee79a2`.
The app-signing key and an upload key have different roles. Merely signing this
AAB with the existing key does not make Google's eventual APK certificate match.
Verify a genuine Play-track in-place update on a disposable prior installation,
then the owner's protected continuity check. Do not export/enroll the key or
create an upload key as part of this offline package. See
[Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756?hl=en).

## Listing draft, English

The following is proposed listing copy, not published marketing. Existing app
name is retained; [#38](https://github.com/robelene112/redsquare-android/issues/38)
keeps the later independent-brand decision open. Publisher must confirm naming
rights and avoid implying endorsement by Saito. Title: **Red Square**.
Short description:

> Posts, conversations and your own wallet on Saito, built for Android.

Full description:

> Read and join Red Square conversations from a native Android app. Browse the
> main feed or Following, open threads and profiles, and write posts, replies
> and quote posts with images. Search available posts and people, follow
> accounts, and manage your personal mute and block choices.
>
> Keep reading cached posts when your connection drops. Save drafts for later,
> or explicitly send a post or Chat message to queue it for delivery when its
> selected backend reconnects. Switch between the Saito and David servers in
> Settings. Available history and moderation can differ between servers.
>
> Use private and group Chat, with local conversation controls. Private Chat
> messages are encrypted in transit; group messages are not. Chat history
> depends on what is retained on your device and available from the network;
> importing a wallet does not guarantee complete past conversations.
>
> A wallet is created on your device for first use. You can import a compatible
> wallet or export a backup after Android authentication. The app holds your
> signing key locally. Protect your exported backup: the exported JSON contains
> private wallet material. Losing every key copy can mean losing access.
>
> This is an independently developed client for Red Square on Saito. Published
> posts and signed network activity can be retained by independent participants.
> Removing a post from normal views does not promise erasure of every copy.
>
> This preview has no configured background push service or GIF search keys.
> Existing GIF attachments can still be displayed. Embedded video availability
> depends on YouTube and the device. Public username registration is subject to
> the selected server's availability. Android10 or newer is required.

Do not remove the preview limitations until configured public-provider and
physical acceptance actually pass. Do not advertise two-second startup from
emulator evidence. Title/short/full limits are30/80/4000 characters, respectively:
[listing fields](https://support.google.com/googleplay/android-developer/answer/9859152).
Suggested category: Social, subject to publisher confirmation. The owner's
business decisions confirm a free first release. Countries, target age, ads
answer, content-rating questionnaire and verified developer identity still need
confirmation, not inference from source code. No paid app products/ad SDK were
introduced by this work; exploratory donations are not an approved product feature.

## Asset drafts and provenance

[Assets](assets/) contain the512px current-design icon (RGBA),1024x500 feature
graphic (RGB), editable SVG sources, and two unmodified320x640 RGB phone captures.
The icon reuses the tracked adaptive foreground paths and launcher red #DA243D,
using viewBox18 18 72 72; no rounded mask or claim of new brand ownership is added.
The feature graphic is vector artwork, not a fabricated app screenshot.

Phone captures are copied byte-for-byte from accepted48 Android17 emulator
captures `feed-readiness-interactive.png` and
`private-scoped-preview-exact-conversation.png`. They show actual `.dev` UI and
controlled test content, not the bundle installed from Play, live friend accounts
or a physical Pixel. The feed capture contains explicit fixture text. They are
review drafts; capture representative final-candidate content with permission
before submission. No screenshot was cropped, composed, recolored or AI-edited.

The native source/build equivalence and capture paths/hashes are retained in49
evidence. Google currently requires a512x51232-bit PNG icon,1024x500 JPEG/RGB
feature graphic, and at least two qualifying screenshots. These captures meet
minimum320px and maximum2:1 aspect requirements; they do not meet the higher
resolution recommendation for promotional placements. See
[preview asset requirements](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en-en).

## Reviewer access draft

No publisher password, private wallet, backup or recovery phrase is required.
A reviewer uses their own new empty wallet. Do not give them an owner's wallet.

1. Install the eventual authorized test-track candidate on Android10+. Open it
   into the feed; first launch creates its own wallet locally. A backend outage
   may leave available cached history or a connection banner. The app stays on
   the selected server rather than silently moving to another one.
2. In Settings, choose Saito or David. General and Following history may differ.
   Open a post, thread and author profile. Searching only covers available data.
3. Open the composer to inspect text/image draft controls. Back out to retain
   work. Publishing, reacting, registering a name, sending Chat or transferring
   SAITO are real signed network actions. Use reviewer-owned identities and
   deliberate test content only. No funded owner identity is supplied.
4. Open Wallet to see the reviewer's public receiving address. For export, set
   up the reviewer's own Android device lock/PIN, authenticate and choose their
   own document destination. The JSON contains private material; do not attach
   it to a review report. Import replaces identity through the existing guarded
   confirmation. Neither wallet import nor switching servers is account deletion.
5. Open Chat or Notifications to see the optional Android permission offer.
   Skip is allowed. Background transport is unconfigured in this candidate.
   Native50 now explains this in the two Settings descriptions while retaining
   saved choices. Its complete ordinary Android10/17 acceptance passes, whole
   and publication reviews report0/0, and ordinary35 is published/verified.
   Historical AAB33 and beta31 retain their older generic copy. Actual51 also
   passes the scoped16KB journeys; configured Google delivery remains unproved.
   Local in-app/foreground activity does not demonstrate Google push.
   GIF search is also unavailable; existing attachments can still render.
6. Use a second reviewer-controlled identity to exercise private/group Chat.
   Available history is retained locally; private transport is encrypted, group transport is not. Full historical resync is
   not promised. Payment requires funds and deliberate confirmation; do not
   invent a successful transfer in an unfunded review account.

These are source-grounded draft instructions, not a completed fresh-device
walkthrough. Rehearse on the exact final Play candidate after setup. No remote
service credentials or fixture-only controls exist in Release.

## Permission and data inventory, current implementation

The older [October15 inventory](../research/PLAY_DATA_INVENTORY_2026_10_15.md)
remains useful for named local stores, exports, post proofs, caches and remote
boundaries, but its "future FCM" section is superseded by the implemented
[42/45/46/47 checkpoints](../handoffs/IMPLEMENTATION.md). Neither document is a
privacy policy. The [GIF addendum](../research/GIPHY_DATA_FLOW_2026_10_17.md)
describes Posts flow; Chat now uses that same provider with its independent key.

The actual merged bundle/derived-APK permission list is in49 evidence, including
transitive SDK declarations. Source explicitly requests INTERNET,
ACCESS_NETWORK_STATE, USE_BIOMETRIC and POST_NOTIFICATIONS. WorkManager/Firebase
add normal wake/boot/service permissions; inspect the retained actual manifest,
not just these four source declarations. No camera, microphone, contacts,
location or broad storage runtime permission is required by the current source.
Android's chosen document provider handles wallet/image selection, including
potentially remote providers. Permission declarations alone do not prove which
SDK network traffic occurs at runtime.

| Data and purpose | Destination/control and proposed Console input |
| --- | --- |
| Public wallet ID, challenge signatures, requested references, Registry name | Selected Saito backend receives connection/request data and authenticated identity. Candidate Personal info/User IDs and app-interaction classifications require actual Console mapping and operator roles. Private signing key stays local, except explicit user-selected export, whose provider may be remote. Do not answer "no collection" solely because no email/password is used. |
| Public text, profile, attachments and signed financial activity | Explicit publication sends to network peers/archives; remote media URLs can expose ordinary IP/request metadata when rendered. Consider user-generated content/photos, messages and financial/transaction data as applicable. Current deletion hides normal-view posts while keeping signed proof; it cannot recall independent copies. |
| Private/group Chat | Private Chat sends signed encrypted originals; private decryption and preview rendering happen locally. Group Chat sends signed UNENCRYPTED originals, including membership/control references, and must not be advertised as end-to-end encrypted. Local group history/work is encrypted at rest, a separate property. Map messages/User IDs and actual encryption/ephemeral-processing definitions accurately, not a blanket "no data" answer. Local history/drafts/keys have no demonstrated whole-account deletion. |
| Optional background push, configured release only | `PushTransport` initializes Firebase only when configured; registration is manual and governed by independent wallet-local Social/Chat consent plus Android controls. Gateway bindings associate FID, public wallet/backend, categories and group interests, using installation revocation authority. Google gets FID plus an opaque wake; the gateway sees authenticated subscriptions and relevant signed originals/references. It retains encrypted private Chat originals, signed unencrypted group originals and Social references, not decrypted private text. Candidate Device/other IDs, User IDs, messages/app-interaction inputs and service-provider/sharing definitions need publisher/provider confirmation. |
| Push retention/control limits | Source `GatewayChatOriginals` bounds originals to24hours/4096files/128MiB and `GatewaySubscriptions` limits binding/event lifetimes (up to7days, Social wake up to24hours). Cleanup/revocation and deduplication state are implemented, not an operational deletion policy. Google `unregister` does not delete FID. Gateway hosting, logs/backups, Google retention and request process remain unknown. Native pending wake eligibility of7days is not a promise about full Chat history. |
| Giphy search/media | When configured, direct requests send query/key and ordinary connection data to Giphy; Posts and Chat keys are independent. Existing media display makes requests even without search keys. Media URLs are requested from the media host during previews/display; deliberate Send separately publishes selected references to Saito posts or transmits them to Chat recipients. Selected references/drafts are encrypted locally. No Giphy SDK analytics is implemented by this client; provider handling still needs verification. |
| YouTube and external links | Tapped embeds use YouTube's web player; external links open the user's selected app/browser. Provider-controlled traffic/identifiers/content must be reflected accurately. No claim that third-party embeds have zero collection, ads or retention. |
| Content-policy agreement | Child53 prepares an installation-local exact revision in an unencrypted bounded no-backup record, separate from wallets and portable exports. Only a deliberate successful acceptance save approves a configured ready revision. Actual publication checks it afresh before preparation/claim/enqueue. Missing/unready policies deny non-preview publishing; ordinary review builds remain draft-aware. This does not grant Android/Push consent, upload an agreement separately or demonstrate final owner approval. Complete Android10/17 verification and ordinary38 delivery are retained in53; final policy approval remains absent. |
| Local wallet/work/preferences/caches | Encrypted wallet/work/controls versus some unencrypted public caches/following/read markers are distinguished in the older inventory. No automatic Android backup. Local-only processing may differ from off-device collection for the form, but user-selected backup files and remote providers remain distinct. |

Proposed Data safety work: enumerate each actually collected type, required vs
optional purpose, sharing exception if justified, encrypted-in-transit scope,
retention/deletion result and applicable SDK/provider behavior for the FINAL
configured binary. No prefilled form or "all data encrypted" certification is
provided. The existing inventory is evidence to answer the actual form, not the
form itself. See [Data safety definitions](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en).

## Confirmed publisher decisions

On2026-10-18 the owner selected **Angels’ Foundry** as this app's publisher and
directed release preparation to the local business dossier. The spelling has
the apostrophe after the s. The owner's private `Business/README.md` and
`Business/BESLUITEN.md`, read on that date, also establish these product decisions:

- The first Saito-app release is free for the community. Donations are exploratory,
  with no donation feature, recipient or revenue model selected.
- The owner takes responsibility for moderation reports on the Owner backend
  and messages addressed to the app publisher by Google. The external backend's
  actual response process and complete moderation operations remain unverified.
- Avoid extensive usage tracking. Additional automatic crash reporting is still
  undecided; this is no instruction to add a reporting SDK or claim zero data flow.

The dossier records intended business registration, domains and business email,
and on 2026-10-19 the owner explicitly confirmed KVK registration is not yet
complete. Usable public contact values remain unconfirmed. Treat
Angels’ Foundry as the selected publisher name, not a verified registration or
verified organization account. Do not invent a legal suffix, registration number,
D-U-N-S number, support mailbox or policy URL. Only release-relevant decisions
are summarized here; private business records stay outside this repository.

## Native policy drafts

The owner authorized placeholder implementation on2026-10-19. Child52 adds a
bundled Settings reader for six clearly identified drafts, with no recorded
agreement, request submission, active contact link or invented retention values.
The maintained owner prerequisites are [OWNER_INPUTS.md](../../OWNER_INPUTS.md).
Draft resource text is in `app/src/main/res/values/policies.xml`; it can be read
offline in the app and remains subject to owner review. This reader is not final
policy approval, hosted policy publication or operational report/deletion handling.
Child53 separately prepares deliberate versioned consent before relevant content
creation/upload, with independent durable publication checks and cancellation of
unaccepted queued work. Complete controlled Android10/17 runs, separate reviews and ordinary38 delivery
demonstrate the prepared mechanism. No final ready
policy bundle is configured. Reading and wallet setup remain unobstructed; payments,
reports, deletion and notification consent retain their separate safeguards.
The substantive owner, operational and store gates below remain open.

## Publisher decision and remaining release gates

| Gate | Evidence/remaining action; none is completed by an AAB |
| --- | --- |
| Legal publisher and account | Chosen name: Angels’ Foundry, confirmed2026-10-18. KVK registration is not yet complete, confirmed2026-10-19. Registered legal identity, appropriate verified account, D-U-N-S where required, public contact/support details and eligible territories remain unverified. Google account-type guidance includes cryptocurrency wallets under financial services for organization accounts; noncustodial does not automatically override account guidance. [Account types](https://support.google.com/googleplay/android-developer/answer/13634885?hl=en), [organization verification](https://support.google.com/googleplay/android-developer/answer/13628312?hl=en). |
| Testing access | The12tester/14continuous-day production-access rule is for qualifying new PERSONAL accounts, not a general organization-account rule. Genuine organization verification and any actual Console requirements still apply. Internal testing is separate from production access. [Testing rule](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en), [review distribution](../research/PLAY_REVIEW_DISTRIBUTION.md). |
| Financial declarations | Declare actual wallet/transfer functionality; do not select "no financial features" merely because this is chiefly Social. Noncustodial wallets are outside the specific cryptocurrency exchange/software-wallet country licensing policy, not automatically outside other financial/account/declaration rules. Resolve actual Console categories and countries; no exchange, custody, earnings or licensing claim is invented. [Financial policy](https://support.google.com/googleplay/android-developer/answer/9876821?hl=en), [specific crypto policy](https://support.google.com/googleplay/android-developer/answer/16329703?hl=en-en). |
| Privacy policy and app access | Need identified publisher/contact, all actual providers/operators, purposes, retention/deletion and an accessible hosted policy plus in-app link. Settings has an offline draft policy reader under52; no finalized hosted-policy link is configured. No final text/URL is published here. [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en). |
| UGC Terms and moderation | Google UGC policy covers clients to UGC platforms. Child53 prepares deliberate exact-revision Terms/community acceptance before relevant content creation/upload; complete controlled Android10/17 runs, separate reviews and signed ordinary38 delivery are verified. Actual approved prohibited-content/behavior text and a ready production bundle are still absent. Need verified reporting/blocking/ongoing moderation coverage including public-content/user reports and1:1 Chat blocking. Existing post report/mute/block is useful, not demonstrated complete coverage. The owner accepts publisher and Owner-backend moderation responsibility; external-backend response, actual process, contact and enforcement remain unverified. [UGC policy](https://support.google.com/googleplay/android-developer/answer/9876937?hl=en-TT). |
| Social child-safety declaration | As a Social app, need published child-safety standards, effective feedback/report handling and a designated responsible contact, plus actual Console certification. None is invented here. Resolve age/target-audience classification against the actual app; pseudonymous wallet identity alone does not establish an anonymous-chat exemption or mandate. [Child-safety standards](https://support.google.com/googleplay/android-developer/answer/14747720?hl=en-en), [age-restricted functionality](https://support.google.com/googleplay/android-developer/answer/16302250?hl=en). |
| Account and data deletion | Do not assume exemption from a locally created noncustodial keypair. Confirm actual account classification and a supported in-app/web request path, ownership verification, completion process and justified retained data. Current local wallet replacement, backend switch, opt-out or post hiding is not complete associated-data deletion. Destruction can lose funds/authority; preserve authentication/backup safeguards. Independent copies cannot be promised erased; a disclosure alone is no retention exception. [Deletion scope](../research/PLAY_ACCOUNT_DELETION_SCOPE_2026_10_15.md), [Google FAQ](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en). |
| Configured dependencies | Issues16/31: authorized independent Giphy keys and live proof.32/33/34: real Firebase/GCP Android associations, gateway hosting/deployment and physical closed-app delivery.17: public YouTube playback unresolved.19: owner Registry unavailable. No provider workaround, node deployment or cloud action occurred here. |
| Final device and store result | Issue35: physical Pixel7Android17 update/wallet/local-data continuity and timing samples. Issue36: final candidate/declarations, account access, actual track installation/signing continuity and authorized submission. target36 is already implemented; recheck policy at actual submission. [Target API](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en). |

No Console account/app, cloud project, production gateway, legal policy, support
message, signing-key enrollment or store submission has been created. Keep36,
35 and the genuine dependent originals open. The owner authorized clearly
identified draft/placeholder implementation on2026-10-19. Continue independent
native preparation while keeping governing facts, final text, retention boundaries
and actual operations pending in OWNER_INPUTS.md. Placeholders do not complete
those gates; more than external paperwork remains.
