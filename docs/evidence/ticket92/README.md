# Ticket #92: grouped Like activity

Review base: `1f4ba191d1ae7783a283491649f278aa5b5f729b`.
Accepted production/test source: `67d8bfd0919d880ce06d128c5afb65187615a3ef`.
Both source review axes and independent native visual review have zero findings.

## Delivered behavior

Verified Likes share one stable card per exact target post. Distinct people appear
once in a bounded avatar strip, using the existing verified picture/fallback
component. A separate localized count stays visible when a long name truncates.
People details retain actual Like and distinct-person totals. The card opens its
exact post or unavailable-preview reference directly. Replies and other activity
remain individual.

A lifecycle-scoped newest-Like check automatically applies genuinely new events,
reorders the existing group and makes it unread. Reads retain observed underlying
signed event IDs. Duplicate reads and paging preserve read state. Newest-page
catch-up and older-history continuation remain separate, including bursts larger
than one Archive page. Existing partial/error/cache/identity semantics remain.

Resolved preview inputs use stable post-ID ordering, so group motion alone does
not discard already-resolved text. Content and author-revision changes still
invalidate previews through the existing verification gate.

## Native verification

| Platform | Broad notifications run | Strong focused viewport run |
| --- | --- | --- |
| Android 10 | [42076520352](https://github.com/robelene112/redsquare-android/actions/runs/42076520352), PASS | [42074570253](https://github.com/robelene112/redsquare-android/actions/runs/42074570253), PASS |
| Android 17 | [42075647474](https://github.com/robelene112/redsquare-android/actions/runs/42075647474), PASS | [42074575625](https://github.com/robelene112/redsquare-android/actions/runs/42074575625), PASS |

All four runs use the same accepted source. Each broad run passes **48 UI methods**:
all 32 original notification methods, #80's four availability methods, five prior
inbox/viewport/state/revision/Like-contract methods and seven additive grouped
methods. Android 10 also passes one permission method; Android 17 passes both
choices. Actual process death preserves the same wallet/backend/event, retained
activity and read state under failed controlled-server reads, then actor navigation.
The retained checkpoints show different initial/recovered process IDs. No public
transaction is sent.

The new methods prove multiple actors on one post, repeated actor/same post,
separate targets for one actor, individual reply content, exact missing-preview
target navigation, read-to-new-event resurfacing without Refresh, duplicate reads,
paging, recreation and cached outage. The burst tracer retains the older cursor,
receives 150 new signed Likes, catches up to 157 and then 160 without skipping the
middle, and remains read after duplicate reload and exhaustion. Original tests
retain wallet/node isolation and the 150-event cache/read goal through grouping.

Each OS has 12 actual viewport cells: 320/412dp, light/dark and 100/130/200% system
text. Thirty-six original focused PNGs per OS retain group content, avatar strip
and People dialog. Native avatar targets are distinct and at least 48dp. The
long-name summary keeps its separate actor count visible. Full identities and
Done remain usable in the scrollable dialog. Independent review inspected both
matrices and large-text originals with no remaining findings.

Four original native movies per OS retain normal/zero system motion, actual
window-scale receipts and every decoded display frame. Normal motion moves the
existing group above the reply while retaining previews. Zero motion changes row
positions directly. People opens/closes and the Activity window returns intact.
Endpoint assertions alone were not used as motion proof. All original method,
request and class deadlines remain; the viewport placement observation is bounded
at 15 seconds inside the existing 180-second class.

## Source and review checks

[Source/debug lint run42074736707](https://github.com/robelene112/redsquare-android/actions/runs/42074736707)
passes at the same accepted source, with no device execution. Core/gateway tasks
reuse validated Gradle results (117/16 checks). Debug lint has no errors, with
55 existing warnings and one hint. The retained raw log and report preserve this
qualification. Standards and Spec source reviews have zero findings. Independent
viewport and final motion reviews have zero findings on both OS versions.

## Failure and correction provenance

- Run42052527296 is the real separate-row RED;42053508077 proves the first grouped
  card on Android 10.42054867561 is the automatic-resurfacing RED.42058638445 passes
  the first two functional methods before the burst repair.
- Run42060508924 proves the paging gap: only 110 Likes where 157 are required,
  with an older-history bound used before the unseen newer middle. Typed head,
  catch-up and older reads fix range ownership. Both final runs prove the 157/160
  totals and exact query bounds while retaining the older continuation.
- Runs42056057905 and42062553476 fail compiler heap before device checks. Run
  42065265946 fails checkout because its abbreviated source_ref is treated as a
  branch. Later runs use full 40-character SHAs.
- Run42065481253 still fails Kotlin FIR safe-call/data-flow analysis at 3GB. Its
  matching10/source runs42065475340/42067317225 were cancelled as superseded.
  Caching immutable wallet.publicKey once in02e restores full compilation at the
  retained local1792MB bound (5m46s main+test compile) and CI3GB. This establishes a
  mitigation, not an upstream compiler diagnosis. Earlier local compiler/lint
  attempts stopped143 are not passes. The local container stays2500MB; compiler
  and library pins, app/device memory and test deadlines remain unchanged.
- At02e, broad runs42069072792/42069083860 pass grouping/burst/motion and the
  existing availability/inbox gates, but fail unscoped viewport selectors matching
  background and dialog profile roots.2d scopes those selectors to the dialog.
  They also fail the native expected-text capture after People Done in the
  150-event regression, while the in-scope failure PNG/XML show the correct unread
  count. Bounded dialog-removal/window-focus observations precede the unchanged
  native capture; both final runs pass all32 original methods.
- At2d, runs42071551958/42072310521 pass47/48 UI methods. The narrow200% first-avatar
  observation fails despite a later original PNG/XML showing the strip. Test-only
  ab4's bounded placement wait alone also fails in42074233670; it does not establish
  a timing cause. Final acceptance uses67d's complete matrices and broad runs,
  preserving the earlier failures without an asserted cause.
- Original02e motion frames show a brief Loading preview flash during reorder;
  2d canonicalizes preview request ordering while retaining author-revision gating.
  Visual review then finds the long name hiding the actor-count suffix.67d separates
  that count from the truncated name and adds its visible assertion in every cell.
  Final native originals prove both corrections. No app delay, transaction change
  or weakened data assertion was introduced.

The `provenance/` folder retains representative RED logs and original failure
observations. Full raw packets remain under the matching
`/mnt/storage/redsquare-android-artifacts/redesign92-*` directories and linked runs.
`native-results.json` records exact source/run/count mapping. Retained viewport
PNGs come from the focused runs; other grouped PNGs/movies/logs come from the final
broad runs. Contact sheets are derivatives; original PNGs and movies are unchanged.

All native checks use guarded disposable dev emulators, public unsafe fixture
identities and controlled signed peers. They establish no physical Pixel
performance, public-node delivery, push-provider, store or owner acceptance.
No production node operation or beta publication was performed. #94 retains
consistent-source whole-app, signed update and integrated publication gates.
