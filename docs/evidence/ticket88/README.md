# Ticket88: real feed and writing presentation

Completed against review base `7264de4ecf52388588bd6d49f72f33b518c4108b`.
Last production change: `04621d79844a987e6985692a2a7ceec6c9ac5834`.

## Delivered behavior

Home and Following share compact real author metadata, existing verified avatars,
consistent media corners and real article cards. Cards navigate to the original
signed link or current text URL, preserving punctuation. Bounded available node
preview hints supply the title, description and raster; missing hints produce an
honest target card. A different `og:url` never replaces the actual destination.
Text-only edits discard hints belonging to the old text target, while an explicit
signed attachment remains attached. Preview hints do not establish signed article
authority. Images, GIF attribution and the existing video path remain supported.
Verified quoted content uses the same presentation and save-before-source guard.

Post/reply/quote writing shows the real signing identity, actual target and current
draft state. Its editor fits the remaining viewport above the real keyboard,
including320dp at200% system text. Text remains fully scaled, with internal field
scrolling and the complete visible caret rather than outer clipping. Existing
attachments, consent, queue/retry, cancel and draft persistence remain in place.

Pull-to-refresh announces success after applying the page and reveals genuinely
new leading posts when the reader remains at the top. A reader who scrolls while
the response is pending keeps the newly chosen post and exact pixel anchor.
Thread/profile return, unsent draft recovery and cached outage/recovery remain.
Like feedback is unchanged and belongs to91; thread-header changes belong to79.

## Native acceptance and exact sources

| Android | Unchanged feed/media/Following proof | Final writing proof |
| --- | --- | --- |
|10|[41877928927](https://github.com/robelene112/redsquare-android/actions/runs/41877928927),23 accepted methods at `9dab481a07657def0e9b8d8c9d8a69c2a66bee19`|[41883471588](https://github.com/robelene112/redsquare-android/actions/runs/41883471588), all8 methods pass at `04621d79844a987e6985692a2a7ceec6c9ac5834`|
|17|[41877933291](https://github.com/robelene112/redsquare-android/actions/runs/41877933291),23 accepted unchanged methods at `9dab481a07657def0e9b8d8c9d8a69c2a66bee19`|[41883476813](https://github.com/robelene112/redsquare-android/actions/runs/41883476813), all8 methods pass at `04621d79844a987e6985692a2a7ceec6c9ac5834`|

This is31 accepted methods per OS across the explicitly identified sources,
not a single final-source full-application pass. After9dab, the only production
changes are `PostComposerScreen.kt` and `PublishingEditor.kt`; all affected
writing classes were rerun at04621. Android10's earlier broad run failed overall
because two writing-fixture methods mistook a disabled saving field for completed
navigation. Its23 unaffected methods passed. Android17's earlier broad run passed
all31. All8 earlier writing results are superseded by the final focused proof on
both platforms. Original failed logs are retained, not relabelled as passes.

The23 unchanged methods cover4 link,2 refresh,1 reading,1 actual media motion,
4 original post presentation,1 post presentation viewport,1 content context,
1 content viewport and8 Following methods. The final8 writing methods cover
2 new viewport/draft journeys,1 original focus,2 original draft status and the
3 original post/reply/quote journeys. Each class keeps its180second bound;
no original assertion or full Social inclusion was removed.

The final writing matrices contain12 actual cells per OS:320/412dp, light/dark,
and100/130/200% system text, with real IME and exact unsent text after recovery.
The viewport methods check visible text ink and complete field geometry. Minimum
visible editor height is135px on10 and146px on17; maximum outer clipping is0px
on both. Minimum actual text ink is871/902 pixels respectively. All24 cells keep
the real keyboard. Two Android17 cells acknowledge Gboard's own font-size
education with its actual package-scoped OK button; font scaling is not reduced.
Original PNGs and contact sheets were visually inspected, including the narrow
200% caret/last-line case. Normal and zero-motion media captures use the actual
window motion scale, without a Compose test clock; original movies and decoded
frames show the ready image and the normal intermediate fade.

Host verification passed117 core and16 gateway tests, with zero failures or
ignored tests. Final `:app:lintDebug --max-workers=1` passed at04621, retaining
the existing heap/container limits; see `final-lint.txt`. Read-only Standards and
Spec reviews against the fixed base have zero remaining findings. Exact URL
trimming and edited-target metadata findings were fixed before acceptance.

## Failed attempts and corrections

- RED41871697525 atae317a9c5 demonstrates the missing real article card.
- First broad41874873857/41874879942 at668fc71db retained the old first post
  after a top pull.9dab adds explicit leading-post reveal and independently checks
  an active reader's exact pending-response anchor.
-9dab's Android10 writing-fixture failures waited for field absence, which also
  occurs during saving.381daca1b waits for actual destination navigation instead.
- Focused41880325473/41880333484 at381 passed the navigation assertions, but
  rendered review found the old160dp field partly clipped at320dp/200%. The
  retained before PNGs explain the final available-height editor fix.
-41882553403/41882557668 at902 failed compilation before native tests because
  nested Compose DSL scope hid `maxHeight`.04621 captures the same viewport
  height before entering that scope; both final writing runs then passed.
- An earlier local combined build lost its Gradle daemon and a later lint was
  interrupted by a server restart. Neither is counted as a pass or asserted to be
  an out-of-memory diagnosis. The final standalone lint completed successfully.

## Packet and limits

`native-results.json` is the source/method/geometry summary. Each platform has
`feed-native/` and `writing-native/` transcripts, exact app/workflow/runner
provenance, `rendered/` original native PNG/XML/JSON/movie evidence and three
contact sheets. `before-and-diagnostics/` retains the relevant failure logs and
before editor captures. Binary originals are unchanged; text line endings and
trailing whitespace are normalized for repository checks.

These are guarded disposable dev-emulator proofs using controlled signed peers,
local raster fixtures and intercepted Android external-link intents. They do not
establish physical Pixel performance, live provider delivery or store acceptance.
No production node, wallet or block-production configuration was changed, and no
public social transaction or intermediate beta was published.94 retains the
required consistent-source whole-app, signed in-place update and integrated beta
publication gates. Current ordinary beta83 remains published.
