# Compact private photo draft attempt, Android 10

Run38716715709 exactace4d018c55776282a2baf7d4544be38ac06af15 FAILED:
3cases2PASS1FAIL36.86s. Actual selected photo RGB passes. Exact displayed draft
wait still fails at155 after20s. Root inspected the original PNG and parsed XML:
photo/Send/text are now visible, and enabled EditText text equals the exact
literal caption including emoji, quotes and newline. This proves neither a
specific AnnotatedString metadata mismatch nor a successful Compose matcher.
Back/reopening/finaltail remain unexecuted.

Next fixture compares EditableText.text exactly to the same literal, rather
than requiring equality of the entire styled AnnotatedString object. Enabled
and displayed criteria,20s bound, real raster and no-wire checks remain.
On a further failure it retains the public fixture's unmerged Compose semantics
for diagnosis. No product change is made by that fixture correction. No actual
text-loss cause is inferred, and this failed run is not relabelled successful.
