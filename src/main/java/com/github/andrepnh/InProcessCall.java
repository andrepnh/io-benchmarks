package com.github.andrepnh;

import org.openjdk.jmh.annotations.*;

import java.util.Arrays;

import static com.github.andrepnh.BenchmarkParams.*;

public class InProcessCall {
    @Benchmark
    @Fork(FORKS)
    @Measurement(iterations = ITERATIONS)
    @Warmup(iterations  = WARMUP_ITERATIONS)
    public char[] localCall() {
        return newPayload();
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public char[] newPayload() {
        // Since most benchmarks end up reading from or writing to an array, we should do this here too
        char[] payload = new char[PAYLOAD_LENGTH];
        Arrays.fill(payload, 'a');
        return payload;
    }
}
