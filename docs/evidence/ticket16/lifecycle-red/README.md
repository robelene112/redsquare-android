# Deliberate playback observation before state events

Focused Android10 run38083125042 ata3647ad: four cases29.266s, first three
pass; the lifecycle tracer fails line272 after10s waiting for native playing
status, with exactly one controlled provider request. It reached and tapped
the provider's public Play button. The individually inspected original PNG
and separately captured XML both expose Playing controlled video, while the
native instruction still says to start playback. No real media is depicted.

The fixture's play method only accepts a trusted button gesture and emits
documented YouTube state1. The production embed had not subscribed to state
events. This establishes the missing native observation, not background pause,
recreation or actual YouTube playback. Raw captures/logs are preserved.
