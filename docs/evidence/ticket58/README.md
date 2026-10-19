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
