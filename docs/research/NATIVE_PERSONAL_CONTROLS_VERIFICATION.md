# Personal account controls verification

Active ticket21, parent specification1, review base
99e58c6a49f5a10ee630f948ad3a18c9f5fb5592. The owner authorized sequential
implementation of35tickets and seams1–4; no additional grill is required.

Source contracts: NATIVE_DISCOVERY_MODERATION_CONTRACT.md and
NATIVE_MODERATION_CONTRACTS.md. Personal policy is distinct from backend
moderation. Mute hides authored posts and social notifications; block also
suppresses incoming Chat. Conversation mute remains separate. Policies belong to
the active public identity and apply across both approved backends.

First tracer: native Profile Mute confirmation explains effects, feed hides only
that author's posts, the same policy holds on the other approved backend and
fresh offline Activity. Settings management Unmute restores retained cached
history. Independently signed existing following/profile fixtures are the truth
source; real local binary controlled peers authenticate the actual wallet. Verify
same wallet/all client identities and zero transactions. Full-list absence checks
must search the complete list rather than pass because a post is offscreen.

TDD status: test and focused controls harness added first. Final isolated emulator
guard uses the maintained HARDWARE check in setup and cleanup; compilation PASS
1m20s. Native RED pending. No product implementation or acceptance claim yet. Tests run only
in the .dev isolated emulator. Native screenshots and hierarchies are separate
observations; preserve originals and primary failure diagnostics.

Implementation direction: separate encrypted identity-scoped local preferences,
without signing-wallet mutation. Preserve unknown imported wallet options; do not
reinterpret foreign moderator lists. Wallet-key import alone cannot recover absent
local preferences. Filter display without deleting verified cached history, cover
feed/thread/profile/search and quoted originals, retain innocent commentary and
access to account management. Load policy before displaying authored content.
Failed saves preserve previous choices; durable recovery must cover late failed
commits, not just pre-write obstruction.

Later vertical journeys follow the first result: independently saved Block/Mute,
mounted views and quoted-original privacy, real failed-save repair and cold
retention. Chat and notifications use this shared policy in their later tickets;
this ticket must not claim those currently absent features are implemented.
The tracker explicitly makes #21 a prerequisite for #24 notifications and #25
private Chat; each later ticket requires actual personal-control integration.
Finish with both source review axes, relevant complete Android10/17 acceptance,
release/lint and retained evidence before closure. No live moderation writes,
node operation, physical timing or new phone APK claimed here.

First native RED38149454797 at056d9a3:1FAIL29.531s at waitFor Mute account,
line70; source/core/debug/test PASS. Root views original blank PNG and parses XML
without app nodes: capture happened after ActivityScenario closed. End safeguard
assertions not reached. Durableticket20/first-red keeps raw log/originals/4hashes.
Fixture now captures inside each live Activity and waits for Profile first; repeat
corrected focused RED before adding product code. No visible-Profile/GREEN claim.

Capture-scoped fixture compilation PASS1m16s. No product source added. Corrected
focused Android17 RED dispatch next; successful first source push38149448583 also
passes core/debug/test/release/lint.

Corrected native RED38150093643 atd4bf054:1FAIL31.11s at missing Mute account.
Root views original native Profile bitmap and separately parses the hierarchy:
actual author/reference post/Follow/Refresh present, Mute absent. Durable
 ticket20/captured-red retains raw log/originals/4hashes. Both initial fixture
review axes0 actionable findings. Later assertions remain unreached.
First product tracer now implements only saved Mute, profile confirmation,
feed filtering, backend-independent scope and Settings Unmute. Block, other-view
filtering and durable late-save recovery follow further vertical native tests.

First Mute implementation compile PASS2m14s. Focused native GREEN attempt next;
no runtime acceptance asserted. No wallet options or transaction path modified.

First-product bounded source reviewd4bf054...0503aee: Spec0 new actionable
findings for the first tracer; Standards1 actionable documented-boundary finding.
Null personal scope hides all cached rows and indefinitely reports loading during
wallet recovery failure, conflicting with preserved anonymous public reading.
After the first GREEN, add a native recovery journey: without prior personal
records, unavailable wallet still permits public cached reading; with prior
personal records and unknown owner, withhold content with explicit wallet recovery
copy rather than risk exposing hidden authors. Prove through the UI using the
existing controlled filesystem-sync failure, not guessed owner/plaintext reads.
No closure claim; later slices and final two-axis/full acceptance remain required.
Focused first GREEN attempt38150876485 at0503aee is active.

First native GREEN38150876485 at0503aee:1PASS32.955s, all first-tracer
wallet/peer/zero-TX assertions reached. Root views3originalPNG/separate3XML;
 ticket20/first-green retains8hashes. No final #21 acceptance claim.
Next native tracer added test-first for the actionable anonymous-reading/recovery
boundary: actual wallet OS-sync failure with intact wallet bytes, public cached
reading without personal records, then saved-Mute owner unavailable gate and
same-identity recovery retaining the choice. No production recovery fix yet.

Recovery tracer compile PASS1m19s; focused Android17 RED dispatch next.

Recovery fixture/evidence review0503aee...560424e: Spec0 actionable,
Standards0 new hard violations/heuristic smells. Both independently verify the
first-green8hashes and original observations. The initial Standards source
finding remains open until recovery RED/fix/GREEN. Recovery RED38151508176
at560424e is active; no production fix added ahead of observed failure.

Recovery RED38151508176 at560424e:2reported/1PASS1FAIL30.592s; missing cached
root atline149 in empty list after positive wallet failure. Root inspects original
failurePNG and separateXML; ticket20/recovery-red retains4hashes. Later repair,
owner-gate and end identity/no-TX checks unreached. Minimal fix now loads explicit
anonymous policy only when no personal record exists; otherwise explains owner
recovery and withholds the content list. Known-owner policy loading stays gated.
No signing identity is guessed or changed. Compile/GREEN pending.

Minimal anonymous/owner recovery fix compile PASS2m23s; unchanged two-test focused
Android17 GREEN attempt next. No runtime recovery acceptance claimed yet.

Recovery source review560424e...7f5d796: Spec0 actionable; Standards prior
anonymous-reading finding resolved,0 residual hard violations/heuristic smells.
Both verify4REDhashes/original observations. Native unchanged two-test GREEN
38152142197 at7f5d796 is active. Source review is not runtime acceptance.

Recovery GREEN38152142197 at7f5d796:2PASS45.744s, both unchanged journeys/end
gates pass. Root views5originalPNG/separate5XML; recovery-green12hashes. Native
anonymous reading, saved-policy owner gate and same-wallet repair/mute retention
now proved on Android17 only. Next SINGLE Block tracer added before product code:
explained Block after intentional Mute, approved backend switch, offline Unblock
retaining Mute, fresh offline reopening and explicit Unmute restoring cached post.
Both approved sessions intentionally share one controlled binary peer; different
server histories were independently verified in the first Mute tracer. Native
Block RED/compile pending; no Block implementation or final acceptance claimed.

Block tracer first draft compilePASS1m26s. Before dispatch, strengthen the same
journey to temporarily Unmute while Block stays active: full-list author absence
must still hold, Settings shows Block without a saved Mute, then explicitly
re-Mute before switching backend/offline Unblock. This prevents Mute alone from
masking absent Block filtering. Recompile before the first native Block RED.

Strengthened Block tracer compilation PASS1m27s; first native Block RED next.
