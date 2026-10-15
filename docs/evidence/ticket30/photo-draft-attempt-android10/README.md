# First private photo draft minimum attempt, Android 10

Run38715743979 exact0438be5b31c56214c8ed91f74401ccdbd713d29f FAILED:
3cases2PASS1FAIL36.616s. Photo selection and actual normalized raster pass,
but exact displayed caption readiness times out20s after selection at155.
Root original PNG inspected and XML parsed. The enabled EditText XML contains
the exact expected caption, but the screenshot clips the text field and Send
button at the bottom. This demonstrates an unusable composer layout on this
small viewport; it does not establish a text-loss defect or the precise matcher
failure cause. Back/reopening/finaltail are unexecuted.

The next production correction uses a compact100dp composer photo preview,
omits redundant Add photo while attached, and bounds the text field to four
visible lines with internal scrolling. Normal feed/history previews keep180dp.
The exact caption/raster/recovery criteria remain unchanged. Autosave fix79f8e12d
is not executed in this earlier run; it was separately reviewed Spec0/Standards0.
