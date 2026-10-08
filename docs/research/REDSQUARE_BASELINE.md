# Remaining web baseline capabilities

Read-only static-source inspection under `/opt/saito/node` on 2026-10-08.
No runtime state, wallet material or live interactions were inspected.

- Red Square defaults to Curated (`mods/redsquare/redsquare.js:98`), with curated
  and unfiltered preferences exposed in `lib/ui/overlays/settings.template.js:62`
  and saved by `settings.js:112`. The inspected styles did not establish the
  `active-curation` class actually implementing the filter. No dedicated Following
  feed was found. Sidebar follow suggestions have placeholder event handling
  (`lib/sidebar.js:9` and `:43`). Native feed/following behavior needs its own
  explicit definition and integration verification.
- Reposting exposes optional commentary (`lib/ui/overlays/compose.js:63`).
  `buildRetweetData` adds the original signature, text and images (`:577`), then
  broadcasts a signed retweet (`:657`). Receivers archive RedSquareRetweet and
  update original statistics (`redsquare.js:1455` and `:1708`). An embedded-post
  renderer exists (`lib/tweet.js:206`), but a complete normal-feed quote-post path
  was not established in this initial deployed inventory. The
  [completed quote-post trace](REDSQUARE_QUOTES.md) confirms published upstream
  support and identifies differences in the deployed redesign. Quote posts are
  included in the release scope; the initial incomplete trace is not an exclusion.
- Author-only editing/deletion actions exist (`lib/ui/overlays/tweet-menu.js:78`).
  Text edits are signed (`compose.js:688`) and author-checked before archival
  metadata is updated (`redsquare.js:1871`). Signed deletion removes the archived
  original after author checking (`tweet-menu.js:219`, `redsquare.js:1959`).
  Application deletion does not erase every historical copy. A brief edit window
  mentioned in composer text was not enforced by the inspected menu/receiver.
- Chat can create named groups with selected contacts or initially no invitees
  (`mods/chat/lib/overlays/chat-manager-menu.js:623`, `chat.js:1034`). Shareable
  invites and recipient join transactions exist (`chat.js:1118`, `:1137`, `:2499`).
- Group actions include viewing members, leaving, renaming, muting and local
  conversation removal (`lib/overlays/chat-user-menu.template.js:7`). Admins can
  promote other admins and remove members (`chat-user-menu.js:121` and `:157`),
  despite an older single-admin comment. Admin rename broadcasts a group update;
  non-admin rename saves a local name (`:41`). Removal accepts admin actions or
  the member leaving themselves (`chat.js:1395`).

These observations do not prove working native behavior. Product scope decisions
remain in PROJECT_CONTEXT.md and the later approved specifications.
