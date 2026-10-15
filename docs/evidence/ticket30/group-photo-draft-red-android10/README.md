# Selected group photo draft RED, Android 10

Run38718728322 exact06814f6e322d9fddf2411143dc80ab02a2efc6a9 FAILED:
4cases3PASS1FAIL36.699s. The group composition case waits20s for the absent
Add photo at155. Root inspected the original failure PNG and parsed its XML;
the exact caption remains visible and no photo control exists. Selection,
Back/reopening and final recovery assertions remain unexecuted.

Both incoming viewers and private photo draft recovery pass. Later fixture
00a97cc9 fixes reopening from the Groups list after Back, reviewed Spec0/
Standards0. That correction is not run here and does not change this missing-
control criterion. Only public unsafe identity and owned synthetic image state.
