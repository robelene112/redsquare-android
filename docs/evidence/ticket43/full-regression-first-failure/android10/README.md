# First final-full Android10 failure, unchanged production

[Run39027471407](https://github.com/robelene112/redsquare-android/actions/runs/39027471407),
exact91d8fefecc71d0f71b1aa74f43b866d43b0d1c51, remains FAILURE. All six new
Chat GIF cases pass56.728s. Core114/0/0 and source/release/lint pass. The complete
preceding instrumentation records and their actual statuses are retained in
instrumentation-summary.json:157PASS/1preparation failure,37completion records
and10 earlier interrupted outputs. The later Profile stage was not reached.

The private Chat process preparation fails before intentional termination:
PrivateChatProcessRecoveryTest106 calls captureNativeScreen12 for pendingText;
UiAutomator cannot observe it within the original10s. Compose existence/Retry
checks had passed. Root viewed both original PNGs and parsed both XMLs. Failure
shows the first cached message, unknown-send status and disabled attachment
controls, with the pending message outside the visible history viewport. The
new GIF control consumes additional composer space. Earlier reaction capture
shows the typed draft and reaction status while keyboard is open.

Fixture correction scrolls the actual Private messages list to the exact
pendingText and asserts it displayed before both pre-death/cold-recovery captures.
It retains required-text capture,10/15s bounds, same originals/digests, actual PID
termination, cold recovery, zero-write assertions and device-only restoration.
No app change or longer timeout. A focused chat-history-process scope runs only
the existing process helper; full wiring is unchanged. Corrected execution is
pending. This failed run proves no private process death or cold recovery for
that stage; subsequent full-suite tails remain unexecuted. Restorer passes1/0/
0.177s. Existing encrypted originals were kept device-only.

Android17 full39027473052 continues at the original source. Prepared signed26
remains unpublished and production-equivalent; neither this failure nor a later
pass changes this run's actual conclusion. No Google/live-provider/node change.
