# Native named-group Chat verification

Active #26, review basebbdd121c0c707ad28ae22a997dd95707dc860f7c.
#37 is closed and signed14 is published; original21of35 complete count unchanged.
No #26 app UI, membership/history recovery or live write acceptance yet.

## Creation reference

Fetched immutable public SaitoTech/saito node/mods/chat/chat.js at
A9a4ca7e9442eb5e872168ced361ce741d7d139d7 and
B214504ecece76d1cd20a245fe4468eccbfca2951. SourceSHA256:
A35e372b7d9da0ddfd6286a93059c00a3e34eb494b62350db88a9380c4814b1af;
B28a8609343e75dec7982498c0bafd7aa99be0a499156f43c5a543435426209eb.
The actual sendCreateGroupTransaction method was extracted and executed forboth
pins against a minimal wallet/network/keychain system-boundary fixture. Its source
method and resulting signed envelope are identical atboth. Unicode/literalmarkup
name, creator/self-first recipients, duplicate-self skipping, group ID/keychain
creation and one broadcast/direct-relay event are observed.

Independent signing uses public npm saito-wasm0.3.2 and elliptic6.6.1 withbase-58
0.0.1, Node24.13.0. ActualWASMsha matchesf59be69dbb949368ce1fc7ef47d414cbe0281a86a206fe4bfb1128a4d087e01e.
WASM serializers/signaturepreimage/hash/deserialization and independent Elliptic
verification pass. Public unsafe scalar1/2/3/4 fixtureidentities only; never fund.
System timestamp/random group scalar are deterministic in this bounded fixture.
No Saito application initialization, walletfile, production network or public write.
The generated public vector is core/src/test/resources/native-group-chat-creation-reference.json.
This is constructor/wire proof, not complete group-authority/runtime interoperability.

First runtime attempt reused an older reference helper path under production
node_modules; named dependency load failed MODULE_NOT_FOUND before reference
execution. Read-only serviceWorkingDirectory and named package-directory checks
found no usable reference package at that old location. No live node was changed.
Switched to isolated public npm packages in ignored scratch, ignore-scripts and
empty separate npm configfiles. Initial same/dev/null user/global config failed
before installation; distinct emptyconfigs fixedsetup. Generator then ran in a
read-only, network-disabled non-root Node container andpassedbothpins.

## First native tracer

Core GroupChat public creation factory vs exact independent full signed envelope
and signature, plus authenticated author/recipients. Initial stub deliberately
throws UnsupportedOperationException. RED:1testfailed UnsupportedOperationException/0.466s, build1m4s.
Factory implementation then GREEN:1PASS, build1m3s; original exactenvelope/signature/
author/recipient assertions preserved. Evidence ticket26/creation-red andcreation-green.
Pre-agreed protocol/wallet seam from#1; no new seam approval needed.

All further create/invite/join/member/leave/history/persistence and Android10/17
acceptance still pending. Protected untracked NATIVE_GROUP_CHAT_CONTRACT.md is a
separate preexisting research file and remains untouched.
