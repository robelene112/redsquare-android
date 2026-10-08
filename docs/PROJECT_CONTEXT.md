# Project context

## Desired outcome

Build a professional, fast Android app for Saito's Red Square. Preserve familiar
Red Square navigation and social behavior for existing users. Turn speed and
polish into measurable acceptance criteria during the scope grill.

Performance target: on the owner's Google Pixel 7, a cold launch with cached
posts available reaches an interactive feed within two seconds. Show cached
content first and refresh in the background. Measure first-ever launch
separately; its target and network test conditions remain to be defined.

## Decisions already made

- First-release baseline: the core social experience, including feed, threads,
  posting/replies, likes/reposts, profiles, following, in-app notifications and
  wallet creation/import/export. Additional features are decided individually;
  selecting this baseline does not automatically exclude them.
- Users can edit their profile banner, bio and custom profile picture inside
  the app in the first release. Keep the generated Saito avatar as the default.
  Preserve compatibility with Saito Profile updates. See
  [the inspected profile features](research/REDSQUARE_PROFILES.md).
- Users can optionally choose and register a Saito username inside the app
  in the first release. Registration must not delay immediate first use.
  Preserve compatibility with Saito Registry; verify its availability and
  registration behavior for both supported backends.
- Attaching and viewing images in posts and replies are included in the first
  release. Attachment formats, limits and other media types remain open.
- The first release includes the Red Square GIF search and picker supplied by
  Giphy, for composing posts and replies. See
  [the inspected web media features](research/REDSQUARE_MEDIA.md).
- YouTube links in posts support playback inside the app in the first release,
  preserving the web experience's embedded videos. Playback begins only when
  the user taps; videos must not autoplay.
- Offline reading of cached posts and saving drafts are included in the first
  release. Posts and Chat messages explicitly sent while offline queue for
  automatic delivery when connectivity returns, with a visible Queued status
  and cancellation before sending. Saved drafts remain unsent until the user
  presses Send. This decision does not authorize queued SAITO transfers.
- Queued posts and Chat messages remain tied to the backend selected when
  the user pressed Send. Switching backends pauses those items until their
  original backend is selected again; switching must not reroute them.
- Show a small, non-blocking banner at the top with a red dot while disconnected.
  On reconnection, briefly show a green dot and "Back online", then hide the
  banner. Preserve feed position and composed work. Distinguish loss of internet
  from an unreachable selected backend so the banner describes the actual
  condition. Report recovery only when that condition has cleared.
- Product text must not use em dashes. Use plain punctuation throughout the app.
- Optional Android push notifications for replies and mentions are included in
  the first release, including delivery while the app is closed. The delivery
  infrastructure, permission flow and initial notification defaults remain open.
- New Chat messages also support push notifications, with controls to mute
  individual conversations inside Chat. Android system settings control the
  notification categories, accessible through a shortcut in the app. Categories
  distinguish replies, mentions, private chats and group chats.
- Searching for posts and people is included in the first release. Search
  coverage and behavior depend on the selected backend's available data and
  still need to be defined.
- Personal controls to mute or block accounts are included in the first release,
  alongside backend moderation. Their exact effects and persistence across
  backends remain open.
- Reporting posts for moderation is included in the first release. Report
  recipients and handling remain open; reporting is not an automatic removal rule.
- Saito Chat integration is included in the first release, preserving its place
  in the familiar web Red Square experience. It includes private one-to-one
  conversations and group chats. Remaining chat features, history restoration
  and interoperability requirements still need to be defined. See
  [the inspected chat integration](research/REDSQUARE_CHAT.md).
- Native Android in Kotlin. The UI framework and supporting libraries remain
  open; Kotlin does not itself settle those choices.
- Design the app to add further Saito modules, such as Saito Stack, without
  rebuilding shared wallet identity and backend connectivity. Red Square and
  Chat should have distinct feature boundaries. Stack is a future extension
  example; implementing it in the first release has not been requested. See
  [the extensibility decision](adr/0001-shared-saito-foundation.md).
- iOS is deferred. A later iOS app would be a separate Swift implementation.
- User wallet creation, import and export are in scope. Wallet identity and
  local data must survive ordinary in-place app updates.
- The first-release wallet also displays balances and supports sending and
  receiving SAITO. Sending SAITO and exporting the wallet require system device
  authentication using supported biometrics or the phone's PIN. Transfer
  confirmation, unavailable-authentication handling and error behavior remain
  to be defined.
- A fresh installation opens directly into the feed, creates a local wallet
  automatically, and offers a clearly visible import-existing-wallet option.
  Reading does not require completing a setup wizard.
- Users can begin posting and chatting without a mandatory backup step.
  Offer a dismissible backup reminder and clearly available export/recovery
  guidance. Seamless first use is an acceptance requirement: wallet creation
  must not introduce a setup wizard or block UI interaction. Actual cold-start
  timing and fresh-wallet delivery must be verified on Android. See
  [the inspected first-use prerequisites](research/FIRST_USE.md).
- Wallets are non-custodial: signing keys belong on the user's device. Never
  upload private wallet material to the VPS or GitHub.
- Preserve compatibility with existing Saito wallet and Red Square behavior;
  prove native signing and transaction interoperability rather than assuming it.
- The first release includes choosing between `https://saito.davidengelen.nl`
  and `https://saito.io`, with `https://saito.davidengelen.nl` selected by default
  on a fresh installation. Switching keeps the same wallet and identity without
  reimporting. These are the only selectable backends in the first release;
  users cannot configure a custom backend. Wallet imports must not silently
  add or select another backend. Feed switching behavior and control placement
  remain open. Archives and moderation can differ between backends.
- Keep the selected backend during an outage. Do not automatically fall back
  to the other server. Users can still choose to switch manually.
- Block production is deferred and is not an Android backend prerequisite.
- Use Matt Pocock's project-local skills. Implement tickets sequentially,
  without Ralph or parallel ticket implementation.

## Review and distribution goals

The owner's primary physical review device is a Google Pixel 7. Its installed
Android version has not been recorded. Plan for a stable review link with
build notes and an installable update; screenshots or video can assist review.
Updates should install over the same application and preserve its wallet.
Signing identity, application ID, versioning, backup behavior and device checks
must therefore be settled before delivery automation is claimed complete.

Google Play is the intended public distribution channel. APK distribution is
also an option. Store accounts, eligibility and submission policy must be
verified against current official sources when release work begins.

## Decisions still open

- Attachment details, notification delivery design, search semantics, personal
  moderation behavior, report handling, Chat feature scope and other individual
  features need explicit release boundaries.
- Wallet storage, recovery, import/export format and replacement behavior.
- Native Saito integration approach and any required backend adapter.
- Remaining backend switching behavior and selector placement.
- Android version support, UI toolkit, architecture and remaining performance targets.
- App name, application ID, release signing and review distribution mechanism.

## Specification authority

GitHub Issues contain approved specifications and implementation tickets.
This file preserves project-wide context; it is not a substitute for a feature
specification. On 2026-10-07 there is no approved app spec or implementation
ticket. Discover current work from the tracker and Git history in later sessions.
