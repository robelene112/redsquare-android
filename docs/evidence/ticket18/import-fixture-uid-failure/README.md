# Import fixture setup failure

Android 10 run38116827086 on11e92b9 reports five cases, four passes and one failure, total20.776s. Import case fails atUsernamePreviewTest.kt:264 before launching its Activity: instrumentation cannot create the test-provider directory from the target process. No import screenshot or acceptance assertion was reached. This is fixture setup evidence, not the intended missing-name RED. The corrected setup uses host adb run-as for the separate provider UID, matching existing profile/import fixtures. Only public unsafe scalar2 wallet data is provisioned.

Original raw instrumentation log retained verbatim. SHA256: `a35005a654553f45acdff41f41053f9458efd9ac3bed3a023413d73ab8741ab4`.
