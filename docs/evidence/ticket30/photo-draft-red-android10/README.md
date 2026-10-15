# Selected private photo draft RED, Android 10

Run38714801378 exact7dfe86852bf9b54f8c3deff0fd08c07d8f89404b FAILED:
3cases,2PASS/1FAIL33.118s. Both incoming viewers pass. Selected private photo
case times out20s waiting for enabled Add photo at142. The original failure PNG
and parsed XML show the exact composed caption and no attachment control.
Root inspected that original PNG. Photo selection/recovery/no-wire tail is
unexecuted; production has no picker yet.

The later963b1675 fixture correction requires actual normalized JPEG pixels
after selection and reopening, reviewed Spec0/Standards0. It does not change
the absent-control criterion, and is not executed in this earlier RED run.
Only public unsafe test identities and an owned synthetic image are involved.
