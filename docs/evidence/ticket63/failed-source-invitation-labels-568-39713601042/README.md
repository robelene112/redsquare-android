# Source gate rejects invitation label resource access

[Run39713601042](https://github.com/robelene112/redsquare-android/actions/runs/39713601042)
executes568d1feae43910cd2275990d75ddeba2635e693f and completed FAILURE in its source
build stage. The terminal failed-stage log reports two lint errors; the first is
LocalContextGetResourceValueCall at GroupInvitationDialog.kt:74, Copy's resource
lookup through LocalContext. The source also uses that pattern for the share label.
Core/native compilation and the later focused device passes do not satisfy this
failed lint gate. This source run has no device execution or native acceptance.

The bounded fix resolves both labels through stringResource during composition
and captures those current values in device-action callbacks. No lint suppression,
baseline, payload or permission change. Corrected debug/performance lint and final
native/source acceptance remain required. Public beta62 is unchanged.

run.json and the complete original terminal failed-stage log are retained and
bound by SHA256SUMS; this README is excluded. Exact external originals remain at
/mnt/storage/redsquare-android-artifacts/group63-source-labels-568-39713601042.
