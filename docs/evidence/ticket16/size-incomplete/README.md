# Incomplete initial size checks

Android10 run38080627218 and Android17 run38080629357 at05f89ef.
Both instrumentation logs start the first size case, declare two cases, then
end without a completed case, assertion stack or OK result. The harness has
a180s limit. Neither run establishes minimum-size behavior or a failure cause.

No usable in-scope failure screenshot/stage capture was retained. Later
diagnostics record stages and an independent final window. The revised fixture
rotates an already opened video instead of trying to scroll the small landscape
feed. This is not proof that scrolling caused these incomplete runs.
