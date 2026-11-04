# Ticket78 first-use reading space

Status: complete, reviewed and verified. Stop after this issue, as requested.
No intermediate beta is published; the integrated signed beta remains94 work.

Review base: `a31029be6`. Last production change: `076cd5d07`.
Final new verification source: `b43ed54ae`. Between those last two revisions,
only the viewport test adds the real writing/IME/draft-return journey.

## Result

At very large text, the reminder uses its warning as the backup action, an
accessible Later close control and a clearly visible Import wallet action.
The import control retains its full accessible name. Padding gives the warning
room without shrinking the system font or hiding guidance.

The48dp writing button docks in the existing large-text navigation caption row,
clear of the reading list and post actions. Normal-text floating composition
remains56dp. The live docked action is outside the caption's cleared semantics;
all five existing navigation destinations remain independent.

Actual320dp/200% reading-list height improves from183 to221 pixels, about21%.
The first post's body and reactions are visible without the compose overlay.
At412dp/200%, list height improves from480 to496 pixels and the button is also
outside the reading list. These measurements match both themes and OS versions.
The100/130% list bounds are unchanged across every before/after cell.

| Width/text | Before list height | After list height | Compose action |
| --- | --- | --- | --- |
|320dp/200%|183px|221px|56px overlay becomes48px outside list|
|412dp/200%|480px|496px|56px overlay becomes48px outside list|
|Both widths/100% and130%|Unchanged|Unchanged|Existing56px floating action|

These are measured emulator layouts at density160, not physical Pixel performance.

## Native verification

| Boundary | Android10 | Android17 | Source |
| --- | --- | --- | --- |
|New first-use matrix and interrupted motion|[41841038043](https://github.com/robelene112/redsquare-android/actions/runs/41841038043)|[41841042536](https://github.com/robelene112/redsquare-android/actions/runs/41841042536)|b43ed54ae|
|Complete original frame, reading and motion|[41840388700](https://github.com/robelene112/redsquare-android/actions/runs/41840388700)|[41840393368](https://github.com/robelene112/redsquare-android/actions/runs/41840393368)|076cd5d07|
|Failing original density baseline|[41837773974](https://github.com/robelene112/redsquare-android/actions/runs/41837773974)|[41838468795](https://github.com/robelene112/redsquare-android/actions/runs/41838468795)|05a8a2543/700b12f9a, unchanged app source|

Each final first-use run requires two viewport methods covering12 cells:
320/412dp,100/130/200% actual system text, light/dark. Each cell retains visible
backup/import/Later, selected-node context, Home, actionable account controls
and actual native minimum compose-button size. The reading-space goals remain
at least32% of the narrow viewport and a compose height no more than25% of its
reading list. The additional large-text overlap assertion compares actual
on-device Compose bounds, not platform native visibleBounds. Existing frame
checks retain platform-native48dp/disjoint target assertions.

Both large-text widths also open the actual docked writing control, type unsent
text with the real Android IME, save locally, return Home, and reopen with the
expected draft text retained. Backup guidance remains visible on Home return.
Each Back observes a positively open IME first. Retention uses the full expected
text with assertTextContains, not a claim of exact field-byte equality.

The separate interrupted-motion class uses actual touchscreen events: dismiss
Later, then tap Chat32ms later, within the normal180ms transition. It covers
real system scales0/1 and observes each matching window MotionDurationScale.
The reminder stays hidden through Home return and Activity recreation. Original
movies, every decoded-frame timestamp and representative original frames remain
in the packet. Native chronological frames were inspected for both OS versions;
zero motion has a direct height change and normal dismissal remains interruptible.
Irregular recording cadence is not a frame-rate or physical-device claim.

Each original frame run passes4 frame methods,1 retained-reading method and2
motion methods. Those preserve first-use return, actual backup guidance,
Wallet/Settings,8-cell native target geometry, real profile IME/unsent work,
original1px Chat-return/recreation/outage/pull anchors and reminder persistence.
All pre-existing class/assertion deadlines remain. Each new class has its own
180second bound and remains mandatory in complete Chat verification. Both final
host reports retain117 tests,0 failures and0 ignored tests.

Wallet export identity, selected backend, reminder/preferences and existing
files are independently restored at the controlled-peer/native UI boundary.
The fixtures require zero outgoing transactions/reports. No live wallet import,
transfer, provider setup or production node change is used as test evidence.

## Rendered comparisons

Before is the upper row; after is the lower row. Each row shows100/130/200%,
light then dark. Original PNG/XML and configuration/density records accompany
these contact sheets. System clocks and generated active-wallet avatars can
differ between independent runs; the comparisons evaluate presentation.

- [Android10,320dp](comparison-api29-320.png)
- [Android10,412dp](comparison-api29-412.png)
- [Android17,320dp](comparison-api37.0-320.png)
- [Android17,412dp](comparison-api37.0-412.png)

Original PNG/MP4 bytes remain unchanged. Text evidence normalizes line endings
and trailing whitespace. Movie SHA256 values, exact source references, results
and all24 before/after comparisons are in native-results.json. Full decoded
frame sets remain in the original Actions artifacts; the retained movies and
complete timestamp manifests reproduce their sampling order.

## Review and corrective provenance

Fresh independent Specification and Standards reviews of the fixed-base diff
and its follow-ups report0 actionable specification findings,0 documented
standards violations and0 actionable Fowler findings. Subsequent changes only
record this evidence and the one-issue stop.

The initial native baseline genuinely fails the new density goal:183px reading
space and56px compose height at320/200%. An initial48px floating correction
passes those first density goals (41838722380) but still visibly covers Repost.
Replaying its actual recorded Compose rectangles rejects the overlap. The final
docked action removes that obstruction while preserving the earlier thresholds.

Initial62eced6de scopes were superseded by the actual unmerged post-body observer.
The first Android17 scope was initially missing the workflow's runtime exclusion
guard and was cancelled; it is not Android17 evidence.700b12f9a adds both guards,
and the proper Android17 baseline above confirms the same density failure.
Superseded/cancelled runs are not acceptance. The final native runs above are
complete successes, including writing, IME, draft return and interrupted motion.

Parent55 visual acceptance, release36 actual owner/provider/physical/store work
and final94 consistent-source full-app/update verification remain independent.
No complete app release or physical-phone acceptance is claimed by78.
