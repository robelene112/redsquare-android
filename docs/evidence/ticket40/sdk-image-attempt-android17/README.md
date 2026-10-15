# Android17 system-image setup failure

Run38761787929 at39dcc708 FAILED before the emulator or application tests.
SDK manager downloaded x86_64-37.0_r06.zip then reported Error reading Zip content
from a SeekableByteChannel while unpacking. No native keyboard result exists;
its missing device-evidence directory is expected. Core/debug/test source build
completed earlier, but this is not runtime acceptance or an app failure.

Exact failed SDK excerpt and run metadata retained. Raw failed-step log remains
local .scratch/keyboard40-reopen17-ack-failed.log; artifacts on storage under
keyboard40-reopen17-ack-attempt. No cause such as network corruption or bad
published package is established. One unchanged bounded retry uses a fresh
hosted runner after this scope ended. No SDK/tool/dependency update or app/assertion/
timeout change was made. SDKmanager also emitted an Android CLI deprecation
notice; that notice is separate from the Zip failure and does not prove its cause.
