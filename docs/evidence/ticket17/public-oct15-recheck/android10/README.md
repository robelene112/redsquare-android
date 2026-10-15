# Public YouTube recheck, Android 10, 2026-10-15

Run [38681078177](https://github.com/robelene112/redsquare-android/actions/runs/38681078177), exact source `e3edf1c971c25f49d5b793d98cfb2de85dd70aac`.
The second independently signed reference selects `bHQqvYy5KYo`. One test failed
in 52.838 seconds, waiting 30 seconds for Playing at PublicYouTubeReadTest:64.
The public Play control was found and activated. The original ready capture
shows the player and start guidance; the failure capture and separate XML show
“This video can only be watched on YouTube.” with Retry and Open in YouTube.
That product copy maps to provider codes 101/150; the numeric code was not
captured and its underlying cause remains unproved. Browser numeric diagnostics
show a paused video with readyState 0, not successful playback. The later
Playing/close/feed assertions were not reached. No production Saito write or
wallet import occurred. #17 remains open; this is not real playback acceptance.
Root inspected both original screenshots and separately parsed both XML files.
