# Interrupted private submission coverage GREEN on Android 10

Run38349463109 at8615857 reports six passing native cases25.282s, one OK,
no assumptions or actual process deaths; core82/source gates pass. Twenty-eight
records retain runner/core/metadata, twelve original PNG/XML pairs and public
unsafe encrypted native envelope. Root parsed all XMLs and viewed interrupted
and recreated originals: outgoing message with Submitted/not confirmed plus
send failure, then same message/state after reopening with unavailable Archive.
No Forwarded claim and exactly one propagation/zero Relay observations for the
interrupted case. The existing implementation passes this new coverage directly;
no RED is invented. Other normal-send captures are separate. Shared envelope
filename is the later normal-send capture. This is socket interruption plus
Activity recreation, not actual process death or complete uncertain recovery.
