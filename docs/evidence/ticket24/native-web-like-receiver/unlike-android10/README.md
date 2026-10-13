# Captured outgoing toggle interoperability, Android10

Input originals from ../../ui/private-unlike-android10-green/native-sent-{like,unlike}-envelope.json. Each is independently checked against the actual pinned constructor, full envelope/body and WASM/elliptic signing, with exit0. Native UI separately proves those distinct operations toggle on then off and persist cleared state. The reference receiver operates at its method boundary; native dispatch deduplication is separate pending work. No real delivery/production writes.
