package com.github.andrepnh;

import org.openjdk.jmh.annotations.Benchmark;

public class Empty {

  @Benchmark
  public void baseline() {
    // noop, measures jmh overhead
  }
}
