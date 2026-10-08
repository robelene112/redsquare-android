# Existing Red Square profile features

Read-only inspection of deployed static sources under `/opt/saito/node` on
2026-10-08. No wallet or database contents were read and no services were changed.

- Red Square embeds shared `SaitoProfile` in `mods/redsquare/lib/profile.js:15`
  and exposes Posts, Replies and Likes tabs at lines 75 to 79. Editing is enabled
  in `mods/redsquare/redsquare.js:104`.
- Banner editing emits `profile-edit-banner` from
  `lib/saito/ui/saito-profile/saito-profile.js:444`. The separate Profile module
  uploads and resizes the image, then submits `{banner}` in
  `mods/profile/profile.js:45`.
- Bio editing emits `profile-edit-description` from `saito-profile.js:481`.
  Profile opens the editor at `profile.js:54`; entered text is submitted as
  `{description}` in `mods/profile/lib/ui/update-description.js:27`.
- Profile updates are signed `Profile/update profile` snapshot transactions,
  defined in `mods/profile/profile.js:362`.
- Custom avatars are supported by the Profile transaction schema
  (`profile.js:360`) and shared card (`saito-profile.js:512` and `:546`).
  Red Square updates its avatar cache in `mods/redsquare/lib/profile.js:150`.
  An avatar upload control was not found in the inspected Red Square, Profile,
  shared card, settings or header sources. Default avatars are public-key
  identicons (`lib/saito/keychain.ts:514`).
- Displayed names come from `keychain.returnUsername` (`saito-profile.js:91`
  and `mods/redsquare/redsquare.js:150`). It returns a registered identifier or
  `Anon-` followed by the first six public-key characters (`keychain.ts:590`).
  No distinct editable display-name field was found in the vanilla profile.
- Username registration belongs to Registry. It checks availability of
  `identifier + '@saito'` (`mods/registry/lib/register-username.js:54`) and
  creates a signed `Registry/register` transaction
  (`mods/registry/registry.js:363`). Validation is alphanumeric (`:371`).
  Registration is separate from editing a Profile or changing local text.

These findings establish existing source capabilities, not successful native
delivery. Registry availability and native interoperability require verification.
