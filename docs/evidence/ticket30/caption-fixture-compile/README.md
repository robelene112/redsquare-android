# Caption fixture compilation failure

Run38717605044 exact55e528e078ee378b90960d5aad9398de853e51d4 FAILED at
compileDebugAndroidTestKotlin:116, unresolved getOrNull extension. Core/debug
production compilation passed; no native runtime case executed. The correction
imports androidx.compose.ui.semantics.getOrNull, matching existing test usage.
No production or acceptance criterion changes. The runtime result remains pending.
