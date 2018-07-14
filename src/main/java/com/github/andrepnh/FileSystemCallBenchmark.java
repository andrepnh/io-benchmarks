package com.github.andrepnh;

import static com.google.common.base.Preconditions.checkState;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.BenchmarkParams;

public class FileSystemCallBenchmark {
  @State(Scope.Benchmark)
  public static class WritingState {
    private File file;

    public OutputStream outputStream;

    public byte[] payload;

    @Setup(Level.Iteration)
    public void prepareForWrite() throws IOException {
      file = new File(UUID.randomUUID().toString());
      checkState(file.createNewFile(), "Could not create file %s", file.getName());
      file.deleteOnExit();
      outputStream = new BufferedOutputStream(new FileOutputStream(file));

      payload = Payload.copy();
    }

    @TearDown(Level.Iteration)
    public void deleteFile() throws IOException {
      try {
        if (outputStream != null) {
          outputStream.close();
        }
      } finally {
        if (file != null) {
          // Requesting file deletion on JVM termination takes care of deleting files when tbe
          // benchmark
          // process is interrupted. Now we're just making sure files are deleted as soon as no
          // longer necessary
          // to avoid disk space issues
          checkState(file.delete(), "Could not delete file %s", file.getName());
        }
      }
    }
  }

  @State(Scope.Benchmark)
  public static class ReadingState {
    private File file;

    public BufferedInputStream inputStream;

    public final byte[] toRead = new byte[Payload.ACTUAL.length];

    @Setup(Level.Iteration)
    public void prepareForRead(BenchmarkParams params) throws IOException {
      file = new File(UUID.randomUUID().toString());
      checkState(file.createNewFile(), "Could not create file %s", file.getName());
      file.deleteOnExit();
      try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file))) {
          bos.write(Payload.ACTUAL);
      }
      inputStream = new BufferedInputStream(new FileInputStream(file));
    }

    @TearDown(Level.Iteration)
    public void closeReader() throws IOException {
      if (inputStream != null) {
        inputStream.close();
      }
    }
  }

  @Benchmark
  public void fileWrite(WritingState state) throws IOException {
    state.outputStream.write(state.payload);
    state.outputStream.flush();
  }

  @Benchmark
  public int fileRead(ReadingState state) throws IOException {
    return state.inputStream.read(state.toRead);
  }
}
