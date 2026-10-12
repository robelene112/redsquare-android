# Review ordering correction build

Bounded history review8b9a237...4bfe312 reports one source ordering finding on
both axes: old Activity checks authority before the history commit lock and can
later overwrite a newer saved observation. No runtime scheduler reproduction.
The minimal correction holds the existing shared lock over both current-operation
check and history commit. No new abstraction or monotonic/reorg assumption.
Debug/test build passes2m53s, one original log/hash; native fix runtime is pending.
