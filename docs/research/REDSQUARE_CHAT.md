# Red Square chat integration

Read-only inspection of `/opt/saito/node` on 2026-10-07 found that Red Square
can embed a chat interface, supplied by a separate Saito Chat module.

- `mods/redsquare/redsquare.js`, around line 2418, obtains an optional module
  responding to `chat-manager` and registers it with the Red Square main view.
- `mods/redsquare/lib/main.js` checks for that capability and gives the chat
  manager desktop or mobile containers. A chat view is unavailable without it.
- `mods/redsquare/lib/menu.template.js` conditionally adds a Chat navigation item.
- `config/modules.config.js` includes `chat/chat.js` in both the server and
  browser module lists on this node.

Thus messaging can be part of the web Red Square experience, while its protocol
and UI are owned by a separate module. Native messaging would require integrating
that capability; its first-release scope has not yet been decided.

The public Saito homepage also lists Chat separately from RedSquare:
https://saito.io/
