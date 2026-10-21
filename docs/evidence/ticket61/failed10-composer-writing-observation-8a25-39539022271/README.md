# Android10 mixed-layout writing observation failure

Run39539022271 executes exact8a25abc026729a139bc895179df5828a273f32c6 and
concludes FAILURE. The focus method completes both130%themes, real IME/Send,
exact leave/return, identity/node/zeroTX and restoration assertions, then fails
its unchanged deferred writing-order assertion for Light. All later classes
are unreached. No failed overall method is counted as acceptance.

Separate bounds observations report Light writing214..425 versus attachmentTop346;
Dark reports138..337 versus346. Root viewed Light writing PNG: actual IME,
visible writing above the image toolbar. That later capture is not atomic with
the earlier bounds. IME-driven app-header movement can occur between separate
node reads; the revised operator reads both live bounds in one UI-thread action
and additionally requires positive dimensions. Original ordering/IME/return
assertions and180second class bound remain unchanged. Corrected runtime pending.

Lifecycle JSONL records two configuration-driven Activity replacements immediately
after the explicit-recreation boundary, without reproducing the earlier stage
exception. This supports overlapping relaunches, not a proven cause of that
historical failure. Revised fixture requests Activity.recreate inside onActivity
and retains the15second bound with a positive different-instance/resumed/focused/
320dp/font/Home gate. It does not retry a failed recreation or skip goals.

One archive retains21 original members byte for byte. Raw logs, exact run/source
metadata and original capture/lifecycle records remain independent of later runs.
This test source predates the4c9202c3 busy-status priority fix. No full#61 or
physical/provider/store acceptance is claimed.
