# Expected RED: unsent editor preview, Android10

Run39734373868 at exact14da7843948f82eebf09272989d649d7ee5ce03f is FAILURE.
ProfileEditorPresentationTest fails in7.868s/180 at the first default-Light
cell: the entered bio exists only as editable text, with no separate rendered
profile preview. The real IME was shown and hidden before this lookup.
Root inspected the actual failure original: old loose avatar/buttons and bio
field are visible, with no coherent profile preview. This is the intended
RED for the new editor slice, not acceptance. Later cells are not claimed.

This packet retains11 exact original archive members. SHA256SUMS excludes
this later README. Raw artifacts remain external at
`/mnt/storage/redsquare-android-artifacts/profile64-red10-editor-14d-39734373868`.
Reading results are separate. Native editor GREEN, media/recovery regressions,
accessibility, complete suites and signed64 remain pending.
