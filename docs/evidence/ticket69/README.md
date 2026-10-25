# Accessible fullscreen images69

Review base: b7ec3c979e4187b7ab3a690a64962c9a0d7fa222.
Signed production source:57a6a27bfab543a26cb53cf563a28ee1536353a2.
Complete regression is accepted on Android10 and17. Signed beta67 is publicly
available at https://davidengelen.nl/redsquare/ as an in-place update.

The viewer adds zoom in/out/reset and a movement menu, semantic adjustment
and pan actions, meaningful image-position/zoom context, responsive themed chrome
and inset handling. Pinch/pan, decoding/retry and GIF attribution remain. Image
changes reset transforms and respect system motion settings. Keyboard input stays
inside the dialog and closing returns to the original attachment.

## Focused observations

- Android10 keyboard/viewport/motion:40186130296 at711d02c4a.
- Android17 keyboard/viewport/motion:40186131893 at711d02c4a.
- Android10 TalkBack:40189402929 at7a2f213c9.
- Android17 TalkBack:40189404755 at7a2f213c9.

All four whole focused runs succeeded; original packets and independently checked
hashes are retained. Viewport observations cover320dp portrait light/dark at
100/130/200% text, plus actual landscape200% in both themes. Seven native targets
are disjoint and at least48dp. Selected original renders show readable controls,
contrasting system bars and usable image areas. Normal image replacement has
intermediate fade frames; zero system motion has none. Actual pinch/menu movement,
zoom bounds, reset, navigation, modal keyboard confinement and original return pass.

Pinned TalkBack produces completed English engine speech for every control and
for image context with150% and100% zoom. It returns to the exact original thumbnail
on both SDKs; native captures show its focus outline. This is engine synthesis and
native focus evidence, not a claim of physical audibility or Pixel performance.

## Diagnosis and retained failures

The native RED40183253293 proves the original viewer lacks Zoom out. Early
implementation review found missing replacement motion and duplicate pan logic;
both were corrected. Native speech then exposed a real Compose naming split:
merged semantics put image position on a descriptive child while the adjustable
parent spoke only zoom. One unmerged control now exposes both context and state.

Other failures were observer defects: counting retained input focus across both
windows, seeking text instead of an attachment description, and measuring the40dp
painted icon instead of its48dp native target. Corrected checks retain their
original behavioral requirements and add actual window and target observations.

The apparent TalkBack return to Wallet was reproduced with external focus requests.
Pinned TalkBack restores its own action history; direct test focus requests do
not establish that history. Android's ordinary UiAutomation input injection also
[skips the accessibility input filter](https://android.googlesource.com/platform/frameworks/base/+/HEAD/core/java/android/app/UiAutomation.java).
The corrected viewer test uses the disposable emulator's actual virtual keyboard,
with independent key-release cleanup. The pinned enhanced keymap uses Meta+Right.
It records service-owned navigation and completed speech before opening the viewer,
without requesting duplicate speech that TalkBack suppresses. Existing shared
focus/speech callers retain their actions, assertions and deadlines. The actual
close-return assertion is unchanged; no tool patch or provider setting changed.

Every failed probe remains retained with its true outcome. Focused packets from
different sources are never assembled into complete acceptance.

## Full regression gate

The independent catalog and collectors were frozen prospectively in
39aa5793ecdaaa29059e9aed4f340ffac89926ed. Expected identities:536 on Android10,
537 on17; normal completions170/171,131 archives,35 intentional interruptions,
34 continuity records, and only the inherited Android10 Profile assumption.
All accepted66 identities, public reads, recovery checks and tool pins remain.
The viewer validator additionally checks service-owned origin navigation and
completed speech. It was exercised against focused captures before full dispatch.

Complete Android10 run40189989884 at57a succeeds with535 passes and the sole
inherited Profile assumption,536 exact identities,170 normal completions and
2538 packet hashes. Complete Android17 replacement run40194163662 at8ae succeeds
with537 passes/no assumptions,537 exact identities,171 normal completions and
2606 packet hashes. Both have131 archives,35 intentional interruptions and34
continuity records. Every partition succeeded and both unchanged frozen collectors
pass. All packet hashes were independently checked. Original light/dark portrait
and landscape200% renders, plus actual TalkBack return outlines, were inspected.

Initial full17 run40189991513 remains failed and is excluded from acceptance.
Its new second-image observer cleared an already-started automatic utterance's
request/START, retaining only AUDIO/DONE. Correction8ae clears before Next and
observes fresh automatic speech if the exact image already holds accessibility
focus; otherwise it makes the original explicit request. All existing assertions
and15s completion remain. Corrected focused runs40193626428/40193627883 succeeded
before the complete Android17 replacement. No failed partitions are combined.

WORKFLOW permits rerunning only the outstanding SDK after a fixture correction.
`verification-source-equivalence.json` records identical production/build inputs
between57a and8ae. Immutable signed67 keeps its truthful57a provenance and hash;
only the verification commit differs. Prepared APK is38332627 bytes with SHA256
64f2b6d1934e5cc8c8c9b59423a5a6933ee4980171846da402346c0ef3ad0d7c and the established
signing certificate. Canonical publication completed successfully. Independent
HTTPS before/after checks verify the exact APK, package, certificate and manifest.
All26 protected historical resources remain byte-identical and both withheld61
URLs remain404. See signed67-prepared and signed67-public; the public packet has
42 independently checked hashes. No APK or signing material is committed.

Paired final native reviews report Spec0 and Standards0 findings. Reviewers
independently checked both full packets, frozen references and production/source
equivalence. Public delivery review is recorded in the completion ticket comment.

Owner prerequisites remain parked in issue36 and OWNER_INPUTS.md; this delivery
does not claim provider configuration, store acceptance or physical-device proof.
