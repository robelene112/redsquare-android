# Existing social receiver regression

Actual Android10 run39059979882 at8c09aa207e8f9600d6a308d485b6c674ba60708d passes all15 focused existing cases after the shared receipt implementation and union preflight change: binding1case/1.393s, social presentation/tap11cases/86.315s and worker3cases/46.514s. Eight hashed records retain all three raw instrumentation/exit pairs, run metadata and exact source hashes.

The serialized Social receipt filename, key alias, prefix and schema remain unchanged. This focused scope does not run Social process-death recovery, Android17 or complete app acceptance; those remain final full gates. Pure-decode correction14211039 follows this immutable run and is not retroactively covered. No Google operation or production deployment is claimed.
