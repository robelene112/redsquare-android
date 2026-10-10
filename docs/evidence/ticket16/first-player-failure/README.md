# First player runtime failure

Source18027d6: Android10 run38079399700 and Android17 run38079401831.
Each test reached the signed post and tapped Watch on YouTube, then timed out
waiting for readiness. Test totals22.570s and31.930s respectively.

Both retained original PNG files were inspected separately at original size.
They show YouTube, Close video, the load-failure explanation, Retry and Open in
YouTube. No active player is visible. The screenshots prove this visible
failure state only. Provider request counts, errors and JavaScript console
details were not captured, so the failure's cause is not established.

These are failed intermediate checks, not accepted playback evidence.
