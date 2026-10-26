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
