# Ticket #81: owned navigation and loaded-result motion

Review base: `3b7bf5b04638ddced7b63add45c84b08b40db7b6`.
Final implementation and executing feature source:
`1d3b6e0204bb141d9e6dedc6be2ad970b9b20110`.
Both final source review axes and both final native rendered reviews report no
unresolved actionable findings.

## Completed execution

| Platform | Complete focused feature run | Methods | Classes | Original movies |
| --- | --- | --- | --- | --- |
| Android 10 | [42139774934](https://github.com/robelene112/redsquare-android/actions/runs/42139774934) | 21 | 11 | 30 |
| Android 17 | [42139777989](https://github.com/robelene112/redsquare-android/actions/runs/42139777989) | 21 | 11 | 30 |

Both complete focused runs use the same final source and pass every listed class.
`retain-native.py` validates the literal method identities, successful native
completion codes and host exits, exact source/run, pinned system images and all
30 movies per SDK. Raw results, source/environment receipts, native screenshots,
hierarchies, movies and decoded original frames remain in each platform directory.
Every class retains its 180-second bound. These are focused feature runs, not
whole-app acceptance. Ticket #94 retains complete final-source application gates.

[Source/debug verification 42139780278](https://github.com/robelene112/redsquare-android/actions/runs/42139780278)
passes on that source. Core reports contain 119 valid cached results with zero
failures or skips. The gateway task also reports FROM-CACHE; its count report is
not uploaded, so no fresh execution or new gateway count is claimed. Debug lint
reports zero errors, 55 warnings and one hint. The exact workflow log is retained.

## Result and observed behavior

Primary/public destinations, private/group navigation and existing Settings
categories use the shared owned reveal. Only the current feature tree exists;
controllers and recoverable state retain their established owners. A changed
normal-motion destination first draws at near-zero opacity, then begins the
180ms linear reveal after a draw callback and subsequent frame opportunity.
This does not gate input, delay initial mounting or establish completed GPU
presentation. Actual scale zero bypasses preparation and motion.

Loaded raster/profile/provider results use the shared linear 180ms presentation.
Only rendered results overlap. Loading and GIF animation lifecycle effects stay
outside that overlap. Changing source/configuration disposes the old provider
asset. The drawable identity remains stable across GIF frames. An outgoing Retry
loses input/semantics ownership immediately; synchronous failure retirement keeps
three rapid physical taps to one deliberate replacement read, two HTTP reads
including the original failed request at both scales on both SDKs.

| Native observation | Android 10 | Android 17 |
| --- | --- | --- |
| Normal Search title intermediate frames | 8 | 2 |
| Normal banner intermediate frames | 6 | 8 |
| Normal provider intermediate frames | 9 | 9 |
| Zero-motion intermediate frames, all three scenes | 0 | 0 |
| Route-to-focus, normal / zero | 167 / 287ms | 370 / 412ms |

Normal Search retains the original two distinct title-projection requirement,
contrast/variance limits and touch-started five-second observation deadline.
Normal media retains two distinct chroma intermediates with range at least 10;
zero retains fewer than two. Every media scene also requires an original decoded
endpoint, two independent stable native endpoint samples within the original
ten seconds, unchanged container geometry, and actual before/after window and
system motion-scale receipts.

Existing animated-GIF picker/draft/recreation and six owner/official post/reply/
quote methods all pass, including the original attribution/link assertions.
Search cancellation/recovery, scrolled-feed restoration, large-text keyboard
navigation, Settings/wallet entry/return and thumbnail arrival also pass.
No live provider credentials or public posting are used in these owned fixtures.

## Failure provenance and observer corrections

`failures/` retains original pre-change failures: absent destination/media
interpolation, a repeated Retry causing three HTTP reads, the failed Bee batches,
and the unchanged-threshold Android 17 Search failure before first-draw preparation.
Earlier failed runs remain failed. Provider attempts using `localhost` do not
establish provider-renderer behavior; the corrected fixture uses the existing
`127.0.0.1` guard and actual native ImageView.

Native hierarchy traversal was removed from the Search recording interval while
retaining the original deadline measured from touch, including successful query
completion. GIF attribution explicitly scrolls to Source before its original
visibility assertion. A newly introduced observer assumption requiring duplicate
encoded endpoint frames was corrected: damage-driven screenrecord may encode a
settled image only once at either scale. Recorded arrival plus two stable actual
endpoint samples remain mandatory. This does not relax normal interpolation:
the earlier one-intermediate failures still fail that requirement.

`diagnostics/` retains the bounded timing experiments. Actual easing callbacks
and main-thread samples show rendering/scheduling gaps; discrete samples do not
identify an exclusive cause. The attempted bitmap preparation and four-core guest
both failed the original motion goal and were reverted. Final runtime allocation
is the established two-core KVM guest, with requested/actual capacity receipts.
All clock/stack probes are removed from the final app and harness. The final
linear curve and owned first-draw preparation are presentation changes, not a
claim that the underlying render synchronization was fixed.

## Review and limits

Final Android 10 rendered review inspects all 35 root frames, all 31 loaded-media
frames and 215 representative frames across every destination journey and
Settings/wallet movie, with no new actionable finding. It records direct zero
replacement, monotonic normal progression, stable geometry and live input.
The final fresh-published GIF screenshot paints Source; an earlier 1780 PNG/XML
pair disagreed and is retained as a non-atomic capture limit, not product proof.

Final Android 17 review inspects every root and loaded-media frame, with ordered
representative frames across all remaining 24 movies and relevant reading,
recovery, GIF and 320dp/200% light/dark stills. It reports no actionable finding:
no persistent faint state, flash, clipping, geometry shift, outgoing editor or
transition-induced reading shift. Focus is observed 100/261ms after field touch.
The ordinary GIF-credit PNG paints Source; its fresh-Activity PNG alone does not
establish Source paint. The following XML and original visibility assertion are
separate observations. This non-atomic evidence limit remains explicit.

These software-rendered emulator observations do not establish physical Pixel
latency, gesture feel, continuous observation of every display refresh, external
provider delivery, complete application acceptance or beta publication. Existing
Chat empty-before-loading and public-thread loading-status movement were observed
separately; the Chat presentation follow-up is #83. Parent #55/#84 and release
#36/#1 retain their own acceptance requirements.
