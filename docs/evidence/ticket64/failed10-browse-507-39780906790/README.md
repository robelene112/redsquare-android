# Rejected overly broad tab measurement, Android10

Run39780906790 at exact507d35d8192c2d8cd536010be79c06d2a0295e3a failed20.074s/180,
three of eight original methods failed. Two exact post targets became offscreen;
the cached-profile journey failed the new nonoverlap check. Root viewed the
original paging failure: the full header is back at the top, with post history
below the viewport. Unlike the prior failed attempt, this stops before paging.

The new helper matched every Role.Tab, including the NavigationBarItem controls
at the bottom of the app frame. Their lower bound caused an excessive reverse
scroll. The correction scopes the measurement to the three named history tabs,
requires all three and waits for UI idle after the semantic scroll before the
same visible/nonoverlap checks. Original15s absolute budget, exact content,
real clicks, older-liked-reply search and180s class limit remain unchanged.

All retained file hashes were verified, with10 original archive members.
SHA256SUMS excludes this later README. This failed result is not acceptance.
Corrected native execution is pending. Public63 remains current.
