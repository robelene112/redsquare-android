# Photo and text compatibility at the pinned web receiver boundary

Canonical helpers build-support/references/chat-photo-caption-reference.cjs and
chat-photo-caption-browser.cjs execute source-pinned Browser.sanitize(msg,true)
for A9a4ca7e and B214504ec, followed by the inspected Chat newline conversion.
Source and dependency mounts are named/read-only, network is disabled, the
container environment is empty, and Saito is never initialized.

The proposed native message contains one complete img.img-prev followed by a
newline and a div containing only escaped caption text. Actual A/B sanitizer
outputs match. Offline Chromium DOM decodes the literal12x8 reference raster
at RGB220/30/40, and its caption innerText equals the exact literal emoji,
special characters and two-line text. This verifies the receiver sanitization
and DOM boundary, not live Chat, signed native transmission or transport receipt.

Reference generation used existing node24.13.0-alpine and pinned dependency
modules. Browser proof used existing playwright1.63.0-noble plus matching module,
network-none/read-only/cap-drop/no-new-privileges and tmpfs for Chromium. No live
node configuration, database, wallet or process was accessed or modified.
