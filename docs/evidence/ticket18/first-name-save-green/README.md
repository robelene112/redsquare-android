# Verified name save failure and repaired cached recovery GREEN

Android10 run38120998143 on c91877c passes all eight ordinary username cases in38.502s and the actual cold-process recovery case in3.283s. The new name-save case observes matching map/proof despite an isolated name-cache directory obstruction, displays the verified full name with a generic save warning and unchanged public key, and emits no transactions. Removing the obstruction and reopening performs a fresh lookup and saves without warning. Activity recreation preserves selected Profile; with namecheck responses changed to unavailable, cached full name/public key remain without warning. All final no-send/handshake/peer-health assertions pass.

Root individually viewed the three original PNGs and inspected XMLs. Error pair shows name/warning/key; repaired and cached pairs show the same name/key without warning. Cache restoration XML still shows independent profile-history/detail loading, which is not required for identity display. These are separate observations.

The actual-process stage kills PID4339, verifies its absence and records recovery PID4426 with no wallet/backend/work reset. Public checkpoints differ only phase/recoveredPid. Native read-only namecheck emits nothing; explicit Retry emits the exact retained original signed frame. Its request/routing signatures independently PASS pinned WASM0.3.2/Elliptic6.6.1 against the published registrar. Frame SHA256: 2ce538918bb3e8fe4c8b31a147de736f6482ed6347c4915d7ffc1801544b93b5. Current process PNGs are not retained/reviewed here; earlier original process visual evidence remains separately qualified. No live namespace, actual UI wallet replacement, power-cut or physical acceptance claim.

Original evidence SHA256:

- `capture-status.txt`: `9a271f2a916b0b6ee6cecb2426f0b3206ef074578be55d9bc94f6f3fe3ab86aa`
- `continuity.txt`: `737addf0e086b47a64f6392aad59310e896d92e06da31ad8133378c8e725f896`
- `interrupted.txt`: `1261000a71e4af163da8c337ac36d33402bf3901bc56a13a2d3f8e3334894352`
- `pending-checkpoint.json`: `0457945647c4dcde18b401273770e0b178dabc6fa735a291cd5ed982dc526023`
- `recovered-checkpoint.json`: `e995f2cd281c3a4f88efbb0fb1289f3a25e8be8a5d3a7cd5db1ae0d139e20c8d`
- `recovered.txt`: `6490d0fbafcb0ea552f1139ceb5c502c53467b5f84bc4286c8c140132514fb53`
- `username-name-save-cached.png`: `6012307413e0f22e2e793a9447e24340461760c46ae6ca1ca0d6c562147cb20f`
- `username-name-save-cached.xml`: `23c77bc966a2ff28ca09760c01eddd977b879dceee00034e7bc9849a099ca30e`
- `username-name-save-error.png`: `1f654a3420fb60308e275458bc0fc920309e067a40f87a8fa60c6ce74019d6cc`
- `username-name-save-error.xml`: `64cc4ef21fd6a6c08a9abdd91e685d99f6e7ff4cc91d44f4014edce532ed634c`
- `username-name-save-recovered.png`: `a5b875bc9aa3d25f437e9b975a02c2a35c84616385d0a7806985c3f90f75b69c`
- `username-name-save-recovered.xml`: `8794176a14dc559b055a12b998d81af537ed9f83a93c23244c5b091b88594a9c`
- `username-ui.txt`: `e43ea6227c67e40e33c6e5d5ea837739e95d5798738b3e3ec2746853cc81814b`

Derived independent verification report (network disabled, not an original emulator artifact):

- `independent-frame.json`: `b0db88480733087fd7cdd4f81eecde9248a2291de6fd941d1945a1f8ec7b5e8c`
