# Unreadable notification policy and repair GREEN, Android10

Run38700873867 exact9fc36231d911347da45458c8884adab14740e03e,
7PASS74.080s. An actual generic private Android notification establishes the
positive path. With only the isolated fixture policy unreadable, a new signed
incoming message is acknowledged under RPC779, retained and readable without
an alert. Removing that fixture file repairs the original absent policy. The
same original under RPC780 remains one readable message without an alert;
a fresh signed message under RPC781 produces an actual generic Android alert.
The prior six private/group mute, notification, failed-save and dedup cases pass.

Root inspected the three new policy-failure/repair PNGs; all29XML parsed.
Both reviews of later12af76e9 cleanup correction report zero findings. That
fixture-only correction preserves primary failures if cleanup also fails; this
run predates it and does not execute it. Production and acceptance assertions
are unchanged. No backend/identity/process, physical/closed-app or whole29
acceptance inferred.
