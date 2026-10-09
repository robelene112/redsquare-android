# Native following

Ticket #9, delivery slice 8 of 35, is in progress. Review base 6dd511d.
Parent specification is GitHub issue #1. Do not treat this report as acceptance.

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
core/debug/test assembly passes1m26s. Focused green37957941542 is running.
Partial author-result recovery, final source reviews and full verification
remain pending.
