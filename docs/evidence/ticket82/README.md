# Ticket82: shared verified profile pictures

Review base: `afcaa350208b045fa41dbe56280d56c1ea48e73d`.
Last production change: `1b39a2632`. Final verification sources: `a562b298d`(Android10) and `5246f83ab`(Android17).
Both final complete focused runs pass all12 methods per OS: [Android10](https://github.com/robelene112/redsquare-android/actions/runs/41858294363),
[Android17](https://github.com/robelene112/redsquare-android/actions/runs/41856926016).
The5246 observer change asserts two consecutive captured pixel matches within
the existing10s bound instead of taking a different screenshot after a wait
already succeeded. It also retains bounded pixel/bounds observations. The prior
fixture change gates successful HTTP responses until the native retry click.

## Delivered behavior

One selected-node avatar source serves feed/thread authors, people results,
Activity actors, private Chat contacts, public profile headers and the account
control. Unsent editor previews remain local. Discovery validates the existing
signed Saito Profile envelopes against the complete public key. It preserves
signed removals and cached snapshots through stale responses. Legacy partial
fields also invalidate an image when a late picture update arrives after a newer
biography update.

Identicons retain their original geometry. Available pictures fade into that
same space using the shared180ms token and actual system motion preference.
Changing node or identity resets outgoing pixels as well as discovery state.
A failed image leaves an identicon; the public profile exposes the existing
accessible Retry picture action. Cache write failure cannot crash the public
profile or prevent an observed signed removal reaching the display.

The shared source retains64 compact identities and at most16MiB of256px
ARGB8888 rasters. Public signed discovery metadata uses one64-entry/16MiB
store per node. Raster disk storage is OS-prunable, limited to64 files/8MiB
and384KiB per file. Keys include node, signer, signed version and source.
Existing2MiB encoded/1024px decoding and16MiB loader memory limits remain.
There are two concurrent metadata reads and two image loads; repeated rows
share results. A deliberate refresh during an old lookup retains one follow-up.

## Verification

See [native-results.json](native-results.json) for exact sources, run IDs and
outcomes. The final `avatars` and `avatars-android17` dispatches each require all
five classes with their separate unchanged180second bounds:

- Propagation:1 actual native feed/pixel test, first observed failing before code.
- Identity journey:3 methods covering the same synthetic signed identity across
  feed, thread, people, Activity and Chat, both themes, change/removal, offline
  recreation and selected-node cache isolation.
- Cache:2 native Android persistence/decoder methods, including same-URL version
  changes, node separation, signed removal and stale/foreign-signer responses.
- Recovery:5 methods covering HTTP failure and a real Retry click, an obstructed
  Android AtomicFile path, late legacy fields, an explicit in-flight refresh and
  three simultaneously mounted large signed inline pictures.
- Motion:1 plain native method with actual system/window scales0/1, real pull
  gestures, original movies and every original decoded frame. Last movie frames
  must contain the resulting picture/removal; row width/height must remain.

The original complete profile regression has16 UI methods per OS plus two actual
process deaths/recoveries. Its prepared-image, exact candidate, no-rebroadcast,
wallet/backend and public-bio/pixel assertions remain. Each workflow also runs
`:core:test`, `:push-gateway:test`, both debug APK builds and host-peer compilation.
The earlier local Android instrumentation compilation passed; the named final
CI builds establish compilation of the final source.

These are controlled emulator results, not physical Pixel performance or live
provider acceptance. No intermediate beta is published. Integrated full-source
app/update verification and signed delivery remain94 work.

The final10 large-image case records actual536870912bytes and declared512MiB.
Original normal/zero movies and all decoded frames are retained under each
platform's `native/`; `visual-review/` contains labelled crops with exact source
frame selections. Visual inspection confirms short blends in normal motion,
immediate replacement at scale0, and unchanged circular avatar dimensions.

## Failure provenance

- `41844701727` at66478e0c7 is the original failing feed pixel test. The first
  implementation's feed-only pass is41847785234 at7f4bdfae8.
- `41848270359` at8cd3a0b37 failed the new journey's initial feed: its fixture
  incorrectly treated an empty `field4` root filter as reply-only history.
  The actual archive contract distinguishes empty from nonempty thread filters.
- `41850457727` at a4dfff6e5 reached the custom picture through feed/thread/people
  and Activity, but expected an ownership caption without an available signed
  target. The corrected expectation is the product's neutral "Liked a post".
  Its node switch also omitted the official loopback override and expected an
  unprefixed node label. That failed switch performed public Official-node reads;
  it is not controlled isolation evidence. Passing switch cases bind both debug
  endpoints to the owned peer and require the actual prefixed node label.
- Original profile regression17 run41849267593 passed all16 UI methods, then its
  cold-candidate fixture rejected an additional avatar lookup for the retained
  reference feed's signer.04eb442f9 returns no profile only for that exact known
  foreign signer; every other request still requires the owner and original sig.
  Complete corrected regression17 run41851457733 passed, including both deaths.
- Runs41852024695/41852030658 at e577036d3 passed all11 functional methods, then
  the motion operator could not find a scrollable node in the one-post feed.
 6d6c61822 uses positively observed post/navigation bounds for the same real pull.
- Run41853726199 at6d6c61822 passed all12 methods on17. Its paired10 run41853720793
  passed propagation/journey/cache and four recovery methods; an automatic image
  retry succeeded before the test tapped its now-removed button.42575848e keeps
  successful HTTP responses behind that actual click. It preserves pixel and
  input assertions rather than making the click optional.
- The later17 run41855328757 at42575848e failed its removal observer while
  the final failure PNG already showed the identicon. The observer checked a
  different screenshot after its wait condition succeeded.5246f83ab asserts
  two consecutive captured matches instead and preserves the10s goal, last
  recorded-frame result, geometry and actual window-scale assertions.
- Run41856918685 at5246f83ab hit a16MiB ART growth limit while constructing the
  large signed fixture, before opening its Activity. This is the already
  documented API29 photo-fixture heap anomaly. a562b298d records the actual and
  declared limits and requires parity before fixture allocation, matching
  ChatImageHistoryStorageTest. It does not raise the heap, restart services,
  skip the large case or relax its visibility/source-size/retention assertions.
- Cancelled replacement runs are not passes. The earlier passing component
  partitions never replace the final whole focused dispatches or94 acceptance.

## Standards review

No open findings after the paired read-only review and incremental rechecks.
The mounted-image budget and legacy-field invalidation findings were fixed.
The final recheck also covered node/identity animation reset, bounded refresh
coalescing, temporary-file cleanup and the corrected native gesture.

## Specification review

No open findings after the paired review and incremental rechecks. Cache write
failure and movie coverage findings were fixed. The recovery-fixture exception
preserves ownership/signature checks; the motion operator preserves its original
color, timing, recording and geometry goals.

Final source-review totals: Standards0; Specification0. Native results are
reported separately and are never inferred from a clean review.
