Run38577824737 at4f437d6f4c4b20789574f501ca4bc0c2d824570d: Time: 134.92, OK (17 tests)

All 17 focused cases passed. This includes signed incoming creation while the list is open, received status without outgoing Retry, live message acknowledgements, exact protected original/message/draft state and no outgoing raw/Relay transmissions through Activity recreation.

The new storage-fault case injects an owned unfinished .new record, observes matching negative acknowledgement and visible local error without overwriting bytes, waits for old authenticated connections to disappear and a replacement to authenticate, removes only its owned obstruction, and obtains a fresh correlated positive acknowledgement without Refresh. The error persists until explicit Refresh. Unanchored traffic is rejected without a new storage error; a later admitted message also sees no error. Root inspected original unavailable/recovered/reopened PNGs and parsed XMLs.

This is controlled-peer emulator evidence. Actual process death, imported group discovery, account controls, physical performance and complete26 acceptance remain separate gates. Unsafe public fixture identities only.
