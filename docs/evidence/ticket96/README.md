# Direct Conversation replies, issue96

The Conversation action below each post now opens a reply to that exact post.
It has a visible Reply caption and the available count. The redundant header
reply is removed; Back and history information remain. Post-body navigation stays
available for reading. Existing per-parent drafts, exact parent/thread/inherited
recipients, recreation and successful-submission return are preserved.

Review base: `bf362ddec3a287c2d26d69ef30c147f9db6c0585`. Main implementation:
`8f4f3c27953f1b73d9481083b1a913a96d0a6c88`; large-text field correction:
`9fa494eac91940b83873b03a3d7657fdbdfd8548`. The frozen complete-run and signed105
candidate source is `c1c1d1066b28fb9eff626bc27908d8c79e409382`.

The original nested-reply regression reproduces the misleading action on
Android10 run42872277261:17 of18 original cases pass, and the inline click fails
to open the composer within its original15-second goal. See [baseline](baseline/qualification.json).

Initial8f focused posting and writing pass both SDKs, but the thread viewport
fails on320dp/200% Light: the correct long parent pushes the text field below
the viewport. Both original failures remain failed in
[Android10](entry-failure-android10/qualification.json) and
[Android17](entry-failure-android17/qualification.json). The reply-only correction
reveals the ready field without requesting focus or opening the keyboard.
The existing15-second check now requires the whole field and at least80dp height.

All six corrected focused runs pass. [Original packets](focused/) and
[independent focused reviews](focused-reviews.json) cover actual signed nested
reply/recreation, exact drafts/recipients,12 thread width/font/theme cells per
SDK, native48dp controls, actual IME, reading-position restoration and writing
journeys. Two Android17 entryXML snapshots retain the previous hierarchy; their
paired original PNGs and subsequent IME XML show the correct field and draft.
This limitation is qualified, not used to waive a failed application assertion.
[The exact source bridge](focused-source-bridge.json) proves only evidence paths
changed from the focused source to the complete/candidate source.

[Source reviews](source-reviews.json) pass both axes. The [full contract](full-contract.json)
and [verifier](verify-full.py) were frozen before complete execution, retaining
95's entire634/635 identity catalog,215 classes,181 archives and all recovery,
viewport, publication and public-read gates at their original bounds.

Signed105 [binary review](artifact105/review.json) passes actual APK checks,
including established certificate continuity and the unchanged editor license.
[Staging verification](artifact105/staged.json) confirms exact immutable download
bytes/notes while the public latest remains104. Complete Android10/17, actual
signed-update and final publication gates are still pending. This evidence does
not establish physical Pixel/OEM, actual provider or Play acceptance. Parent
specifications1/55/84/86 and release36 remain open.
