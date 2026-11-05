# Ticket #93: truthful selected-node block observations

Review base: `b0078e033d946dc8273bf26962b22278ea2e31c0`.
Final production and native feature source:
`3b1afd4fe15f142fb91afec25c6c1a96d1bf6fc0`.
Both source axes and final native reviews report 0 unresolved actionable findings.

The shared header reports the selected node's observed height. Connection details
use the shared fixed account window, retaining source, block time, last successful
check, older/missing/disconnected states and an interruptible recheck. Unknown
information is never fabricated as zero, and late responses cannot populate another
node. Block arrival does not complete an independent feed/archive refresh.

## Completed execution

| Platform | Complete node feature run | Methods | Viewport cells | Original movies |
| --- | --- | --- | --- | --- |
| Android 10 | [42115910068](https://github.com/robelene112/redsquare-android/actions/runs/42115910068) | 8 | 12 | 8 |
| Android 17 | [42115913790](https://github.com/robelene112/redsquare-android/actions/runs/42115913790) | 8 | 12 | 8 |

Both runs use the same final source. Each contains all availability, late-node,
held signed-feed, narrow/Pixel-like, and actual normal/zero-motion classes.
All original assertions, request bounds and 180-second class bounds remain.
Matrices cover 320/412dp, light/dark and 100/130/200% system text. Native action
receipts independently verify 48dp heights at 100/130%, 54dp at 200%, and a 48dp
close target. The details window retains its native geometry through status changes.
Actual system and window scales match 0/1. Reading settles before the original
four-second response, the header stays collapsed, and the exact reading anchor
remains after arrival. Raw results, native JSON, PNG/XML pairs, movies and every
decoded original movie frame are retained under each platform directory.

Independent Android 10 review inspects all 36 viewport PNGs, native bounds,
ordered samples from all eight movies, and every zero-motion old/recovered frame.
The prior open-dialog dropouts are absent. Android 17 review likewise inspects all
36 PNGs, 12 native receipts, ordered samples from all eight movies and every
zero-motion old/recovered frame, with 0 actionable findings.
Both source review axes, including the final fixed-window change, have 0 findings.

[Final source/debug check 42115917586](https://github.com/robelene112/redsquare-android/actions/runs/42115917586)
passes on that source. Core results reuse the valid Gradle cache (119 methods);
the gateway test task also reports FROM-CACHE. Its method-count report is not
uploaded, so no new count or fresh execution is claimed. Debug lint has 0 errors,
55 existing warnings and 1 hint. Separately, all 119 core tests were freshly
executed locally with 0 failures/errors/skips, including old public metadata
accepted while stale monetary funding remains rejected. That parser is unchanged.

Original shared-shell/header/menu/reading regression passes at `cd1f7d3ca` in
[42109805756](https://github.com/robelene112/redsquare-android/actions/runs/42109805756)
and [42109825905](https://github.com/robelene112/redsquare-android/actions/runs/42109825905),
5 methods per OS. Independent reviews sample all 14 original recordings per OS
and representative stills with 0 actionable findings. These earlier regression
runs support their unchanged routes; final node feature gates above cover the
subsequent details-window repair.

## Observation contract

Public `GET /options` publishes `blockchain.last_block_id`, `last_block_hash` and
`last_timestamp`. Inspected deployed source: `server.ts:752-758` delegates to
`storage.getClientOptions()`; `storage-core.ts:636-680` publishes
`app.options.blockchain` or the configured client-options override;
`blockchain.ts:41-49` saves the live core tip and `:128-130` updates it on new blocks.
Inspected server.ts SHA256:
`5cca9333856502e827a36a78ce87d2d2406d0914724b4a35cb860942e0800752`.

Public read-only observations are separate in `public-options-observations.json`.
Both approved HTTPS origins returned HTTP200 and compatible fields. At that
observation the owner's published block was about 17.5 hours old; the official
node's was recent. These are node reports, not independent consensus or global
lag proof. No node configuration, production, wallet or database change was made.

The reader performs bounded cancellable foreground-only 30-second checks on the
selected approved origin, without redirects or automatic node switching. It does
not repeatedly generate UTXO snapshots. Controlled fixtures do not initiate public
metadata reads or consume wallet-balance fixture counts. Payment freshness and
validation are unchanged. Old block time is distinct from stale successful-check
age. See the glossary's Node block observation entry.

## Failure provenance

- Snapshot tracer RED42099524913; precise-options tracer RED42100785834; first
  native height/details GREEN42102264768. Availability RED42104075126 followed by
  GREEN42105364120. Availability/boundary GREEN10:42107939021.
- Early 42109245127 reports a real 40dp Compose action and an invalid 12-second
  recording caller. Explicit 48dp controls and the existing five-second recorder
  contract resolve those. Superseded 42109628396 is cancelled, not accepted.
- Eee-source 42110457489/42110463009 pass seven methods each but remain failed:
  an old saved Done coordinate on 10 and a stale UiObject2 reading handle on 17.
  Current native targets and direct virtual-node snapshots preserve physical input.
  Interim 386 motion fails its working-target observer; 05f feature runs are cancelled.
- Native XML exposes 40dp clipped ancestors even after Compose 48dp checks pass.
  Reusing the established AppDialogWindow alignment resolves the native geometry;
  every final viewport cell additionally checks and records actual native bounds.
- 791-source 42112822206 passes seven methods but fails an immediate reading-anchor
  comparison during gesture movement. 42112826587 passes all eight on 17. Test-only
  2b positively observes 200ms of stationary reading before 3.5s, inside the unchanged
  four-second response, then retains the exact post-arrival comparison. Original
  failed action movies are now retained before the unchanged failure propagates.
- 2b motion42114263362 passes assertions but original zero-motion frames reveal
  open-dialog dropouts of approximately 21ms, 18ms and 34ms. Those movies and frames
  remain in `failures/zero-motion-42114263362/`; their source cause is unproved.
  The floating window changes size between healthy and status states. Final 3b
  reuses fixed AccountDialog and additionally asserts stable native window geometry.
  Actual intermediate frames, rather than endpoint geometry alone, decide visual
  acceptance. Both final platform reviews resolve the dropout finding.
- Earlier PNG/XML availability observations differ across separate capture calls.
  Cause is unproved. The observations remain separate from no-input captures and
  actual recordings; neither atomic capture nor an unrelated repaint fix is claimed.

## Limits

These are controlled owned-emulator/signed-peer checks, not physical Pixel,
public-delivery, provider or store acceptance. Focused success is not a complete
application pass. Issue #94 retains final-source whole-app checks, signed in-place
update and beta publication. Actual Stack archive/publishing/payment integration
and #1/#36/#55/#84 acceptance remain independent. No beta is published by #93.
