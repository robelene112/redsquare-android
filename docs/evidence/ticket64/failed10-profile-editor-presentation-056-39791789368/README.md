# Rejected Android10 Back event receipt

Complete run39791789368 at exact `05680ceac13a1fb39fa5344b076ce25ea61da62f`
is FAILURE. Social stops at ProfileEditorPresentationTest line43 after7.485s,
because UiDevice.pressBack returns false. Original writing-IME and failure
captures show the keyboard open before Back, then closed with the exact unsent
biography intact. No loss or app navigation failure is established here.

The pinned UiAutomator2.4.0 source implements pressBack by waiting for
TYPE_WINDOW_CONTENT_CHANGED, whereas pressKeyCode checks the physical down/up
injection. [Official pinned source archive](https://dl.google.com/dl/android/maven2/androidx/test/uiautomator/uiautomator/2.4.0/uiautomator-2.4.0-sources.jar).
Correctionec3 uses checked KEYCODE_BACK and retains the original10s actual IME
closure goal plus every preview/save/recovery assertion. Corrected focused
Android10/17 successes are retained separately. This rejected run stays failed.

Only this failed goal's originals/logs are copied here. Whole raw run remains at
`/mnt/storage/redsquare-android-artifacts/profile64-full10-056-39791789368`.
