# Private native notification RED, Android10

Run38693284699 exactcc74746c0764d63573556eaa1a9188a53d45950e,
2cases/1PASS/1FAIL30.514s. Compatible unmuted incoming text and positive ACK
pass; line82 then times out20s waiting for an actual Android notification.
Original failure PNG/XML show received text with the unmuted bell, no alert.
Generic body/category/contentIntent/system shade assertions are unexecuted.
The other private mute/draft/recreation/unmute/no-wire case passes.

The source implementation at this point has no NotificationManager posting path.
Next minimum adds actual live private alert after retention, deduplicates signed
original IDs including undecryptable retained ciphertext, freshly checks selected
public identity/backend and durable policy, and defaults to generic New message.
Muted/account muted/blocked/unreadable policy/permission denied stays silent.
Archive, outgoing messages and private reactions remain silent. Closed-app push
and group notifications are not implemented by this minimum.

The later reviewed fixture84f8e98a strengthens silence observation without changing
the missing positive-alert criterion. Root failure original inspected/4XML parsed.
Public unsafe fixture only, no production message; fixture restoration retained.
