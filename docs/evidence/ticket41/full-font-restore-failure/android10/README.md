# Full Android 10 stopped at font-cleanup assertion

Actual run [38808096905](https://github.com/robelene112/redsquare-android/actions/runs/38808096905) at 36b66a7d81a5019ecd9ba67a52982ff019f12bb5 concludes FAILURE. Source/core/release/lint pass, core 114 with zero failures/ignored. Appearance passes one case in 9.306 seconds. Keyboard reports ten cases, nine pass and one fails in 66.152 seconds.

The first private long reply/photo/font1.3 case reaches its final explicit-send image and application preservation/no-repeat assertions, then fails the newly added exact persisted-font-setting comparison in independent finally cleanup (line 251). Its recorded prior setting is absent (`null`) and prior rendered resource is 1.0. The previous immediate error did not retain the observed setting. No cause, successful exact restoration or successful test completion is inferred. The later nine keyboard cases pass. All later full-suite gates are unexecuted.

Root viewed the original failing-case explicit-send and keyboard-open PNGs; all retained XML parses. Only that synthetic case's PNG/XML, four numeric font-profile records, raw class logs and run/core metadata are selected. No wallet snapshots, secrets or private content are copied. These are controlled public unsafe fixture identities. The pre-existing separate focused font passes did not execute the failing new assertion.

Diagnostic ac9f0ea4 retains the actual public setting/resource readback and adds it to the unchanged exact assertion message. It does not relax restoration, timing or application acceptance. A focused observation is required before choosing a correction. Android17 full is still a separate pending run; signed24 is prepared but unpublished. This failed full run stays a failure.
