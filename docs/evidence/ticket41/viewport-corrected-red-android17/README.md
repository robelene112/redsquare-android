# Second viewport attempt, wrong warning literal

Run38795296345 at9453287df98e2299dc28d68a8025d54ad97b069d FAILS6cases5PASS/1FAIL172.061s. Cancellation case again stops at30s warning wait164 BEFORE physical bounds. It expects group_error, but the actual send error changes to chat_automatic_send_error when the queued attempt becomes automatic. History/live warning checks are correct. This is another fixture setup failure, not physical-oracle RED; later physical/recreation/cancellation/no-wire assertions are unexecuted.

Root viewed both original queued/failure PNGs and parsed both XMLs. First has the generic group error, second has automatic-retry error; both occupy72pixels above the same history/live warnings. Both show cancellation text clipped3pixels at626..629 and its clickable parent clipped36pixels at604..640. GroupChatScreen explicitly selects these alternative send-error strings from automaticMessage/pendingMessage. Recognize either actual send warning rather than assume the transient generic text persists. No new storage fault or production state change is needed.

Queued original stays scoped to owner, history empty, alternate raw/Relay counts zero in selected checkpoints. FORWARDED is prior group creation, not queued-message delivery. Core114/0failures/0ignored and debug/test build pass. Separate observations are not atomic pairs. Production minimum still unapplied.
