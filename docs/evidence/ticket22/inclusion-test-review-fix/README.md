# Inclusion fixture review corrections

Review at3a41bf1: Spec requests repeating the existing terminal gates after
Activity recreation; Standards requests protected/identity-bound restoration.
The same journey now checks no Pending/no Retry/original backend/zero snapshot
availability after reopening. An empty snapshot does not independently prove
reservation persistence; that remains covered by separate funding/recovery
journeys. No additional native test or product change is added.

The protected region now includes initial wallet replacement, retains primary
failures with suppressed cleanup errors, accepts only the exact prior or exact
known synthetic fixture wallet, and positively verifies persisted restoration.
No backup text is logged. Test APK with all corrected assertions compiles1m20s;
one original hashed log retained. Runtime missing-Included RED and bounded
source-fix review remain pending. No restoration failure is claimed reproduced,
nor fresh Android block compatibility or whole-ticket acceptance established.
