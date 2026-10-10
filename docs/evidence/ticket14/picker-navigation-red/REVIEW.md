# Picker navigation loses recently typed text before the correction

RED run [38072955931](https://github.com/robelene112/redsquare-android/actions/runs/38072955931),
test sourcec3b2aaa160e718e47ce5ef243bdc5b0e6cb1fb1f, production unchanged312f58c.
The first phase prepares red media with preliminary text, deletes its source,
releases Espresso Intents, types `Recover prepared image after its source disappears`,
opens the real picker and kills the app without a selection/result or Save.
The interrupted log and host PID check establish actual app-process death.

The cold phase verifies retained red pixels, then fails its unchanged final-text
assertion: the UI contains `Prepared image before opening another picker`.
This reproduces loss of the newer text, rather than a guessed picker timeout.
Neither phase publishes before that assertion; no live transaction is involved.

Individually inspected PNG/XML limits:

- picker-before-process-death.png shows the final text and red image in the
  outgoing app window. The subsequent XML observes com.google.android.photopicker
  with `No photos yet` before process termination. The earlier bitmap is not
  visual proof that the picker transition has finished.
- failure.png shows retained red pixels in the cold composer; its text is above
  the captured viewport. The assertion trace and failure.xml independently
  expose the preliminary text. Do not infer the text from that bitmap.

The fix saves and reopens encrypted authored work before external navigation,
prevents launch when saving fails, and keeps results bound to the originating
composer. GREEN and fresh full Android10/17 verification remain outstanding.
