# Issue70: deliberate navigation for large text

Status: complete native verification passed; signed beta68 published.
Review base7711a0c05368eefc4facea93d3d14428c1d4ada7. Only issue70 is authorized.

The native regression at824d89794, run40286952560, fails because Following occupies
two lines at320dp/200%. Original screenshots also show broken bottom captions.
Run40287533766 atba4de8172 reveals a new tooltip layout defect: only Home fills
the bar, and the unchanged label-fit assertion fails. Diagnostic40288267873 at18ee
records size104x28, widthOverflowtrue/heightOverflowfalse with maximumwidth136.
These are retained failed runs, never acceptance evidence.

Atff480635e, a real weighted outer Box gives every navigation item its own share;
Material1.4.0 TooltipBox applies its modifier to an inner anchor. Caption fit uses
the actual bar gap and text measurement. Ordinary constrained single-line Text
replaces nowrap rendering; the no-overflow assertion remains. The active caption
and long-press tooltips name the adaptive icon layout, with full names on the native
tab targets. Feed modes move to their own row only when labels/actions cannot fit.
No font shrink, node, wallet, draft or feed-filtering behavior changes.

New native observations use existing controlled-peer/UI seams and independent
restoration. Both320dp and412dp,100/130/200%,light/dark are required, alongside
keyboard, all selected destinations/tooltips,48dp disjoint targets, Following
selection/scope/recreation, and actual normal/zero window motion. Existing frame,
one-pixel reading recovery, drafts and complete checks remain mandatory.
Original physical Pixel/provider/store acceptance stays parked under36.

Focused matrices40289614296/40289618121 atf369 both pass all five methods.
Both raw packets nevertheless fail the independently frozen positive native-selection
capture check: a few XML trees still contain the prior selected tab. These packets
are useful focused observations, not complete evidence acceptance. At a4eabaebc
the same15s selection wait now requires the actual native selected node before
capture. Focused10run40290600094 verifies that observer correction.

The earlier label-overflow finding was a public layout-observation mismatch:
Text size53/lineRight53/visible native width53, paragraph allocation75 or121.
The original one-line check remains. The corrected check also requires exact
Following text, all nine visible characters, no ellipsis, line edges inside the
rendered size and no height overflow. Both independent reviewers approved this
as a faithful full-label check, not weaker acceptance. Production at that stage remained ff480635e.

Existing frame10run40289020964 passes all seven original methods, including
first-use dominance, profile drafts, one-pixel reading recovery and system motion.
Frozen complete checks b75617661 preserve all accepted69 identities/pins and add
five methods:541/542 identities,173/174 normal completions,35 deliberate interruptions,
34 continuity records and134 archives. Original motion frames show a short caption
crossfade normally and immediate replacement at zero scale. No full run accepted yet.

Visual review found retained keyboardChat tooltip overlapping other long-pressed
captions in both green packets. Strict native RED40291131065 at3c9c confirms it.
Production40fea1af2 coordinates navigation tooltip states, accepting request priority
before dismissing peers and delegating native presentation. Both source reviewers
approve. New complete evidence freeze40fea1af2 strengthens caption presence to
exactly one visible caption; old packets correctly fail it. Both focused versions
40291952046/40291955814 now run that corrected production. Full acceptance pending.

Fresh corrected focused runs40291952046(Android10) and40291955814(Android17)
are whole SUCCESS and pass the stronger frozen navigation evidence check. All five
new methods pass. Original captions now show one complete popup. Two-axis source
review has no unresolved findings. Complete505dcd3a7 executions40292826571/40293131622
are active; no full acceptance yet. Signed68 is preparing at that same source and
will stay withheld until complete checks and final review pass.

Complete Android10 predecessor40292826571 is rejected: wallet passed, chat failed
at the inherited group-invitation recovery scroll target, social was cancelled.
Original target bounds were483..537 before moving to555..609 outside459..537.
No application or fixture change was made. Unchanged isolated recovery run
40295628598 atb2a523f15 succeeds. This does not erase the failure or establish its
cause. Replacement whole Android10 run40296506207 repeats all three partitions;
Android17 run40293131622 remains active. No partial runs will be assembled.
All app/test/harness/workflow/build inputs are identical between505dcd3a7 andb2a523f15.
Signed68 is prepared and withheld; before-publication checks preserve28 historical
resources and confirm withheld61 resources remain404.

Complete Android17 run40293131622 at505dcd3a7 is whole SUCCESS. The unchanged
frozen collector passes542 identities/passes, zero assumptions,174 normal
completions,35 deliberate interruptions,34 continuity records and134 archives.
No missing/extra identities or partial assembly. Final bounded evidence review
is underway. Replacement10 remains active; prepared68 remains withheld.

Replacement whole Android10 run40296506207 atb2a523f15 succeeds. Frozen collector
passes541 exact identities,540 passes plus the sole inherited API29 Profile
assumption,173 normal completions,35 deliberate interruptions,34 continuity
records and134 archives. Original failed40292826571 remains rejected. No code,
fixture or assertion changes were needed for the successful full repeat. Actual
inputs for505/b2a are identical, recorded in verification-source-equivalence.json.
Android17 final evidence review: Spec0, Standards0. Android10 final review pending.

Both final native evidence reviews report Spec0/Standards0. Root also inspected
actual final10 large-text Home and single Activity tooltip originals. Signed68
was canonically published with exit0. Independent HTTPS before/after verification
passes: held APK38365395bytes, SHA256
 d468ffba977ee10d3c97a5d19d59b2eba4f8d4461799782c521bb6b1248e4266,
truthful source505dcd3a7a6b56655fac6910c4686ffe6d8f594b, established identity and
nondebug/nonprofileable manifest. All28 historical resources remain identical;
withheld61 URLs remain404. Public packet has44 hashes. No APK/signing material is
committed. Physical Pixel/provider/store acceptance remains parked under36.
Only70 is completed; stop before further implementation.

Final independent publication reviews: Spec0, Standards0. Both reviewers verified
the actual public APK and all28 historical resources against original bytes.
