# First social choice compatibility checks

Both SDK notification regression packages refer to source
`495fb72cd066aef4fd0f154bcb52779d79a906c9`. Their separate manifests retain
original runtime evidence. Both actual completed runs succeed; see their READMEs
for exact case counts, PID changes, captures and limits.

The separate same-source [push run 38962198525](https://github.com/robelene112/redsquare-android/actions/runs/38962198525)
also succeeds. Its metadata is retained in source-check-run.json and covered by
this directory's manifest. The pinned workflow's push/source branch invokes
core tests, debug/application-test and release assembly, plus lintDebug. Focused
device scopes omit release/lint; those checks come from this separate source run.
No full device suite or external push acceptance is claimed.
