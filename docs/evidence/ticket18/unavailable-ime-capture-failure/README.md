# Unavailable ime capture failure

Android 10 run38117893858 on89d76a2 has one failure at NativeScreenEvidence.kt:12 waiting for the unavailable error in Android's hierarchy. The unavailable test had reached its Compose error assertion and actual one Registry read, then could not capture the expected text. Original PNG and separate XML show the keyboard occupying the lower screen; the unavailable error is absent from the hierarchy. The exact cause of the earlier Compose visibility result versus later capture is not established. Later wallet/no-broadcast assertions of this case were not reached. Other four cases, including the entire import case, pass. Total35.446s. Root individually viewed this PNG and inspected XML.

The bounded fixture correction explicitly scrolls the asserted error into view before the display assertion and capture. No app change and no claim that this correction has passed yet.

Verbatim evidence SHA256:

- `username-ui.txt`: `14d4d8c32e53b58f93c37c4422d937c203ed2d9c78a6c1ebc7e28cc21e61c2ae`
- `username-unavailable-failure.png`: `9c1f4607d60c2d97649245207ffab3fd60b7dd3c9967337d64e364e27604ced3`
- `username-unavailable-failure.xml`: `022ca901e3a97c1d49518821f7240f4d1b4c7c933f41339a94a2ad57a0cb5102`
