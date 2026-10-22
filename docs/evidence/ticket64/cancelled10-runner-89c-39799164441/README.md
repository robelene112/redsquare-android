# Cancelled hosted runner attempt

Whole Android10 dispatch39799164441 at89cb35a7a2d2633cab53497aced64bbcaff83381
was explicitly cancelled after the Wallet job stayed in SDK/acceleration setup
from14:51:11 until15:41:39. No Wallet app build or native test ran. The setup
log contains the scheduled shell script and cancellation only, so it does not
identify which silent command stalled. This is not a diagnosed app failure.

Social completed successfully. Chat native runtime, artifact retention and job
cleanup steps report success, while terminal metadata still reports its job as
in_progress. Neither partition is used for complete acceptance. The entire run
is CANCELLED and rejected. Original available archives remain externally under
`/mnt/storage/redsquare-android-artifacts/profile64-full10-89c-39799164441`.

Independent complete dispatch39804393637 uses the same exact source and inputs.
It passed all setup/build stages before cancellation of this attempt. The frozen
reference now binds that independent run; all three jobs and the whole dispatch
must succeed, with no mixing of jobs from this cancelled attempt. No requirements,
assertions, deadlines, tool pins or exclusions changed.
