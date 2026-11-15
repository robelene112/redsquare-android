# Feed refresh spacing, issue116

The owner's screenshot1000038067 asks for the refresh feedback to sit flush at
its upper edge. Product/native source is
`d6b8083941d126377bf9e9d1a3f77c8dec7cadc6`, reviewed against
`e577b31d93c1249c4f333511f1b2c7a15327176e`.

The existing transient overlay remains at the feed viewport edge with pointer
pass-through. Its8dp outer top margin and3dp shared status padding are removed.
Other status callers retain3dp padding. This does not reserve a new layout row
or claim that the overlay never covers list content. The agent's proposed
normal-flow row was rejected after native evidence showed it clips top-edge
reading text despite preserving its absolute pixel.

## Current acceptance

- Focused Android10 `43677904076` and17 `43677905863` succeed, with all six
  original refresh methods and both reading/applied-result methods per SDK.
  Native measurements place the strip exactly at both tabs-bottom and viewport-top
  in four scenes. Existing strict reading-pixel, physical post-tap, return,
  collapsed-header, pending/outage/recovery goals remain. The added expiry
  assertion retains the same pixel. Native screenshots were inspected.
- Standards and Spec reviews find zero remaining source/proof-method issues at
  this source. Independent complete17and signed-update evidence review also finds zero issues.
  Android10and public delivery review remain pending.
- Signed ordinary127-to131 updates `43680519365`/`43680521171` succeed. Both
  original ZIPs and all96 indexed raw/native originals are verified. Exact wallet,
  unsent draft, cached post, Light preference and separate debug identity survive.
  The frozen workflow permits baseline127; this is not a direct130-to131 proof
  or a separate pending Vault/Store module-observer claim. The inherited scope
  descriptor still says83; actual versions and downloads independently bind127/131.
- Complete Android17 primary `43678447389` succeeds. The qualified current-source
  assessor runs every inherited validator:318classes,251archives,831observed
  identities (826terminal plus5verified intentional starts),345normal completions,
  44deliberate interruptions and43continuities. Four native feedback scenes have
  zero gap. Its `native131-android17` packet retains the exact run and byte indexes.
- Complete Android10 primary `43687853067` remains pending in the standard
  Ubuntu24.04 pool after three unsuccessful10observations. No host-cause claim is made
  and no complete two-SDK acceptance is claimed yet.
- Signed beta131 is staged, not promoted. APK94,089,760bytes,
  SHA256`260634ed86a1487482e097611f11fa0a737a9ea7120bc43e3113ffc385f9ccc4`,
  established certificate`6ca1209bcbba661943f7b683d48b2f58493f13851c760c8d80d1521ac0ee79a2`.

## Whole-run validator

`source-profile.json` prospectively binds the reviewed product and exact native
app/test/core/script/build/workflow trees. `verify-full.py` admits only descendants
whose other changes are116evidence, requires its exact committed profile/adapter
bytes before CI creation, and hash-pins the immutable115assessor, contract and
classification erratum. Its five exact-match adaptations only admit this source,
redirect output, add the four geometry validations/provenance and label the new
result truthfully. All inherited native, archive, identity, recovery, preflight,
speech, motion, media and downstream validators remain unchanged. Two independent
reviewers checked this mechanism and its current bindings.

The new result is `CURRENT_SOURCE_PASS_WITH_INHERITED115_CLASSIFICATION` when
successful. It never claims the old115frozen collector passed. Terminal/assumption
identities and the five verified intentional process-death starts remain separate.
The old115packet and both accepted old snapshots are untouched.

## Unsuccessful observations and limits

`unsuccessful-focused.json` preserves the original-gap red and rejected normal-flow
observations. `unsuccessful-full10.json` retains the failed FileshareCancel class
from first complete10dispatch43678445624. The test did not locate physical Cancel
within its original5000ms notification-shade deadline; the transfer remained
SENDING and cause is unproved. Other partitions were canceled by the ordinary
same-scope concurrency rule when the new complete10dispatch started. No prefix is
accepted. Unchanged-source focused Fileshare43680228412 passes, providing diagnosis
support only. No class/action deadline or native assertion was weakened.

`unsuccessful-second-full10.json` records the different WalletImport failure in
43680230627: import feedback/address were verified, but its focused parent window
was blank after dismissal. The original shared15second parent-visibility budget
failed. Its cause remains unproved; focused import43683570303 diagnoses the same
unchanged methods and succeeds, providing diagnosis support only. Complete10run43683761408uses the already supported Ubuntu22.04
pool with unchanged source, images, goals and bounds. Earlier parents supply no
acceptance, and no forced redraw or test-clock alteration is used.

`unsuccessful-third-full10.json` records a different failure on Ubuntu22.04:
20creator refreshes under the unchanged method tracer exceeded the original15second
wait. That parent is not accepted. Focused Vault-reading43687862233diagnoses the
same original assertions and succeeds, with20traced refreshes in645ms. This is
diagnostic support, not a host-cause or physical-performance claim. Complete10run43687853067returns to the standardUbuntu24.04
pool at the same frozen source. No trace, refresh-count or timing goal is weakened.

A local lint attempt used changing source inputs and failed; a later redundant
local lint attempt was stopped after all six clean hosted build/lint steps passed.
Neither local attempt supplies acceptance. The signed stable-source release build
and its release checks pass. Complete hosted checks remain the acceptance gate.

Whole originals and temporary collection helpers remain outside Git under
`/mnt/storage/redsquare-android-artifacts/feed-notice-fix`. Collection hashes and
per-packet original indexes bind the retained bytes. No APK is committed.
Physical/OEM, real node/provider/schema/funding and Play prerequisites remain open
under36/OWNER_INPUTS. No node/provider/Play operation or public financial write was
performed. Parent1/36/55/84/86 and105 remain open.
