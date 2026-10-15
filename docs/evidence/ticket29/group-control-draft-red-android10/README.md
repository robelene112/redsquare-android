# Group exact reopened draft RED, Android10

Run38695426962 exact3749a5436d8392bba9bf80012e5a4f56a0ac252c,
3cases/2PASS/1FAIL38.852s. Group mute control/save, compatible independently
signed incoming/ACK and exact draft pass. Recreation retains mute/history but
its enabled composer is empty through20s exact-draft observation at204.
Original failure PNG/XML show saved mute, cached compatible text and no draft.
Unmute/group no-wire tail unexecuted; private two cases pass.

The minimum reuses retained ChatComposerMemory for groups, with separate group
owner/backend/id keys, shared writer mutex and initialization. Edits/replies/
send-clear/cancellation update memory; pending durable work is authoritative.
Commit+memory publication remains NonCancellable on existing IO/Main contexts.
No plaintext SavedState/Bundle or new protocol. No group-alert/full29 acceptance.
Root original group failure inspected; all6XML parsed. Public unsafe fixture and
controlled peer only, original scoped state restoration remains in finally.
