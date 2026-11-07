# Android compatibility snapshot

This module preserves Compose Rich Editor `v1.0.0-rc13`, commit
`8342c9eb5b602232fe1b78187a891ae64b61eb2f`, with a targeted immutable-text fix.
It replaces the Maven editor dependency for issue94, retaining the application's
Kotlin2.1/AGP8 toolchain and the approved editor API/design.

The original Android17 Pixel journey failed with `OffsetMapping` mapping45 to45
against original length44. A persistent form avoids unnecessary editor remounts,
but does not repair the mutable transformation closure itself. The model now
captures its immutable annotated text at both transformation constructors, as in
[upstream746](https://github.com/MohamedRejeb/compose-rich-editor/pull/746).
Mapping validation remains enabled. No clamping or exception suppression is used.

`UPSTREAM.json` records every original blob/SHA256 and local SHA256. All62 Kotlin
sources are unchanged except the model's two captures and four mechanical platform
binding files: common `expect` declarations are removed and Android `actual`
modifiers become ordinary declarations. Their implementations are unchanged.
Each modified source carries its local-change notice. The exact changes are retained in `android-immutable-transform.patch`. The64-file
inventory also includes the original consumer rules and Apache2 license, packaged
in the APK under `META-INF/compose-rich-editor-LICENSE.txt`.

The Android wrapper uses the app's pinned Compose BOM and the original parser
versions. This is not the complete upstream746 parser/edit-pipeline rewrite.
A future replacement must preserve the Visual/Markdown source, cursor/input,
selection, lifecycle and native rendering regressions before removing this snapshot.
