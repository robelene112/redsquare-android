# Durable chain cursor: observed missing processing

Exacta15df1d6 owned build FAIL1m31s, gateway9cases/old8PASS/strengthenedchain1FAIL
atGatewayChainTest:145, missing processed cursor viaHTTP. Earlier direct OWNER
range/fullblock/continuation assertions pass; observe is inert. Durable cursor,
restart/no-redownload and OFFICIAL loop remain unexecuted. Core UP-TO-DATE.
Nine manifested files retain build, seven JUnit and18 source hashes. Spec0/
Standards1: require the fourth chain request to use Full with the exact retained
height/hash/fork, not merely accept whichever mode its request implies. This
tracer proves cursor processing only; the pinned blocks contain no social events.
