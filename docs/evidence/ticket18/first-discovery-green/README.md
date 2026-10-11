# Existing-wallet discovery case GREEN, capture failure

Android10 run38120023255 on e162fce passes the full new discovery journey: exact keymap and namecheck, verified full profile name with unchanged public key, optional screen ownership with no Register/Retry, zero broadcasts and healthy identity-bound peer. Six of seven cases pass in42.417s. The whole run fails the unavailable-case native capture at line114 after its Compose displayed-error assertion; later wallet/no-send assertions are not reached. Actual-process stage is not reached.

Root individually viewed both originals and inspected XML. The discovery PNG renders only a status strip in this root view; XML contains NativeCase7@saito and scalar1 address. No consistent bitmap visibility or proven rendering cause is claimed. Unavailable PNG/XML show the emulator IME and omit the error, as in the earlier capture failure; earlier/later visibility cause is unestablished. The bounded fixture correction hides only the positively observed emulator IME before scrolling/error capture, without changing app behavior. This individual-case result does not prove actual UIimport, save failures, full shared display or complete #19 acceptance.

Original evidence SHA256:

- `username-discovered-profile.png`: `129917d4cfd4c9277e0e7fc89830ef0c326a5071cf3d4bb593af7904d6fc5bfa`
- `username-discovered-profile.xml`: `33f32d31dc113d3bf130fd87c8a2eff05dc7e514a62837d9f279e043f33ef41e`
- `username-ui.txt`: `e81a95be50119fb7e29b8c6034914815807feb16e9cf2d7221b77a7bca4fc5ca`
- `username-unavailable-failure.png`: `84550339c5aef2aadbb744a078d05f2006ab6212230ff4b208f4069083fbf323`
- `username-unavailable-failure.xml`: `17bfdf9f8747c7e416dbe56b9744db70bdbf0dae58e7aeffbc425e48c53f64a1`
