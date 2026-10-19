# Content-first application frame58

Review baseaa40be525. Initial tracer sourceb84f472c2, Android17 run39326953921,
retains the actual RED:1JUnit failure in11.938seconds because no selected Home
destination exists. Instrumentation process exits0; the test result is FAILURE.
Original PNG/XML and the complete class output are retained without image edits.

First production slice introduces a compact shared header, content-first backup
notice shown on the home feed, an explicitly selected Home destination, consistent
primary navigation and an overflow for saved/queued publishing work. The identity/
node guard and existing draft-save navigation remain. Returning to Home restores
feed position; Search and own-profile state are retained by the scoped navigation
memory. Wallet and Settings stay reachable, and node context remains visible.

This is implementation in progress, not complete58 or app-wide UI acceptance.
Native green, theme/font/IME/motion/recovery walkthroughs, full regression,
two-axis review and signed UI delivery remain pending. The separate Node45 beta
is wording-only and is not delivery of this redesign.

The first production execution e50538e60/run39328611127 is also FAILURE:
Home selection now passes, but the post body begins below half the320px-wide
viewport. Original capture/root inspection confirms the remaining action row
uses reading space. This is retained as reading-space-red17, not green proof.
The next minimal slice65e1d6002 moves compose to a persistent native vector action,
keeps maintenance in the feed toolbar and reserves list space beneath the action.

First minimal GREEN:65e1d6002/run39329524115 completedSUCCESS, exactly the
first-use method PASS with one normal completion and actual exit0. Root opened
the original320x640 capture: post content is now in the upper half, Home selected,
Node/domain and import/backup visible. The same observation exposed truncated
feed tab captions, corrected in066e39e12 by using wrapping labels and tighter
internal padding. This is bounded first-use proof, not58 completion.

Early independent source review found two concrete follow-ups: the Home shortcut
mistook a one-item thread stack for a primary root, and profile/author forms lacked
a guarded save-and-transition callback for the newly visible tabs. Neither is
accepted as finished behavior. The next single tracer in066e39e12 covers direct
thread -> Home without silently dismissing the reminder. It runs before the Home
shortcut changes. Remaining font/IME/motion/profile-draft/full/signed work stays open.

Direct Home-return RED:066e39e12/run39330476503 completedFAILURE. First-use
still PASS; the second method times out at its unchanged10second return-to-feed
assertion after tapping selected Home. Root opened the original failure capture:
Conversation remains displayed. The fix now recognizes actual primary roots,
not any one-item stack. Wallet comparison in the fixture uses boolean equality
with a constant failure message, keeping full exports out of assertion output.

Home-return GREEN:729073be9/run39331425836 completedSUCCESS, exactly both
first-use and Home-return methods PASS with one normal class completion. The
10second return assertion is unchanged. Source re-review reports0 residual Spec
findings for the shortcut. The next tracerc937802cf covers unsent profile edits
before guarded form navigation is changed. Its new public profile metadata cache
prefix was corrected locally from profiledetails to the actual profilemeta name;
that cleanup correction will be included with the subsequent fix.

Profile navigation execution c937802cf/run39332362216 is FAILURE: first-use and
Home-return PASS; profile navigation times out at the unchanged10second feed-return
assertion. Original capture contains the unsent profile edit with the real IME
open. That capture alone does not establish whether the keyboard transition also
affected the tap. The independent source finding conclusively identifies the
missing guarded callback. The fixture now positively opens and closes the real
IME, verifies the exact edit and a displayed Home target before tapping; no old
assertion or timeout is removed. The corrected metadata cleanup prefix is included.

Profile and author editors now hold their existing busy/save exclusion through
durable persistence and guarded navigation commit, rejecting stale registrations
and pending profile pickers. Existing plain persist/Back behavior keeps its default
callback. Failed primary transitions show one brief non-queued recovery message,
cleared on successful navigation or renewed typing. No silent draft discard or
bypass of PostNavigation is introduced. Runtime green/review remains pending.

Guarded profile navigation GREEN: d958685f0/run39333368227 completedSUCCESS,
exactly3PASS with the real IME open/closed and original assertion deadlines. Root
opened the returned-editor original; the exact unsent edit is retained. Both source
re-review axes report0 residuals for this increment. This is not complete58.
The next native tracer exercises actual130%/200% rendering at320dp and a412dp
Pixel7-sized layout, both themes, full targets and scrollable backup guidance.
Disposable display/font/appearance settings have independent restoration. This
is layout verification, not a physical Pixel or performance result.

Viewport execution09c07837e/run39334261008 is FAILURE: the preceding3 methods
PASS; the new method fails at its first Wallet target bound assertion for320dp/
130% light. Its outer failure capture occurs after display/font restoration and
therefore does not establish the failed layout. Native XML in that restored state
shows a48x48 clickable Wallet target. No app cause is inferred. The fixture now
captures inside the actual matrix case before cleanup, retains visual and native
clickable bounds, and positively waits for the main window after theme selection.
The original minimum/viewport assertion is retained while diagnosing its signal.

For further viewport diagnosis, app-frame-viewport and app-frame-viewport-android17
select only the new larger-text method at the same native seam, with the existing
180second bound. App-frame and final full scopes retain the complete class,
including all preceding methods. This avoids rerunning the three unchanged green
journeys for every viewport probe; a focused pass is not complete acceptance.

In-case diagnostic806541b8f/run39335047387 is FAILURE. Wallet's actual130% record
shows40x40 Compose visual bounds and48x48 native clickable bounds, with320dp/
density1/font1.3 positively recorded. This confirms the observer mismatch; the
fixture now measures public touchBoundsInRoot and retains visual/native records,
requiring both touch and native minimum48dp. Root opened the original in-case
capture: backup wrapping and broken feed tab words also put the post body below
half the viewport. A full-width reminder heading and compact tab typography address
that separate observed presentation problem. No global app target-size change.

The same run has a second fixture failure: pressBack returnedfalse even though
the original capture shows the real IME closed. The fixture keeps the actual
10second IME-closed and exact-draft assertions, and waits for displayed Home;
the driver's Boolean is no longer treated as the user-visible result. This is not
reported as a successful application test. Root closing now precedes independent
font/display/appearance restoration, resolving the reviewed cleanup-order finding.
Standards re-review of3067735ea reports0 documented and0 heuristic findings.
Runtime verification remains pending in39335922124; focused viewport proof does
not replace complete58 acceptance.

Viewport3067735ea/run39335922124 is FAILURE at selected Home. Its actual touch
bounds57x80 are valid, but native isSelected=true/isClickable=false. Pinned
Compose1.11.4 intentionally omits ACTION_CLICK for selected Role.Tab nodes. The
observer now measures the positively selected, enabled native tab itself and
keeps the full48dp/viewport bounds; all other controls still require native click.
Root opened the original130% light capture: compact headings and readable feed
tabs put the post body in the upper half. Other matrix cases are not yet proved.

Source check39335922049 at3067735ea completedSUCCESS, including the workflow's
core/gateway/Debug/test/Release/Performance/lint checks. This is source acceptance
at that revision, not runtime or final delivery acceptance.

The separate plain-native motion observer deliberately has no Compose rule:
Compose1.11.4's test recomposer bypasses the production platform motion scale.
It records real scale1/0 with readback, actual Later/persistence/navigation,
original movies and decoded frames with actual sample timestamps. Functional
PASS alone does not prove motion; original before/intermediate/final positions
must be visually inspected. No custom image-matching oracle is used. The current
conditional reminder removal is still instantaneous; motion remains unfinished.

Viewport8104b860b/run39336828749 completedSUCCESS: the focused matrix method
PASS in73.61seconds, all8 independently checked actual display/font/theme cases,
full48dp native/touch targets and original guidance captures. Root opened6
representative originals. This is not complete58 or physical Pixel proof.
Visual review nevertheless rejects the320dp/200% import label: the tall pill
shape clips its first/last lines. The next slice uses a full-width import action
above Backup/Later at larger text sizes, with smaller rounded corners and vertical
text padding. Default/130% retain the compact row. Green functional bounds alone
were insufficient; revised rendered acceptance is pending.

The initial motion39337267196 at74b115b28 failed before compilation/runtime:
Google SDK download ended with curl92/HTTP2 stream error. It establishes no app
outcome. Setup now retries that transport failure with bounded request times,
keeping the exact SDK archive/hash pin. The recorder additionally awaits its
positively flushed native Content area message after encoder setup; actual movie
coverage still requires inspection. Both source review axes had0 findings for74b.

Source39337266799 at74b115b28 completedSUCCESS with the complete source/core/
gateway/Debug/test/Release/Performance/lint workflow. The next increment also
extends the original Home-return case through thread -> author profile -> Chat ->
Home, retaining its original direct-return assertions and reminder checks. The
motion observer now sets, reads and independently restores all3 system animation
scales, awaits real encoder readiness, and rejects reordered frame/sample pairing.
No existing class/assertion deadline changes. Complete frame runs on both SDKs
will exercise the revised large-text action layout and these native journeys.

The next bounded reading case uses the existing signed20-row fixture, scrolls to
reference05, opens Chat, returns Home, then recreates the Activity. It requires the
same visible post and vertical position, with reminder/selection and unchanged
wallet/backend/zero-send checks. This explicitly checks position across primary
navigation, in addition to the earlier refresh/recreation proof. Its separate
180second class keeps existing class budgets unchanged; complete frame/full
scopes retain it. Native and rendered acceptance remain pending.

Complete focused frame50babd77b:39338297823(Android17) and39338299700(Android10)
completedSUCCESS, exactly4 frame+2 plain-native motion methods perSDK. Independent
configuration checks confirm all8 viewport/font/theme cases on eachSDK. Root
opened the revised200% import originals on17 and6 representative10 originals,
including130%,200%,full wallet guidance,Chat and restored unsent profile edits.
The import label now renders fully. This is focused frame proof, not full app or
physical acceptance. The later reading-position case is separately pending.

Motion visual RED remains independent of functional PASS. Root inspected every
original normal/zero frame in17's before packet: with all3 settings1, frame4 at
312788us still shows the old position; frame5 at327933us jumps directly to the
final position80px higher. No intermediate reading position exists in the7-frame
recording. The zero-scale5-frame sequence also jumps, as expected. Source still
removed the entire conditional Surface immediately.

The next production slice keeps a180ms expand/shrink wrapper mounted only on
Home. Later disables the departing actions immediately and the height collapses
without blocking input. Leaving Home or opening the IME still removes reminder
chrome immediately; it does not overlap form-navigation guards. Compose's real
window motion scale controls the tween. Native normal/zero inspection is pending.

The independent full catalog is frozen before complete execution in
full-identity-reference.json:473/474 identities,122/123 normal completions,
34 deliberate interruptions and33 continuity records. It adds only the3 new
classes/7 methods to accepted38. No new omission or assumption is accepted.

Source39339456579 at6f94c4e8b completedSUCCESS, full source/core/gateway/Debug/
test/Release/Performance/lint. Motion10/17 runs39339460676/39339457242 each
completedSUCCESS with exactly2 functional methods, but root visual review rejects
reduced motion: actual settings0 still produces intermediate content positions.
Root opened representative original10 frames; a temporary read-only pixel replay
tracks the unique red selected-feed indicator, bound to its native tab width and
the independently observed80px post displacement. It exits1 for zero10/17 and0
for normal10, reproducing the exact captured motion defect in seconds. This is
an additional diagnostic, not a substitute for original visual review.

Ranked causes: a cached window scale surviving the previous normal test, an
unexpected test window context, or delayed platform-setting propagation. Pinned
Compose1.11.4 caches a WhileSubscribed scale StateFlow per application context
without a fresh settings read on resubscription, supporting the first hypothesis
but not proving it. The next unchanged-product probe records the installed window
context/scale only AFTER the original movie and runs the existing zero method
alone in a fresh process as well as the unchanged paired sequence. Neither a
fresh-only success nor functional PASS accepts the paired preference transition.

Reading13de2ab37/run39338696534 completedSUCCESS, exactly1 method in31.265s.
Root opened all3 originals: reference05 remains at the same visible position
before Chat, after Home return and after Activity recreation. All guarded state
and zero-send assertions remain. Full regression and signed delivery stay pending.

Diagnosis293f5cff4: fresh-zero39340836020 is1functionalPASS with actual Settings0
and window scale0, and no intermediate positions in its original frames. Paired
39340839780 is2functionalPASS but Settings0/window scale1 and intermediate feed
indicator positions166/150 between224 and144. Root opened the original intermediate
frame and fresh-zero endpoint. This confirms the pinned per-process cached scale
retains1 across windows. The paired normal movie's transition occurs beyond the
opening-second decoded window, so its sparse decoded subset is inconclusive for
normal motion. Complete movies remain original; the next observer decodes every
frame of the bounded3second recording to avoid that gap.

The regression now asserts the actual window scale after retaining the original
observation and checks0->1 as well as paired1->0. This extends existing methods
without changing their identities/deadlines. Production6f94 still fails the
measured1->0 case; no fixed runtime acceptance is claimed.

The production correction keeps standard AnimatedVisibility/layout and the real
Android dispatcher/lifecycle/frame clock. A once-installed Application factory
supplies a fresh per-window MotionDurationScale: register before read, observe
changes, refresh on resume and dispose on root detach or lifecycle destruction.
Normal setContent retains its default parent, allowing Compose-rule tests to
substitute their own factory/clock. This isolated seam explicitly opts in to
WindowRecomposerPolicy's InternalComposeUiApi in pinned1.11.4; revisit it when
upgrading that dependency. No global policy changes occur during Activity/tests.
Both source review axes found0 residual issues in this slice. Native fixed
execution, complete regression and signed delivery remain pending.

Observed RED39341602310 at e098e9b22:2 methods, one failure at the new unchanged
window preference assertion. After initial0, Settings1/window0 suppresses normal
motion; the subsequent0 method passes. The retained original observation proves
the inverse stale direction too. This is an actual app/test execution failure,
not a source-only prediction. The production correction and regression sources
compile locally (:app:compileDebugKotlin/:app:compileDebugAndroidTestKotlin, exit0);
fixed native acceptance is next.

Prepared46 (unpublished): canonical builder in the frozen worktree at9b33d3fef
completedSUCCESS. Actual APK SHA256311d82372566d2eac34e983838c4507b54d4d20ab0740fada1907d58bb95cdb5,
package nl.davidengelen.redsquare, version46/0.1.46, established6ca1209b certificate.
Independent APK inspection confirms nondebuggable/nonprofileable, backupfalse,
min29/target36, reviewtrue and push/PostsGIF/ChatGIF unconfigured. Both preparation
review axes0; all5 retained hashes verify. The before-publication snapshot retains
ordinary45 and performance43 unchanged. This is preparation only, not a live
version, current physical timing or provider/store acceptance.

Fixed focused acceptance at9b33d3fef:39342318884(Android10) and39342315882(Android17)
bothSUCCESS, exactly4 frame/1 reading/2 motion methods. PerSDK all8 real viewport/
font/theme cases retain48dp native/touch targets. Root opened current font, backup
guidance, IME, Chat and reading-position originals. At320dp/200% navigation/feed
captions wrap but controls remain reachable; no whole-app visual completion claim.

All3 recordings perSDK retain matching Settings/window scales0->1->0. Root opened
normal old/intermediate/final frames and zero pre/post frames on bothSDKs. The
normal indicator moves224->144 through198/180/166/156/150/148; both zero movies
have no intermediate positions. The independent original-frame replay passes all6
recordings. Full39343151613(10)/39343154589(17) now execute the same source with
the independently frozen473/474 catalog, public reads and immutable signed35/43
observations. Full outcomes and live46 remain pending.

Source39342315719 at9b33d3fef completedSUCCESS: core/gateway tests, native Debug/
instrumentation sources, Release/Performance assembly and lint.

Full10 run39343151613 at9b33 FAILED early in PublishingSurfacesPreviewTest:3PASS,
one private Chat failure at the unchanged20second visible-Unlike goal. Frame7
and the earlier agreement class passed, but remaining complete gates did not run.
The original failure PNG/XML shows forwarding status and a106px-high scrollable
history containing only the bubble; it does not establish the reaction label or
its offscreen bounds. No complete10 acceptance is claimed.

The next fixture probe records the unmerged Compose tree before scrolling, then
uses the existing20second waitForScrollable helper to assert the same Unlike
visibility, keeping every transaction/relay/identity/agreement assertion. This
distinguishes offscreen layout from incorrect reaction state. A bounded focused
surfaces scope runs the same4 methods with their original180second class bound;
complete agreement/full scopes retain all prior methods and recovery gates.
No production change or fixed result is claimed by this probe.

Pre-execution Standards review caught a diagnostic-only defect in e6db: modal
agreement captures can have multiple Compose roots, so a single-root print may
throw before the real assertion. The recorder now prints all roots with explicit
full subtree depth. This changes diagnostics only; no UI or acceptance condition.
The e6 focused attempts will be superseded, never treated as acceptance.
