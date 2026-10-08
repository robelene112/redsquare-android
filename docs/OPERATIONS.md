# Environment and integration references

## Project boundary

Source: `/home/david/projects/redsquare-android` on the live netcup VPS,
hostname `v2202609429510531338`, accessed as `david`.

GitHub: `https://github.com/robelene112/redsquare-android`.

The repo has a native Kotlin wallet/signature core, a Compose wallet screen and
an isolated Docker build. Signed wallet-foundation previews and build notes are
published at `https://davidengelen.nl/redsquare/`. Debug identity is isolated with
the `.dev` suffix. The diagnostic is explicit and debug-only. Live feed/import
and the completed phone review remain subsequent work. Check the handoff for
current verification and unresolved recovery checks.

The reviewed Android delivery requirement is ordinary in-place updates with
persistent wallet identity. Package/signing/version handling is implemented in
the review scripts; actual signed-update runtime acceptance is still pending.
See [review build operations](REVIEW_BUILDS.md). Keep signing secrets outside Git
and record their management without their values.

## Saito authority on this VPS

When accessing the Saito node or Snake, first read
`/home/david/snake-stickerbook/AGENTS.md` and its current operations guide.
For Saito-specific APIs and transaction design, also read
`/home/david/.codex/skills/saito-dev/SKILL.md` when available.

The app repository does not authorize node deployment. Changes to services,
production configuration, wallet state or on-chain transactions require their
own explicit authorization. Never copy private options, keys or database content
into this repository, issue bodies or tool output.

As inspected on 2026-10-07:

- Production node source/runtime: `/opt/saito/node`, service `saito.service`.
- Staging node: `/opt/saito-staging/node`, service `saito-staging.service`.
- Backend candidates: `https://saito.davidengelen.nl` and `https://saito.io`.
- The production node had block production disabled and was following the same
  recent chain as the checked Saito endpoints. This is a dated observation;
  verify readiness and freshness again before integration work.
- Red Square code is in `mods/redsquare/redsquare.js`. Its current browser flow
  relies on Saito transactions and archive/peer requests; inspected web routes
  are not a complete JSON API for a native app.
- Wallet behavior references are in `/home/david/snake-stickerbook/snake.js`
  and `web/wallet-loader.js`, alongside the local wallet import/bridge tests.
- The Saito runtime pin is documented by Snake's
  `scripts/saito-runtime.json`. Treat the deployed version as authority and
  re-read that pin when planning protocol integration.

Do read-only research first. Native signing, wallet export compatibility and
mobile feed access require explicit evidence. Wallet/signature reference tests
now exist in the native implementation; network/feed interoperability remains
work for the subsequent tickets.

## Agent workflow provenance

Matt Pocock's skills are pinned to commit
`f3fc5632f401156837ee3872f14fe33ccf1024ea` from 2026-10-07.
`.agents/skills.lock.json` records file hashes; `.agents/UPSTREAM_LICENSE`
preserves the upstream MIT license. Install/update only this repo's
`.agents/skills/` unless explicitly asked to affect other projects.
