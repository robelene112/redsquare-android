# Focused incoming-reaction RED, Android 10

Run 38465896724, source f8d58749. The action scope runs four cases: three pass and the incoming count times out at Fixture292; duration 36.716s. Core86 passes. Android returns raw command exit0 while reporting test failure; the required OK result gate correctly rejects the run. No history, process recovery or full acceptance ran.

Root inspected the sole failure PNG/XML: cached message and Like/Reply/Copy are present, without a reaction count. The expanded concurrency tracer and incoming implementation are later changes and were not exercised here.
