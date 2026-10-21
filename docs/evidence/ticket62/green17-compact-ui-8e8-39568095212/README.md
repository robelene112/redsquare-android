# Compact conversation UI, Android 17

Run [39568095212](https://github.com/robelene112/redsquare-android/actions/runs/39568095212)
executes exact source `8e8cdd025566f75ef432d9b0c4a8a80c2edd9bf6`.
Terminal SUCCESS. Four separate180second classes pass:

- `conversation-viewport`: 101.503s.
- `private-conversation-reading`: 20.248s.
- `group-conversation-reading`: 37.663s.
- `group-conversation-polish`: 67.597s.

The private cached-opening and combined group matrices cover320dp at100/130/200%
in both themes, with actual full Details, contextual Like/Reply/Copy and real
system IME. Original readable-history requirements remain96dp at100/130% and48dp
at200%. Group130% and additive100/200% are independently bounded with all real
signed receive/ACK/protected-history/older-reader/New-messages goals retained.
Private live-reading retains130% both themes. Exact histories, wallet/node,
zero outgoing TX/Relay/reports, return/Home, peer health and restoration pass.

Four archives retain286 byte-bound originals. Root inspected current10 private
130% Dark IME, group200% Dark IME and group100% Light opening, plus17 group200%
Dark IME, full Details and actions. At large text the history remains scrollable;
long message bodies need scrolling. A keyboard-open capture can show only the
upper part of the latest bubble. Sticky-end behavior across passive layout changes
is not established by these UI controls goals.

These focused successes do not establish own-send, filtering, legacy media/queue/
process/TalkBack regressions, complete62 acceptance or signed delivery. Later
filtering tracer and failure-only own-send observations are absent from this source.
