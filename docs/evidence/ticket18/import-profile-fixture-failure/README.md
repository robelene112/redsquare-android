# Import preview passes before profile fixture failure

Android 10 run38117628397 on3ab2ee5 reports five cases, four passes and one failure in29.444s. The new import case reaches the verified outgoing-name warning, incoming key, unchecked acknowledgement, disabled confirmation, unchanged outgoing wallet and cancellation into the named own profile. It fails at the final peer health check, line308. ControlledFeedPeer.kt:116 requires field4 even for the different supported profile-history query, producing NullPointerException. The fixture is corrected with an explicit empty Archive callback, matching the confirmed-profile test. The app is not changed for this failure.

Root individually viewed both original PNGs and inspected both XMLs. Preview shows the saved name and unchecked acknowledgement. Compose assertion establishes disabled confirmation; the generic XML enabled attribute is not the button's Compose enabled semantics. After cancellation the own profile shows the verified name and original public key. This failed run is not whole-case GREEN. Only unsafe public fixtures, restricted synthetic authority and loopback writes.

Verbatim evidence SHA256:

- `username-ui.txt`: `53e7b3db52d657c8598ed55cfeff6abae09fe0404dc763b08f9e6da98cc62e73`
- `username-import-preview.png`: `a7a15e64b0c8897693d246c052415fc494e2e393943f7cd7f359e2372c647dea`
- `username-import-preview.xml`: `4e5b9741655f9e82311c58ffbc41f3454473fa1df19f2a3f707388711a35685b`
- `username-import-failure.png`: `48d60a5cb61b226f639d07544ff1a17e1ab196fb22e9026fd5589fff5d8dc617`
- `username-import-failure.xml`: `dee1ef49da77b7a85dca4c6fb22036bc27bed1587cbc6105cb4488fd4c868733`
