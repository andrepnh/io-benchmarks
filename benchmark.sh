#!/bin/bash

# Benchmarks using default configuration:
# - Fail on error
# - SingleShot BenchmarkMode, because some benchmarks must be called only once for every
#   benchmark state's @Setup method (we are avoiding Level.iteration in setup and tear down).
#   One example is the file read benchmark, we must know exactly how many reads are going to
#   happen so that we have that many writes in setup.
# - We enable warmup iterations and increase measured iteratations since we rely only on 
#   Single Shot and don't want to measure cold starts.
# - Time units in nanoseconds because we have a baseline benchmark that does nothing

java -jar target/benchmarks.jar -foe true -bm ss -wi 5 -i 20 -tu ns
