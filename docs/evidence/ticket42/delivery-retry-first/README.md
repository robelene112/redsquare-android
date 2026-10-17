# Lost-send recovery: first controlled observation

Exact7facb232 verification PASS1m10s, gateway7/0/0, coreUPTODATE. Production source
unchanged. Both backend loops exercise a socket close after request reception,
retained recipient resolution, restart and same-wake recovery while Archive head
is hidden, then accepted dedup and fresh post-opt-out suppression. Eight manifested
files retain raw log, six JUnit and source hashes.

Reviews Standards1/Spec1 identify one timing oracle: the immediate post-restart
count assumes the minimum deadline has not elapsed; immediate suppression plus a
later recovery sample does not prove an exact10second scheduling floor. A corrected
fixture must observe attempt timing and tolerate a legitimately due early retry.
This passing observation is superseded, not final recovery acceptance. No actual
cloud acceptance, forced process stop or new production fix is inferred.
