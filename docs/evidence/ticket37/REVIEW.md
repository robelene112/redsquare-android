# Separate review axes

Final beta acceptance remains pending. Each source review used the actual,
nonempty committed diff; these results do not replace runtime verification.

## Standards

Whole source ca2ade6a...8f7ae687: zero documented breaches or actionable Fowler
smells. Appearance is separate from wallet/backend state, on IO, with selected
controls/save errors. Palettes, ForceDark opt-out and uiMode handling preserve
screen/work state. Feed/Chat presentation keeps callbacks, raw content, stored
work and protocol intact. API29 root is confined to guarded emulator mode setup.

Refinement8f7...a533: zero documented breaches; one possible Duplicated Code
heuristic for the two tab/underline shapes. a533...4c8f consolidates both real
callers in private RowScope.FeedTab: resolved, no new findings.

Interim evidence: zero findings. Independently checked59 manifest entries across
valid baseline RED/current10/current17/prepared14; parsed17XMLs; no secret markers
found. Viewed original baseline light failure and17explicitDark-under-SystemLight.
Raw exits, positive summaries, durations, applied modes and exact restoration
agree with the README. Local APK hash matches prepared manifest. Prepared14 is
correctly marked unpublished; this is not final full/physical acceptance.

## Spec

Whole source ca2ade6a...8f7ae687: zero actionable findings against37/1/35 and
existing safeguards. Themes/navigation/type/spacing/Chat stay in scope; wallet,
backend, drafts, history, raw messages and signing/transport state remain intact.
Handling uiMode preserves the live screen; ForceDark suppression makes chosen
palettes authoritative. Emulator setup does not alter application privileges.

Refinement8f7...a533 and a533...4c8f: zero findings. Server controls remain
prominent, theme choices remain scrollable/selected, underline matches feed
selection, and count plurals preserve the original Long. The extraction leaves
weighting, labels, callbacks and selected semantics unchanged.

Interim evidence: zero findings. Independently checked hashes,16currentXMLs and
8pairs perSDK, raw source/run/duration/exit/control records. Valid baseline8db
fails after32 is applied; current9f passes both32/16 transitions and restores10no
and17custom_bedtime. Prepared14 matches exact4c/package/cert/version. The recorded
captures do not claim final Chat visual quality, active full acceptance,
publication or35's Pixel targets. No wallet material is published in those sets.

Both axes have zero remaining source/interim-evidence findings. Final full10/17,
current originals, evidence reviews and signed/public publication are outstanding.

## Bounded fixture follow-ups

4c...e95: Standards0, Spec0. Exact wallet identity is scoped to its native
address description; video retains exact Playing state with bounded10s wait.
No product/identity/playback/no-publication predicate changed. Timing cause is
still qualified; corrected full runtime required.

e95...437: Standards0, Spec0. Full wallet/options String equality remains
exact and local, with static failure diagnostics to protect secrets. Product
unchanged. Both focused current437 cases pass; broader full e95 runs active.
Root also inspected current product4c private Chat originals from its earlier
successful24history/10action checks, while the containing full17 run failed
later. Captures in chat-visuals-4c retain this qualification. Final full acceptance
and evidence review remain outstanding.

## Time-limit follow-up, d4824273...19069015

Standards: zero documented breaches/actionable heuristics. Independent1,108hashes
and378XMLs/rawcount/status checks pass; no secretmarkers/encrypted installation
files/failure summaries found. Original dark-feed/forwardedreply inspected onboth
SDKs. Only workflow40→60minutes changes; individual deadlines/predicates intact.
Cancelled40m workflows remain clearly qualified, not accepted/published.

Spec: zero actionable findings. Counts/status/deaths/hashes/source match explicit
cancel annotations. Six original feed/theme/reply/cold-draft captures acrossSDKs
show selected readable controls, direction, literaltext/drafts/truthfulstatus.
Not exhaustive accessibility/physicalproof. Successful final reruns and signed14
publication remain required. Parent35 remainsopen.

Root additionally viewed10partialcachedFollowing,17uncertainReport and17cached
Conversation originals; availablepost/replies remainreadable, serverunavailable
indicator and reportUnknown/explicitSend retain original semantics.
