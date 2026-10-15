# Chat keyboard layout evidence for #35

Checked 2026-10-15. Research only: no app/test/config changes, builds, CI,
deployment, node or wallet access. #30 remains the sole implementation task.
No new runtime or acceptance result is claimed.

## Observed baseline

[Build configuration](../../app/build.gradle.kts) sets min29, target36 and
Compose BOM2026.06.01. [MainActivity](../../app/src/main/kotlin/nl/davidengelen/redsquare/MainActivity.kt)
uses `fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)` followed by
12dp horizontal/4dp vertical padding. Although that file has no direct
`enableEdgeToEdge` call, [RedSquareTheme](../../app/src/main/kotlin/nl/davidengelen/redsquare/ui/RedSquareTheme.kt)
**does call `activity.enableEdgeToEdge` in `SideEffect`**, around this content.
Neither [manifest](../../app/src/main/AndroidManifest.xml) nor
[theme](../../app/src/main/res/values/themes.xml) specifies `windowSoftInputMode`.
[Private](../../app/src/main/kotlin/nl/davidengelen/redsquare/chat/ChatScreen.kt)
and [group](../../app/src/main/kotlin/nl/davidengelen/redsquare/chat/GroupChatScreen.kt)
layouts have fixed header/composer content and a weighted `LazyColumn`.

Existing Android17 evidence records panning, header overlap with the status bar
and inaccessible Send with IME open at 320x640:
[private queue attempt](../evidence/ticket30/queue-correction-android17/README.txt),
[group attempt](../evidence/ticket30/group-phase-attempt-android17/README.txt).
The group's [original PNG](../evidence/ticket30/group-phase-attempt-android17/captures/group-chat-evidence/archive-failure.png)
was inspected and its companion XML parsed during this research: entered draft
remains, Send is not visible. No successful Send invocation follows from those
taps. Later keyboard-closing fixtures establish their recorded delivery behavior,
**not keyboard-open usability**.

## Stable contracts

- Omitted adjustment defaults to `adjustUnspecified`: the system chooses resize
  or pan. Pan protects the focused editor and can obscure other controls.
  `adjustResize` is the explicit resize policy;
  `adjustNothing` delegates all accommodation to inset handling.
  [Activity contract](https://developer.android.com/guide/topics/manifest/activity-element#wsoft).
- SDK35+ targets are edge-to-edge on Android15+; older Android versions need
  explicit enablement. Target36 cannot opt out on Android16+.
  [Edge-to-edge guide](https://developer.android.com/develop/ui/views/layout/edge-to-edge),
  [target36 change](https://developer.android.com/about/versions/16/behavior-changes-16#edge-to-edge).
- [The exact BOM](https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/2026.06.01/compose-bom-2026.06.01.pom)
  maps foundation-layout to1.11.4. Its
  [published sources](https://dl.google.com/dl/android/maven2/androidx/compose/foundation/foundation-layout-android/1.11.4/foundation-layout-android-1.11.4-sources.jar),
  `androidMain/.../WindowInsets.android.kt`, define
  `safeDrawing = systemBars.union(ime).union(displayCutout)`.
  The IME declaration recommends manifest `adjustResize` and decor fitting
  disabled; detection works API23+, with synchronous platform animation API30+.
  Thus Android10 and17 both fall within supported detection, with different
  underlying animation mechanisms. No dependency upgrade is indicated.
- Compose inset padding consumes applied space for descendants. Nested
  `imePadding()` below the current root should therefore add no already-consumed
  IME space. Raw `asPaddingValues()` bypasses consumption and can double-pad;
  reading inset pixels in composition can lag a frame. Prefer layout modifiers.
  [Compose consumption contract](https://developer.android.com/develop/ui/compose/system/insets-ui).

## Recommended bounded approaches, unproved until runtime

1. **First candidate:** explicitly request `adjustResize`; retain existing
   edge-to-edge and root `safeDrawing`. This addresses reliance on platform
   pan selection. Do not merely add another `imePadding()` or call edge-to-edge
   again. Modern [platform flag deprecation](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/view/WindowManager.java)
   favors inset handling; it does not negate the inspected AndroidX compatibility
   recommendation.
2. If screen-specific ownership is needed, let the root handle system bars and
   cutout, and apply `imePadding()` to the complete chat pane containing history
   **and composer**. Keep one coordinated inset owner. Adding it only to the
   list cannot reserve space for its sibling composer. A Scaffold migration
   additionally requires applying/consuming its padding, not duplicating it.
   [Material inset guidance](https://developer.android.com/develop/ui/compose/system/material-insets).

Either approach still needs constrained-height design: a weighted list cannot
shrink fixed headers, quotes and previews below their own height. Keep identity,
privacy indication, editable draft and reachable Send; compact secondary controls
into accessible disclosure and bound previews without changing message data.
Keep existing signing, queue/cancel, encrypted drafts and explicit Send semantics.
Hiding the keyboard inside Send's callback cannot help a tap that never reaches
Send.

Verify real keyboard-open physical taps on Android10/17, the 320x640 case and
Pixel, including multiline text, photo/reply, queue/cancel, navigation modes,
rotation and font scaling. Check actual header/composer bounds, draft continuity
and one explicit send. Whether resize alone resolves the observed layout, and
whether additional compaction is necessary, remain unproved.
