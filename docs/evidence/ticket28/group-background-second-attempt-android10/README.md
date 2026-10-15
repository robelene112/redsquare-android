Run38676639132 FAILED at122bb349fa823bc23367b8d2837265000a7ecb8e:
two opt-in group cases,one cancellation PASS and one initial-field failure,17.487s.
Background case fails at78 before text entry/Send: Message label exists but no
enabled SetText node. Original PNG/XML show a disabled empty Message field and
prior Forwarded creation status. Root inspected originals. Exact initialization
cause is not proved; the fixture lacks positive edit-readiness observation.
Correction waits for displayed/enabled SetText before the first edit using the
existing20s readiness bound. Reopened queued fields remain intentionally disabled.
No app change, longer deadline or removed assertion. All background send/recovery/
wire/history/key/UI assertions remain unexecuted in this run. Cancellation PASS.
This subset does not rerun private cases or establish whole28 acceptance.
