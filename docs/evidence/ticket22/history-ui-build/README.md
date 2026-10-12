# Historical observation implementation build

Initial debug/test build passes2m43s. Subsequent primary Android API inspection
identified BigInteger.longValueExact as API31. Before native dispatch it was
replaced with a positive bitLength<=63 check followed by toLong, preserving the
checked conversion on minAPI29. No actual Android10 runtime failure is claimed.
Corrected debug/test build plus release lint passes5m52s. Both original logs are
retained with two hashes. Compilation/lint does not establish native history
acceptance; the same positively observed failing journey must run unchanged.
