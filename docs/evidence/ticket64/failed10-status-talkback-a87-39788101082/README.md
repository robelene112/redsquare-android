# Rejected Android10 TalkBack prerequisite

Complete run39788101082 at exact `a87a4a942b3ca09de0178bd841657b5c719cbbf6`
is FAILURE. Chat stops in TalkBackStatusPreviewTest before opening the app,
when NativeTalkBackObservation's15s readiness requires a missing API29 field.
The original failure dump shows TalkBack bound/enabled and touch exploration
active, but no `serviceHandlesDoubleTap` attribute. Actual speech and app focus
therefore did not run in this goal. No passing pieces constitute acceptance.

[Android documents the double-tap service flag as added in API30](https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo#FLAG_SERVICE_HANDLES_DOUBLE_TAP).
Correction232 keeps bound/enabled service, touch exploration and the15s limit,
requiring the additional flag only on API30+. Real speech/focus assertions and
restoration are unchanged. Corrected native execution remains required.

This packet contains this failed goal's originals and pinned tool/settings
metadata. The complete external raw run remains at
`/mnt/storage/redsquare-android-artifacts/profile64-full10-a87-39788101082`.
