# First group photo Send regression, Android 10

Run38721331728 exactc71e647be1fa3b5701e292e2e58cb0cf0188f448 FAILED:
6cases5PASS1FAIL29.808s. Group draft recovery and explicit Send reach one raw
and one Relay with the same signed original. The test then fails at185, its
incorrect reversed-order recipient expectation. The actual recipient list is
not logged by this assertion. Source inspection identifies that the ordinary
outer Relay constructor prepends the signer. Correction5f85b5e1 expects exact
[wallet,contact] in both contexts and was reviewed Spec0/Standards0.

Root inspected the original failure PNG and parsed XML. The screenshot still
shows Queued while the asynchronous UI publication follows wire delivery; this
is not proof of a publication defect. Outgoing caption/empty-composer/envelope
capture/finaltail are unexecuted. The captured group envelope needed for the
independent constructor proof is therefore not produced by this run. No group
acceptance is inferred. Private Send and four earlier cases pass.
