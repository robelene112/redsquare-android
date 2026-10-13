# Contextual Android10 settings and actual cold notification recovery GREEN

Run38326981751 at4d3b1c8 passes main31 notifications126.643s, actual Android10
notification-settings journey1/6.559s, and cold recovery1/3.719s after an intentional
app process death5957->6040. Each completed runner has OK; no assumptions.
Core73/debug/test pass. The interrupted preparation intentionally reports Process
crashed without OK; checkpoint and disappeared PID are validated before recovery.
Public owner/backend/event checkpoint is identical except recovered phase/newPID.
No wallet reset/export, backend switch, live request or broadcast occurs.

Actual settings opens Red Square's system notification screen; back returns to
reading, explicit Refresh and recreation advance authenticated own-Likes reads
without a read error; optional offer stays dismissed. Original opaque wallet and
prior offer are preserved. Process preparation sees1unread/neutral Like/actor2,
explicit Mark confirmsNoUnread before kill. Cold process serves only failedfresh
reads, retains same Like/NoUnread/disabled Mark, opens actualactor Profile and
returns. BothpeerURL overrides are controlled, currentwallet/backend remain.

101hashes/all46XML parsed. Root viewed seven originals: Red Square Android10
system settings, post-choice reading, three process captures and bothwallet
isolation captures. Cold PNG shows actual failed-server warning plus retained
neutral Like and NoUnread. Other PNGs retained without additional visual claims.
All31 earlier journeys/strict wallet cleanup/no writes pass. Source predates
self-like warning correction2a520da and parent continuation; finalfullSDKpair and
physicalPixel remain pending. Closed-app notification delivery is not tested.
