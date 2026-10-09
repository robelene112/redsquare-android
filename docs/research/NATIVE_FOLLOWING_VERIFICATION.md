# Native following

Ticket #9, delivery slice 8 of 35, is complete at reviewed source6d5d9da
against base6dd511d. Parent specification is GitHub issue #1.

Follow choices are local public account identities associated with the active
wallet, shared across the two supported backend sessions. They are separate
from signing material and public-post caches. No on-chain follow transaction
or cross-device synchronization is claimed. The interface says choices stay
with this wallet on this device. Reading and caching use the selected backend.

The selected backend Archive accepts exact field2 criteria, not an author-array
or arbitrary SQL predicate. Following reads use RedSquare, field4 empty,
flagged_ne1, field2 author, a created-earlier cursor and limit20. Signed decoded
authors must match the requested followed identity; archive labels alone do not
authorize content. Available results merge with bounded public post history.

The deployed public Archive source was read without network, write access,
application initialization or private files on 2026-10-09. Its SHA256 is
e1bd400f035979461070a34b9887d09b50866417057d06940661b78119d80743.
Named Git-object reads at the previously documented node Git directory failed
with "not a git repository"; no Git/config/runtime repair was attempted. This
source observation is a deployed-file hash, not a newly verified Git pin.

Independently signed scalar1/scalar2 post fixtures use the existing pinned
reference-only WASM/Elliptic runner, network disabled. See
NATIVE_FOLLOWING_REFERENCE.json and following-reference.cjs. No production
wallet or database is read, and no transaction is broadcast.

## Actual implementation check

Initial test commit e2232bf had a missing AndroidBackendStore import and failed
local compilation. That is a harness/build failure, not a functional red.
Correction 8e77779 passes local core/debug/test assembly. Actual Android10 run
37947832357 then reaches the profile and fails at the missing Follow action,
line47. Public image/log are inspected and retained in
docs/evidence/ticket08/following-red/api-29.

The first implementation adds the follow action, a Following view, and shared
bounded atomic storage of account identities. Existing post-cache file formats
are retained. Confirmation waits for checked persistence; writes and reads run
off the main thread. First functional green at3caa31a passes on Android10/17 in
runs37949419777/37949425181, 7.004/24.019 seconds. Public logs and empty-after-
unfollow images are inspected and retained in ticket08/following-green.

The second app case at40136c5 follows on the owner's backend, switches to
official with different signed content from the same followed author, excludes
the other author and owner-only history, closes official, recreates and requires
its cached followed history plus an outage message. Returning to owner must
restore its separate history while keeping Unfollow. Two-case Android10 run
37950206054 passes both cases in9.387 seconds. Public official cached-followed
feed/outage image and log are inspected and retained in backend-recovery-green.

A third app case obstructs only the active synthetic wallet's public follow
file after loading, requires the failed action to remain unconfirmed, removes
the filesystem fault and retries successfully. Three-case Android10 run
37951108934 at65e4ba5 passes all three cases in11.747 seconds. Its public
unconfirmed-action/error and successful-retry images/log are inspected and
retained in save-retry-green.

A fourth app case at72530fb requires a newer followed-author post absent from
the general page and excludes a signed post from another author even when its
archive field2 claims the followed identity. Four-case Android10 run37951927215
passes all four cases in12.586 seconds; public author-history image/log inspected
and retained in author-history-green. Production source remains3caa31a.

Fifth case atc14246a checks wallet isolation at actual MainActivity launches,
using an isolated-emulator-only synthetic scalar2 wallet filesystem fixture
between cold launches. Its choices must be separate; restoring the outgoing
wallet must recover its prior choices. Wallet replacement UI was verified in
ticket#7; this case does not claim a new end-to-end import UI flow or physical
process death. Five-case runs37952728041/37952732559 and both final source-review
axes were run as recorded in ticket08/INTERIM_REVIEW.md. Both five-case runs
failed compilation because the fixture called nonexistent importJson. Corrected
fromBackup runs37953412336/37953417415 pass all five cases on Android10/17;
public logs and identity images are inspected/retained in identity-green.
This build failure is not a
functional red. Review found general-cache eviction of older followed results,
missing retry after initial choices-read failure, and generic loaded values
misnamed envelopes. Sequential author requests can also discard earlier verified
results after a later request fails. These source issues need fixes, observable
regressions and repeated review. Sixth actual-app regression atc3b88f5 seeds
only the public general cache with100 independently signed newer unrelated
roots after establishing a follow through the UI, then requires available older
followed content after a cold launch. Run37954356779 on Android10 is an
actual retention red passes the first five cases and fails at the followed post
after seeding100 newer unrelated roots (line316). Its inspected image says no
available posts despite the valid response; public log/image retained retention-red.
Minimal115fc7a separates Following history by wallet/backend and applies active
author membership before retention. Local core/debug/test assembly passes1m23s.
Fresh setup58574e0 additionally clears followingfeed fixtures. Six-case green
37955584059 at58574e0 passes all six cases on Android10 in16.971 seconds.
Public log/image inspected and retained retention-green.

Seventh case at5d582bc restores a temporarily unreadable public follow file and
requires recovery through a visible Retry without another restart. Actual
red37956724460 passes six cases and fails at the absent Retry node (line368).
Public log/image inspected and retained load-retry-red. Minimalcc71cb9 adds
explicit load state and shared error/Retry UI in feed and profile. Local
core/debug/test assembly passes1m26s. Focused green37957941542 passes all seven cases on Android10 in23.684 seconds;
public log/recovered Unfollow image inspected and retained load-retry-green.

Eighth case atcbfa5d9 follows both fixture authors via actual profile actions.
The first author supplies a valid newer post unavailable in general/profile
responses; the second supplies malformed archive JSON. The first result must
remain available immediately and after recreation with the peer closed.
Actual red37958609488 atcbfa5d9 passes seven cases and fails waiting for
the newer first-author post (line426). Inspected image shows server error and
empty Following; public proof retained partial-results-red. Minimal e9aa1fb
publishes and caches each verified page before continuing to the next author.
Local core/debug/test assembly passes1m22s. Actual green37959292042 passes all
eight cases on Android10 in19.051 seconds. Public in-memory and offline cached
images/log inspected and retained partial-results-green.

Review refactor74b515f names generic Loaded.values and save values accurately
without changing public cache formats. Both final source axes initially reviewed this SHA
against6dd511d: Spec0; Standards0 hard and one low duplicated test setup
heuristic. Refactor6d5d9da extracts only asset-reading helper, preserving all
peer/UI/assertions. Both axes repeat at6d5d9da. Final full37960235937 is running
with approved public reads. Earlier37959992336 was superseded/canceled during
build so acceptance verifies the final source and test cleanup.
Final results are recorded below.


## Final acceptance

Full run37960235937 at6d5d9da passes core15, debug/test/release assembly and lint.
Each SDK passes55 completed instrument cases (53 top-level plus2 recovered
process cases), with2 additional intentional process-kill stages. Following
passes all8 cases in19.341/44.848 seconds on Android10/17. Public proof and
representative Following/recovery images are inspected and retained final-full.
Wallet/cache cold launches, authenticated export/import recovery, update notice
and signed5-to-8 identity continuity pass. Both final source axes report zero
hard/heuristic/actionable findings; see ticket08/REVIEW.md.

Separately enabled fresh native public reads pass for both approved backends,
including semantics assertions of each selected-server label. Official bitmap
captures nevertheless display the owner's label on both SDKs. Cause is
uninvestigated; those bitmaps are not visual proof of the official label and
are excluded from retained visual acceptance. Controlled backend-switch and
Following screenshots show correct official labels and isolated histories.
No false visual, chain-inclusion, physical performance or closed-app push claim.

No production node/config/wallet writer changes, live broadcasts, private
uploads or new review APK publication. Pixel7 acceptance remains ticket#35.
