# Native YouTube verification

Active #17, fixed review base711e42e. Parent approved seams1–4 apply: observable
native feed/thread flows against controlled Saito and provider boundaries;
verified signed content, failure/lifecycle recovery and pinned web behavior.
Read docs/research/NATIVE_GIF_VIDEO_CONTRACT.md for source/API provenance.

Completed #15 has full Android10/17 acceptance. #16 remains open with an external
Android-authorized Giphy-key/URL-retention dependency; no credentials were reused.
#17 is independently ready (native prerequisite#8 closed), assigned sequentially.
No node, wallet configuration, production transaction or store write.

First core tracer RED: compatible first YouTube link API absent, compile failed
in24s. GREEN: exact M7lc1UVf-VE canonical target passes in42s. UI tracer launches
the native feed with a controlled signed post and provider boundary. It requires
zero provider requests before a visible tap, player readiness after tap, close
back to the retained post and no emitted transaction. Provider code deliberately
has no playVideo implementation, so automatic playback cannot pass readiness.
An initial local test-compile attempt used a nonexistent transaction timestamp
property; corrected to the fixture's explicit public timestamp. No runtime
result is claimed until the hosted tracer completes.
