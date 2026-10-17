# Standalone process: intended missing entry point

Exact83cf14dc local gateway-only task FAIL51s. Existing HTTP lifecycle and renewal
journeys2PASS. New standalone process journey1FAIL at readiness assertion line89:
"Standalone gateway must stay running until ready". The placeholder command exits
without starting a listener. No challenge, bind, abrupt stop or restored-state tail
of this new journey ran. No core task, deployment, cloud or power-loss claim.
Four manifested files retain actual log, both JUnit files and nine source hashes.
