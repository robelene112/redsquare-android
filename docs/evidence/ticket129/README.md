# Understandable Fileshare attachment availability, #129

Parent [#117](https://github.com/robelene112/redsquare-android/issues/117), SP4.
Review base: `264815d2c6b2b6d4dee304017b3ac6cb589981cf`.
Product/source checks: `1e170ba024b0d3a4af0cf0bb98b131a1e3ade3db`.
Complete native fixture: `1619140c7c27f54040c0549fdca2a63f15fbba7f`.

Both independent review axes accept this source and complete relevant native evidence
with the stated IO/picker, capture and operational qualifications.

Send a file remains visible with a neutral explanation while Fileshare loads,
prepares a document, owns another transfer or already has a picker open. The
file row is disabled during that restriction; tapping it leaves the menu open.
Only that row changes. Photo/GIF actions and text editing keep their existing
availability, focus and handlers. Secondary text uses shared typography and a
short animated size transition. A small action/reason value keeps Fileshare state
inside its owner rather than duplicating readiness logic in Chat.

Original publication/draft checks, notification permission, URI grant handling,
identity binding, platform picker and actual transfer paths remain unchanged.
When ready, the normal action opens Android's document picker.

## Complete relevant native qualification

| Controlled scope | Complete parent | Source | Outcome |
| --- | --- | --- | --- |
| Android10 | [43852586005](https://github.com/robelene112/redsquare-android/actions/runs/43852586005) | `1619140c7` | 10 normal methods, seven groups |
| Android17 | [43852586018](https://github.com/robelene112/redsquare-android/actions/runs/43852586018) | `1619140c7` | 10 normal methods, seven groups |

The initial scenario holds an actual background Fileshare directory lookup using
a ContextWrapper, before it acquires the shared encrypted-store lock. Real Chat
loads and edits while the actual FileSession remains unready. Its disabled file
row explains loading; photo/GIF remain enabled, a real disabled-row tap does not
close the menu, and dismissal preserves writer focus and draft text. Releasing
actual IO makes the row available. The scenario observes real Android DocumentsUI
without a picker-result stub and exactly one ACTION_OPEN_DOCUMENT. Canceling
retains the exact encrypted Chat draft and starts no preparation/transfer/write.

Two additive scenarios reuse the original controlled streaming provider and
pinned browser peer. A genuinely stalled preparation and accepted foreground
transfer behind held Relay both explain their file-specific restriction, retain
other media availability and preserve draft/focus. Original preparation Cancel,
foreground/screen-off behavior, bytes, transfer and Chat assertions remain.

The complete scope also retains original send, all three receive/accepted-cancel
methods, actual notification Cancel, streaming-preparation Cancel and denied-
notification checks. Every original class bound stays180seconds, held Relay
stays10seconds, and prior assertions remain. Native permission grant/restoration
runs outside instrumentation where revocation could terminate its own process.

The ready-picker scenario uses the real system picker. Existing separate transfer
fixtures retain controlled picker-result URI grants. Photo/GIF availability and
writer focus are observed here; their existing selection/sending handlers are
unchanged. Full application/media/process/performance qualification remains#131.
Android10 initial-loading-menu.xml omits the disabled row/reason. Its paired PNG
visibly contains both and native assertions pass; that XML is not label proof.

## Source and fixture bridge

The source-to-native bridge changes four AndroidTest files and adds harness
coverage in scripts/emulator-check. It records227699 identical Git entries with
canonical digest e60e896c40402bd58593e97a9115725a60ed0b82814c56c0ac4f024f0af7fc0f.
Product/core/resources/build/workflow/controlled-peer entries are identical.
The harness is changed additively, not claimed identical: the focused scope grows
to ten methods/seven groups, and both new classes also enter full Fileshare.

The original Fileshare fixture reconstructs byte-for-byte after removing the
explicit default-false flag, two additive menu checks and independent cleanup.
The cleanup closes a visibly open SystemUI notification panel with actual Back,
requiring disappearance within5seconds before the remaining restoration. Primary
failures remain primary and every later cleanup is independently attempted.

[Source43849560389](https://github.com/robelene112/redsquare-android/actions/runs/43849560389)
assembles debug/test APKs at1e170ba02 and passes lintDebug with0errors,95warnings
and3hints. Reports contain224 core tests/44classes with zero failures/ignored;
core and gateway tasks are explicitly FROM-CACHE. Final native runs compile
actual161 fixture source. Source checks are not release signing or whole-app proof.

## Retained original observations

All original metadata, logs, server-digest-bound ZIP/TAR archives and exact
extraction checks remain externally at
`/mnt/storage/redsquare-android-artifacts/file-menu129`.

- 43847853758@4d5fb0ae9 is the actual adverse case: real initial Fileshare IO
  is held while Chat is ready; Send a file is incorrectly enabled and the native
  disabled-row assertion fails. The screenshot shows the unqualified menu.
- 43848944193@1e170ba02 passes the complete one-method correction, including
  actual DocumentsUI, one intent and encrypted draft preservation. It is an
  intermediate focused result, not final whole-ticket acceptance.
- 43850805394/43850805713@8c0e41792 each complete nine methods normally, then
  fail the last original notification restriction case. Android10 cannot find
  the Settings switch; Android17 times out at permission denial/explanation.
  Both failure captures show SystemUI's notification panel covering the view.
  This proves the visible shade at failure, not its earlier causal timeline.
  Both whole parents stay failed and their prefixes contribute no acceptance.
- Corrected43852586005/43852586018@161 each independently complete all ten
  methods. Their original sources and complete parent results remain explicit.

Seven receipts retain633 ZIP files and249 regular TAR members. Only the two
complete corrected scopes and source/debug result count for final acceptance.

## Acceptance and limits

Both independent Spec and Standards reviews accept source, original executing
native evidence, source bridge and stated qualifications with zero findings.
Both axes independently verify all172 indexed packet copies against external
source bytes/hashes, the seven-receipt633ZIP/249TAR inventory and canonical source
bridge. Final copied-packet reviews report zero findings.

Controlled emulators and owned peers establish these agreed seams. Physical/OEM,
live community-service, default-domain, full application/performance and signed
release acceptance remain separate. No production node/provider operation or
public financial write occurred. Public beta remains0.1.131 until combined#131.
Continue#130, then final#131, without another approval.
