# Missing unread state RED and existing reply viewport failure

Android10run38305833616 atc15db1a reports15tests/13PASS/2failures58.728s,
zero completed OK records/deaths/assumptions; core73/debug/test compile passes.
Read-history case warms the valid like then fails at line260: 1 unread is absent.
Mark all as read/recreation/read-recovery/actor/final changed-response checks are
not reached. Original PNG/separate XML show owner/Notifications/Refresh/actor/like,
without unread status.

Existing reply case fails at line47 asserting the parent displayed after its
semantics appeared. Original PNG/separate XML show Conversation still Loading
replies, selected reply and actions; parent text is not visible. This does not
prove timing, scroll anchoring or a protocol failure. Fixture now explicitly
scrolls its existing conversation list to the known parent before preserving the
same displayed assertion, as existing quote tests do. The other13cases pass,
including null-repost cache recovery. Five hashes retain both failures and log.
No read status, cold-death, full SDK or whole-ticket acceptance is claimed.
