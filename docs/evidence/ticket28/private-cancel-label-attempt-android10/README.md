Run38662611803 FAILED at28714c2151e72a544cb39f5ff8a093a8e9855041:
one case,onefailure7.354s at55 assertTextEquals. Raw semantics show EditableText
exactly equals the intended text, but Text also contains the field label Message.
Originals show Queued and, after cancellation, the exact editable draft with no
bubble. Queued/recreate/cancel reached; no-bubble and reachable-peer/no-wire tails
remain unexecuted. Fixture correction compares exact EditableText independently
of the label; no app behavior or deadline changes. Not whole28 acceptance.
