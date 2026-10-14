# Early visual beta

Owner priority #37 follows completed private Chat #25 before group Chat #26.
Review base: `ca2ade6a`. This is an early visual slice of #35, not final
integrated-feature or physical Pixel acceptance.

## Appearance tracer

The approved platform UI seam exercises first-install System default under
Android Dark, explicit Light while Android is Dark, persisted Light across
activity recreation, explicit Dark while Android switches to Light, persisted
Dark across recreation, and returning to System default under both settings.
It observes rendered native pixels and full device-only wallet continuity,
backend selection and absence of controlled-peer transaction/report publication.
The isolated emulator harness clears only the synthetic `.dev` application
before any feature fixtures run. No production app data is reset. Original
supported Android night mode is restored; unsupported modes fail before mutation.

Test/harness source `192976f8`. Local Gradle reports BUILD SUCCESSFUL in 1m40s for test APK; Docker wrapper
exited143 after that output. No owned build container remains; do not claim
a clean wrapper exit.
Actual RED dispatched on Android 10: Actions `38491630186`, expected source
`192976f8e115c1102e952954736785b6448a9a59`. No production appearance implementation
exists yet. Do not treat source compilation as runtime evidence.

Minimum tracer reviews `98b06800...49049d0c`: Standards zero documented breaches
or actionable heuristic smells; cleanup qualification for custom night mode.
Spec two gaps: initial default absent, explicit Dark indistinguishable from
System. Correction reviews `49049d0c...192976f8`: Standards zero; Spec zero.
These reviews do not establish whole visual-beta acceptance.

## Presentation reference and remaining delivery

Inspected pinned web tweet CSS and compact identity header: avatar/content
columns, compact name and timestamp, readable body, separators. Reviewed native
Android 17 original owner-feed and cached private-message/reaction screenshots
from ticket24 evidence. The header/actions consume most of the feed; status-bar
insets/contrast and default purple palette need repair. Preserve existing wallet,
backend, cache, draft, import/export, signed-operation and manual-recovery rules.

After actual appearance RED, implement the minimum real theme/override flow,
then compact navigation, feed and Chat presentation. Verify changed native UI on
Android 10 and 17, review original captures and separate specification/standards
reports, run final full acceptance, and publish a signed in-place beta review
with explicit unfinished-feature notes. No store submission is part of #37.

Actual Android10 RED completed: one failure/12.9s at initial System-dark
observation line61. Original failure PNG still renders light; root inspected
it and parsed its XML. Fresh synthetic reset reports Success; instrumentation
exit0 still contains FAILURES, and the harness correctly rejects it. Later
override/recreation/continuity assertions were unexecuted. Raw source/run/exit,
PNG/XML and checksums retained under appearance-red/android10.

Minimum production appearance source3a2ed238 adds System/Light/Dark and an
independent device preference, IO save/load, truthful save error and window
insets/bar contrast. Compilation, bounded reviews and native GREEN pending.
