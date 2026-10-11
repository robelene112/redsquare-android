# Lost acknowledgement and reopening RED

Android10 run38169041012 at0378129:2cases,1PASS/1FAIL27.207s. Lost-reply case
receives one signed report at controlled authenticated peer, observes unknown
semantics, recreates Activity and fails ReportPreviewTest.kt:95 waiting for
retained unknown status. First confirmation/acknowledgement case still passes.

Root individually viewed3originals and separately parsed3XMLs. They reveal a
second limitation: neither lost-acknowledgement nor acknowledged-feed original
shows the status text in this small viewport, despite Compose assertIsDisplayed
passing. XML also omits those texts. The exact layout cause was not diagnosed;
no claim that native visible delivery feedback is already accepted. New native
UiDevice presence checks will expose this gap before product correction. First
GREEN still proves signed routing/semantics only. Both recovery and actual
visible status remain unfinished. No physical-device or public-write proof.
