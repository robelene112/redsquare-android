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

Complete17 run39343154589 at9b33 endedFAILURE in all3partitions, never accepted:
wallet has the same publishing-surface visibility failure as10; chat records
6PASS/4FAIL in ChatKeyboard at its obsolete reminder-in-Chat assertion after IME
dismissal; social records2PASS/1FAIL in Giphy because native image bounds
[16,456][304,636] require bottom+8=644 outside the640px screenshot. Earlier gates
passed but later gates are missing. Original failure packets retain these limits.
The Giphy harness has no separate adb-exit record; its3 complete method statuses
and normal instrumentation terminal marker are retained without inventing one.

Keyboard's corrected expectation keeps backup guidance absent in Chat and the
existing hidden=false persistence check, with every real-IME, exact-draft, physical
Send, group-membership and transmission assertion unchanged. The existing frame
journey verifies the reminder reappears on Home without dismissal. GIF's observer
positions the same image via its nearest vertical scroll ancestor only when the
original outside8px samples would leave the bitmap. It records initial/positioned
bounds and preserves every animation, attribution, original-URL, draft and pixel
containment assertion. These are test-only changes, not fixed runtime acceptance.

Private-surface probe eca374c37 completedSUCCESS on39344505651(10)/39344508913(17),
4PASS each. The pre-scroll unmerged tree has Unlike at y362..382 below the history
bottom322; the post-scroll tree has y292..312 and original PNGs show the action.
The XML-only replay passes10 but returns missing child for17's separate dump;
its original PNG and Compose tree show the action. These observations are not
atomic, so the XML gap is not relabelled as an app-state failure. The next final
capture positively waits for native Unlike before dumping, without dropping any
UI assertion. Private transmission, relay, agreement and identity assertions pass.

Complete agreement runs39344699117(10)/39344701551(17) at eca bothFAIL at group
reaction's policy-dialog goal:6other group methodsPASS; the Like target is below
the short history in the original failure scene. The next fixture scrolls only
the reaction target before clicking, and Unlike after forwarding. Every policy,
cancellation, transaction, relay, wallet and membership check remains. This is a
bounded response to actual RED, not a blanket change to disabled-state checks.

Focused keyboard39345370402(10)/39345373579(17) and GIF39345377196(10)/
39345380431(17) at aa08dd7b6 all completedSUCCESS. Keyboard has exactly10PASS perSDK;
GIF has exactly3 previewPASS perSDK and the scope's posting class also passes.
Original keyboard captures retain authored work after actual IME dismissal and
empty editors after explicit sends. GIF's initial456..636 bounds move to444..624
by12px, preserving the same outside8px samples, animation and attribution checks.
Root opened representative keyboard originals on bothSDKs and the17 GIF before/
after originals. These focused results do not replace full acceptance.
The GIF metadata write inba3 follows capture, which creates its owned folder,
removing accidental test-order dependence. No production/build/signing inputs
differ from signed46/source9b33.

Fresh full39346126335(10)/39346130915(17, all3partitions) execute exactba3ad0240
with public reads and immutable signed35/43 observers. The original independent
473/474 catalog is unchanged, with new source/run bindings recorded before these
outcomes. Previous failed9b33 attempts remain separately recorded and FAILED.
Complete agreement39345992170(10)/39345995233(17) also runs atba3 with the targeted
group-reaction scroll. Their outcomes, complete58 acceptance and live46 remain
pending.

Complete agreement39345992170(10) atba3 FAILED later in the two larger-text Chat
queue cases,2PASS/1FAIL each. Both wait atPublishingQueueFixture:172 for the old
backup reminder insideChat. The original PNG/XML shows reachable cancellation,
queued intent and offline warnings. Group7 and surfaces4 nowPASS, including the
targeted reaction scroll and positive native Unlike capture. Their retained class
results do not change the overall failure or establish missing later gates.
The next test-only correction asserts reminder absence plus persistedhidden=false.
The two ChatQueue cancellation fixtures contain the same obsolete reminder-positive
expectation; they receive the same scoped correction. Every original worker,
signature, warning, physical48dp target, cancellation and deadline check remains.
Home reminder-positive assertions remain intact. No production change.

Android17 complete agreement39345995233 has the same two queue expectation
failures at172; group7 and surfaces4PASS. All four class packets retain actual
results and original bytes. The overall17 run remainsFAILED and later gates
are absent. The correction is test-only, with both review axes0 for its scope.

The incompleteba3 full pair39346126335/39346130915 was deliberately cancelled
after the focused agreement pair proved obsolete reminder assertions on bothSDKs.
This avoids repeating known-invalid expectations. It is not accepted or converted
toSUCCESS. Fresh full39347038831(10)/39347042348(17, all3partitions) execute exact
3cc6363c9 with the corrected fixtures and unchanged app/build/signing inputs.
Source/run bindings were updated before their outcomes; the independently frozen
catalog, public reads and immutable signed35/43 observer inputs are unchanged.
Corrected complete agreement39346896065/39346898616 and focused cancellation
viewport39346900869/39346903466 also run at3cc. No new outcome is claimed yet.

Corrected3cc acceptance: complete agreement39346896065(10)/39346898616(17) both
SUCCESS, matching all27 independent accepted38 identities perSDK (25 ordinary
methods plus2 process recovery/restoration methods). All8 class exits are0;
original checkpoints prove distinct interrupted/recovered PIDs10160/10264(10)
and5763/5841(17), the same wallet digest/revision/unsent text and deliberate
publication only after recovery. Each packet retains147 original members,
including62 PNG and62 XML. This is controlled policy/recovery proof, not actual
owner policy approval or complete app regression.
Focused cancellation viewport39346900869(10)/39346903466(17) bothSUCCESS, exactly
2 original methods perSDK. Root opened original warning/cancel/reopened screens
on bothSDKs and larger-text agreement cancellation/restored drafts. Native48dp
physical cancellation, exact drafts, original candidate identities, warnings and
recovery assertions remain intact. Whole-app full39347038831/39347042348 and
signed46 publication remain pending; production remains9b33-equivalent.

Full10 run39347038831 at3cc FAILED later in ChatPushCapacityPreviewTest. Its exact
100-group notice expected "this server" while the actual original PNG/XML shows
"this node", as required by56. Earlier frame, agreement/recovery, keyboard and
alert gates passed; later full gates are absent, so no full acceptance. The one
failed method and six original group-capacity members are retained separately.
A literal-only fixture correction uses the required node wording, preserving
all101-group/category/subscription/history/draft/signature/choice assertions and
the original10second notice goal/180second class bound. Its wallet equality check
uses a constant-message Boolean assertion instead of dumping export values.
An independent Spec audit found the same active stale server-wording expectation
in ReportPreviewTest's unavailable/reopened report case; that literal alone is
corrected. Generic publisher server-data text, protocol variables and intentionally
historical transfer-message alternatives remain unchanged.
Full17 run39347042348 has a failed Chat partition; its remaining partitions are
still running and not accepted. No app/build/signing changes since9b33.

At replacement dispatch, GitHub's per-scope concurrency automatically cancelled
the older unfinished full17 social/wallet jobs. Run39347042348 is therefore
actuallyCANCELLED, with its Chat partition actuallyFAILED at the same capacity
literal; both distinctions are retained. The six original17 capacity members
positively show the required node notice and retain its failed method. No normal
completion or app-wide acceptance is inferred for cancelled partitions.
Fresh full39349228406(10)/39349230920(17, all3parts) execute exact6388840182 with
public reads and immutable signed35/43 observers. The frozen473/474 identity
catalog is unchanged and the source/run binding was recorded before outcomes.
Focused capacity39349185535/39349188302 and reports39349191022/39349193931 run at
the same source. No production/build/signing differences from signed46/source9b33.

Focused node-wording correction at6388840182 is nowGREEN on bothSDKs: capacity
39349185535(10)/39349188302(17) each1PASS, retaining eight original capacity members
perSDK. Actual101-group pause and automatic resume preserve other categories,
subscription identity, history and draft. Root opened17 paused/10 resumed originals.
Reports39349191022(10)/39349193931(17) bothSUCCESS with all12 independent accepted38
identities (11 UI methods +1 actual cold-process recovery method). Each retains90
original members,44 PNG/44 XML. Original checkpoints prove distinct interrupted/
recovered PIDs4250/4332(10),5040/5118(17), preserving the exact signed report and
requiring explicit retry. The old harness has no separate UI adb-exit record;
complete normal terminal results and actual process continuity are retained,
without inventing an exit record. Root opened bothSDK reopened-not-sent originals
and17 repaired acknowledgement; wording and destination remain truthful.
Complete full39349228406/39349230920 and live46 remain pending. No app/build/
signing inputs have changed since9b33.

Full10 run39349228406 at638 FAILED in the social-push command:10PASS/1FAIL.
The Settings-reply journey aborts immediately in waitFor:503 after the native
notification tap because fetchSemanticsNodes requires at least one current
Compose root. The15second deadline is never reached. Original failure PNG/XML
shows the retained latest unsent draft/photo/IME, not a completed notification
route. A transient root gap is a hypothesis until a complete corrected journey.
The next test-only helper polls with atLeastOneRootRequired=false within the
same15second goal. It adds no catch-all or longer timeout and retains exact
Conversation/target/draft/photo/signature/choice assertions. Both source review
axes0. The failed run and all90 original push members are retained. Full17
39349230920 continues at638 and has no complete result yet; it is left running.
No app/build/signing change; signed46 remains prepared and unpublished.

Test-only092238769 polls for the Compose root within the unchanged15second goal.
Focused social-push runs39350663269(Android10)/39350665509(Android17) and fresh
full Android10 run39350667797 execute that source. Full Android17 run39349230920
continues unchanged at638; dispatching a10-only full scope does not cancel it. PerSDK source/run bindings were recorded
before outcomes. All app/build/signing inputs remain9b33-equivalent; the catalog
is unchanged. The fixture-only split-source acceptance exception can apply only
after both complete SDK suites pass. There is no complete17 acceptance yet.

Full17 run39349230920 at638 has a completedFAILED Chat partition: ChatGiphy's
six methods record5PASS/1FAIL. The reply method times out at the existing10second
visible Add GIF goal. Original capture retains the reply quote, exact draft and
Add photo; it does not show Add GIF or prove its offscreen bounds. Parent status
was stillin_progress when retaining58 original members; wallet/social outcomes
are not inferred. The next fixture scopes Add GIF to the existing composition
preview and scrolls only when it is not displayed, then requires the same10second
visible goal and actual click. Reply-only before/after native observations and
full-depth all-root Compose trees will distinguish clipping from absent state.
Animation, attribution, quote, saved work, queue/cancel, original URL, signed
transmission and all deadlines remain. No production change or green claim.

The observed17 Chat failure means the existing638 attempt cannot provide complete
acceptance, regardless of its unfinished partition outcomes. A fresh17 full run
is required after the GIF fixture correction. The pending092 Android10 full run
continues; no result is inferred. The perSDK binding records current attempts,
not completed acceptance.

Focused social-push39350663269(Android10)/39350665509(Android17) at092238769 both
SUCCESS, exactly11 main-commandPASS perSDK, plus the scope's binding and worker
commands. Original10 Settings-route Conversation and returned exact draft/photo
captures were opened by root;17 returned-draft PNG also shows the exact latest
text and photo. Its separately captured XML still shows Conversation, so that
non-atomic XML observation is not positive restored-draft evidence. Exact Compose
assertions pass; original PNG/XML are retained without treating them as atomic. This demonstrates the
complete corrected journey within its existing deadline, without measuring the
transient root gap or claiming actual Google delivery. Full acceptance remains
pending. No production change.

Focused Chat GIF39351423400(Android10)/39351426372(Android17) atcae bothSUCCESS,
all6 original methodsPASS perSDK. Each retains72 original members, including
before/after native PNG/XML and full-depth all-root Compose trees. Both original
trees show preview329..442 with Add GIF445..485 before scrolling, then402..442
after43px scroll. Root opened both original after-scroll PNGs. This proves the
viewport explanation for the retained reply failure; all animation, quote,
credit, draft, queue/cancel, URL/signature assertions and180second class bound
remain. These focused passes are not complete regression or provider acceptance.

Both preceding full attempts are now completedFAILED:10 run39350667797 at092
reached the same prior ChatGiphy reply Add GIF goal (5PASS/1FAIL), retained in
ui58-chatgif-red10-39350667797.17 run39349230920 at638 has ChatFAILED and both
wallet/socialSUCCESS; all original partition archives are retained externally.
The pending-parent snapshot in the earlier17 failure packet remains an accurate
historical capture. Fresh full39352373816(10)/39352376831(17, all3 partitions)
execute3abeecd35 with current source/run bindings recorded before outcomes.
Expected473/474 identities,122/123 completions,34 interruptions and33 continuity
gates remain frozen and unchanged. No production/build/signing change since9b33.

The3abe complete attempts now reach private queued-photo cold recovery:10
39352373816 isFAILED,17 39352376831 has ChatFAILED, walletSUCCESS and social
still pending. The existing20second photo visibility goal at assertPhoto:216
fails after exact relay/text/history/wallet/backend assertions. Native17 failure
shows the cached first message, with private history216..370 and no visible
photo. Paired ui58-photo-red packets retain original interrupted/failed recovery/
successful restoration/checkpoint and4 original native members each. Their26
hashes retain actual parent states, including17 pending at capture. The retained
failure-stage text is a preparation marker, not a recovery trace.

The next test-only change applies the existing bounded index1 group-history
scroll to completed private photo history. Before death, private queued photos
remain in the composer and keep their existing pixel assertion there. All exact
raw/Relay/recipient/text/history/wallet checks and120second phase bounds remain.
Before/after native/all-root semantics will test the offscreen hypothesis. New
chat-photo-process scopes run both existing private/group photo protocols; full
and images scopes retain all. Source review axes0; no corrected runtime claim.

Focused photo process39354696326(Android10)/39354698834(Android17) at077 both
SUCCESS. Each matches all4 independent accepted38 normal-method identities,
with2 deliberate process deaths,2 continuity gates and original restoration.
Private interrupted/recovered PIDs3448/3715(10),2929/4426(17); group4052/4470(10),
4683/4760(17). Exact original raw/Relay once, recipient, decoded photo/caption,
cached history, wallet/backend and pixel assertions retain120second phase bounds.
Each packet has38 hashes/20 original members, including full-depth all-root
Compose trees and original native PNG/XML. Root opened final private/group PNGs
on bothSDKs: red photos visible. The newly composed private photo follows targeted
scrolling, with Compose bounds252..432 against history216..370.10 final native
XML confirms visible252..370; its immediate after-scroll observations remain old.
17 separately captured nativeXML still omits the photo, including final XML.
Those stale observations are retained and are not positive photo evidence.
Photos remain partly clipped in these short history viewports; complete Chat
viewport polish belongs to62. Passing exact pixel/continuity checks and final
positive PNGs establish recovery/visibility, not provider or full acceptance.

Preceding3abe full10/17 attempts completedFAILED at the old private-photo goal;
17 wallet/socialSUCCESS and ChatFAILED. Current full39355334984(10)/39355337570
(17, all3 partitions) execute077, with source/run bindings recorded before
outcomes. Independent473/474 identities and all existing deadlines remain.
Source39354689887 at077 passed core/gateway, Debug/test, Release/Performance and
lint. All app/build/signing inputs remain9b33-equivalent; signed46 is still
prepared/unpublished and ordinary45 remains live.

Complete077 runs39355334984(10)/39355337570(17) are bothFAILED. Group promotion
preparation times out at the existing120second host bound on bothSDKs. Original
interrupted output has START only, no completed preparation checkpoint or useful
native image; capture exit0 does not validate the unavailable/malformed tar.
Restoration method1PASS remains separate from failed preparation. Paired
ui58-group-stall-red packets retain each7 hashes, including the actual unavailable
archive and selected original host traceback. No progress/cause is inferred.

17 wallet independently matches all76 accepted38 identities and public reads/
native compatibility/update noticePASS. Its later ci-review-update rejects a
legacy APK checksum before signed-update continuity/final diagnostic. The failed
runner bytes/headers are absent. ui58-update-download-red17 retains12 hashes,
selected host failure, exact identities/raw log inventory and separate VPS
observations: local immutable5/8 files and new HTTP200 downloads match manifests.
Those separate observations do not prove what the runner received or a transient
cause.17 socialSUCCESS. Because Chat also failed, this whole attempt cannot
satisfy the release-tail-only exception.

Diagnostic-only5eb9fb09e adds constant stage/monotonic-time records and pre-scroll
native/all-root trees to the existing group recovery. The host retains stages
and stops only timed-out debug-emulator instrumentation before restoration.
Original120second phases and every acceptance assertion remain. The legacy
download observer records public HTTP headers/byte count/hashes before the
unchanged checksum check; no automatic retry or relaxed validation. Both source
review axes0. Focused group-admin39358679469(10)/39358681910(17) execute5eb; no
corrected outcome or complete acceptance is claimed. App/build/signing unchanged.

Focused5eb group-admin39358679469(Android10)/39358681910(Android17) bothFAILED.
New ui58-group-viewport-red packets each retain11 hashes, six original native/
all-root tree members, stages, host120second failure and independent restoration.
Both trees measure Group messages top340/bottom340, zero height. Root opened both
original before-history PNGs: pending status, disabled picker rows and saved
multiline draft consume the viewport. The last stage is before-history-scroll.
This establishes a layout regression; neither failed preparation is recovery proof.

The production fix keeps ChatPhotoAttachment mounted but hides its empty-photo
picker actions while group administration is pending. Existing attachments,
credits, state and drafts stay present; private/default paths are unchanged.
An explicit48dp minimum-history assertion precedes the original cached-message
scroll, without reducing any original goal or deadline. Both source review axes0.
Fresh focused/full paired acceptance is required. Prepared signed46 is superseded
and remains unpublished; a new canonical candidate must include this fix.

Corrected group-admin39360256057(Android10)/39360258546(Android17) atf59 both
SUCCESS. Each exactly matches16 independently accepted38 identities:12 UI methods,
plus promotion/removal cold recovery and restoration, with two deliberate deaths
and two continuity gates. Promotion PIDs8474->8843/4686->4769; removal9207->9541/
4911->5031. Exact persisted signed originals, Relay once on explicit Retry,
wallet/backend/key/history/draft and restored state retain all original deadlines.
Paired ui58-group-green packets retain50 hashes/92 original member bindings.
Root opened both before-history originals: saved text is visible, and both trees
measure Group messages340..426,86px height instead of zero. This is the pending
administration regression fix, not complete Chat polish or full acceptance.

Canonical47 is PREPARED atf59f01faacc696d5e5956933614737aee1875289. Actual APK SHA
bbb7e5bb939807f6dbb51cd50c6c46d0f41ba40b06d165eb35a8a88fd736bb3e, same established
certificate/package, version47/0.1.47, min29/target36, nondebuggable/nonprofileable,
backupfalse. Actual DEX has reviewtrue, pushfalse and no Posts/Chat provider keys.
Both preparation review axes0. Public before-bookend confirms ordinary45 remains
live and immutable45/performance43 bytes match.47 is unpublished;46 superseded.

Source39360230027 atf59 passed core/gateway, Debug/test, Release/Performance and
lint. Fresh full39360894371(10)/39360896559(17, all3 partitions) executee2056df1d,
whose only differences fromf59 are evidence/docs. Their independent473/474 catalog,
122/123 completions,34 interruptions and33 continuity expectations were bound
before outcomes, with public reads and immutable35/43 observers enabled. No full
outcome, signed delivery, physical/provider or store acceptance is claimed yet.
