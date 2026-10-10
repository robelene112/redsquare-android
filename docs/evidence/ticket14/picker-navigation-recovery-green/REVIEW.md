# Newest text survives external picker process death after correction

Focused run [38073556210](https://github.com/robelene112/redsquare-android/actions/runs/38073556210),
sourcecb3d9c391ccc1599c4e57020568398d32a00a1b4, verifies the strengthened
three-process tracer: first actual death occurs after new text and prepared
red media are saved before opening a real picker without a result. The cold
phase requires the exact final text and red pixels without the deleted source,
then produces one signed uncertain candidate and kills again. The third
process confirms that exact candidate without another broadcast and clears it.
Both PID/crash stages and the successful reconciliation log are retained.

Individually inspected cold-draft.png shows retained red pixels. Final text is
outside its viewport; cold-draft.xml and the successful unchanged text assertion
establish its recovery. The bitmap alone does not prove text recovery.

The overall focused run FAILS another case: the real system-picker fixture
cannot find its synthetic image. Its separate failed group log is retained.
At timeout, its captured XML observes the native app, not a picker. This is
not a successful complete image scope. A fixture scheduling hypothesis is
under investigation: the old fixture immediately switches to UiAutomator after
clicking, while the new save-before-launch coroutine is asynchronous. The
strengthened real-picker fixture waits through Compose for actual external
foreground before inspecting media. No unique cause is established by this run.

New storage-refusal and rotation assertions, and fresh full10/17 acceptance,
remain pending. No live transaction, production write, APK publication or
physical-device claim follows from this controlled recovery result.
