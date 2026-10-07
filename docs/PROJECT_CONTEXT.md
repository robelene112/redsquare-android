# Project context

## Desired outcome

Build a professional, fast Android app for Saito's Red Square. Preserve familiar
Red Square navigation and social behavior for existing users. Turn speed and
polish into measurable acceptance criteria during the scope grill.

## Decisions already made

- Native Android in Kotlin. The UI framework and supporting libraries remain
  open; Kotlin does not itself settle those choices.
- iOS is deferred. A later iOS app would be a separate Swift implementation.
- User wallet creation, import and export are in scope. Wallet identity and
  local data must survive ordinary in-place app updates.
- Wallets are non-custodial: signing keys belong on the user's device. Never
  upload private wallet material to the VPS or GitHub.
- Preserve compatibility with existing Saito wallet and Red Square behavior;
  prove native signing and transaction interoperability rather than assuming it.
- Keep the ability to select `https://saito.davidengelen.nl` or
  `https://saito.io` technically possible. Shipping a selector and its placement
  remain undecided. Archives and moderation can differ between backends.
- Block production is deferred and is not an Android backend prerequisite.
- Use Matt Pocock's project-local skills. Implement tickets sequentially,
  without Ralph or parallel ticket implementation.

## Review and distribution goals

The owner reviews from an Android phone. Plan for a stable review link with
build notes and an installable update; screenshots or video can assist review.
Updates should install over the same application and preserve its wallet.
Signing identity, application ID, versioning, backup behavior and device checks
must therefore be settled before delivery automation is claimed complete.

Google Play is the intended public distribution channel. APK distribution is
also an option. Store accounts, eligibility and submission policy must be
verified against current official sources when release work begins.

## Decisions still open

- First-release scope: core social experience or full web feature parity.
  The candidate core scope is feed, threads, posting/replies, likes/reposts,
  profiles/following, in-app notifications and wallet create/import/export.
  This is a proposal, not an approved specification.
- Attachments, notification delivery, search, moderation controls and other
  individual features need explicit release boundaries.
- Wallet storage, recovery, import/export format and replacement behavior.
- Native Saito integration approach and any required backend adapter.
- Android version support, UI toolkit, architecture and performance targets.
- App name, application ID, release signing and review distribution mechanism.

## Specification authority

GitHub Issues contain approved specifications and implementation tickets.
This file preserves project-wide context; it is not a substitute for a feature
specification. On 2026-10-07 there is no approved app spec or implementation
ticket. Discover current work from the tracker and Git history in later sessions.
