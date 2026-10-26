# Issue70: deliberate navigation for large text

Status: implementation and focused native verification in progress, not accepted.
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
as a faithful full-label check, not weaker acceptance. Production remains ff480635e.

Existing frame10run40289020964 passes all seven original methods, including
first-use dominance, profile drafts, one-pixel reading recovery and system motion.
Frozen complete checks b75617661 preserve all accepted69 identities/pins and add
five methods:541/542 identities,173/174 normal completions,35 deliberate interruptions,
34 continuity records and134 archives. Original motion frames show a short caption
crossfade normally and immediate replacement at zero scale. No full run accepted yet.
