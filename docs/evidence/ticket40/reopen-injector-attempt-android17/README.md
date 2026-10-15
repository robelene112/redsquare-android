# Physical reopening correction, Android17 injector attempt

Run38760340721 at129d0901 remains FAILED: four reported, three PASS and one
FAIL71.555s. Private photo/text and group text complete physical Back dismissal,
exact unsent draft/photo/restored controls, actual reopening and one compatible
signed/raw/Relay Send, empty composer, identity/backend and scoped restoration.

Group photo fails only check(device.pressBack()) at153 after the first successful
IME/privacy/editor/Send observation. No later dismissal/reopening/send assertion
is reached for this case. Root inspected its original failure PNG: actual IME
already absent, exact photo/caption visible. Parsed XML contains only app/system
UI packages. Snapshots are separate; they do not prove every subsequent check.
All eleven PNGs and XML files retained; XML parsed. No success-only scope file.

Correction39dcc708 logs the injection acknowledgement instead of treating it as
the acceptance oracle. It retains the physical Back call and SAME bounded actual
IME-closed/draft/photo/no-wire/restored-control requirements, then physical
reopening/privacy/nativeSend/exactwire/identity checks. No timeout/budget or
production change. Corrected actual runtime and full suites remain required.
No production node/service/configuration/wallet/chain, credential or store changes;
no physical Pixel acceptance. Failed case's independent finally restoration ran.
