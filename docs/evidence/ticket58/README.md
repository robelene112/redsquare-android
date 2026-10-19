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
