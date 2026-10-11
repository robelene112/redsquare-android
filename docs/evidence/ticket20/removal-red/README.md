# Removed-anchor recovery RED

Run [38158038045](https://github.com/robelene112/redsquare-android/actions/runs/38158038045), source `09f3f4fc52534f876af8b5b40f8e6c6c906cc3b7`, Android 17 emulator. Six reported tests, five pass and one fails, 88.615 seconds.

The controlled recovery-directory sync failure happens after deletion of the old-policy anchor and creates a nonempty recovery-path obstruction. Mounted UI shows the explicit recovery error, Retry, and no Unmute action; authored content stays withheld. After repairing only that obstruction and opening a fresh Activity, the failed Unmute takes effect: the reference root appears and the full-list absence assertion fails at PersonalControlsPreviewTest.kt:526. Explicit repair and final identity/transaction assertions in this new journey are not reached. This is Activity reopening, not process death or a physical power-loss test.

The root agent viewed both original PNGs individually and separately parsed both original XML files. The first shows the recovery dialog; the second shows the previously muted reference post after reopening. Other five journeys pass in the raw instrumentation log. No production server or wallet material is included.
