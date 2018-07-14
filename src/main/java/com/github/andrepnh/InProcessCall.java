package com.github.andrepnh;

import org.openjdk.jmh.annotations.*;

public class InProcessCall {

  @State(Scope.Benchmark)
  public static class LocalCallState {
    public byte[] preAllocatedArray = new byte[Payload.ACTUAL.length];
  }

  @Benchmark
  @BenchmarkMode({Mode.SingleShotTime, Mode.Throughput})
  public byte[] localCall(LocalCallState payloadCopy) {
    return newPayload(payloadCopy.preAllocatedArray);
  }

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  public byte[] newPayload(byte[] copy) {
    // Since most benchmarks end up reading from or writing to an array, we should do this here too
    System.arraycopy(Payload.ACTUAL, 0, copy, 0, copy.length);
    return copy;
  }
}
