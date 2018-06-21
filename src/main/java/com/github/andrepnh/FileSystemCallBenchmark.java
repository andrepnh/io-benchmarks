package com.github.andrepnh;

import org.openjdk.jmh.annotations.*;

import java.io.*;
import java.util.Arrays;
import java.util.UUID;

import static com.github.andrepnh.BenchmarkParams.*;
import static com.google.common.base.Preconditions.checkState;

public class FileSystemCallBenchmark {
    @State(Scope.Benchmark)
    public static class WritingState {
        private File file;

        public BufferedWriter writer;

        public char[] payload;

        @Setup(Level.Iteration)
        public void prepareForWrite() throws IOException {
            file = new File(UUID.randomUUID().toString());
            checkState(file.createNewFile(), "Could not create file %s", file.getName());
            file.deleteOnExit();
            writer = new BufferedWriter(new FileWriter(file));

            payload = new char[PAYLOAD_LENGTH];
            Arrays.fill(payload, 'a');
        }

        @TearDown(Level.Iteration)
        public void deleteFile() throws IOException {
            try {
                if (writer != null) {
                    writer.close();
                }
            } finally {
                if (file != null) {
                    // Requesting file deletion on JVM termination takes care of deleting files when tbe benchmark
                    // process is interrupted. Now we're just making sure files are deleted as soon no longer necessary
                    // to avoid disk space issues
                    checkState(file.delete(), "Could not delete file %s", file.getName());
                }
            }
        }
    }

    @State(Scope.Benchmark)
    public static class ReadingState {
        private File file;

        public BufferedReader reader;

        public final char[] toRead = new char[PAYLOAD_LENGTH];

        @Setup(Level.Iteration)
        public void prepareForRead() throws IOException {
            file = new File(UUID.randomUUID().toString());
            checkState(file.createNewFile(), "Could not create file %s", file.getName());
            file.deleteOnExit();
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                Arrays.fill(toRead, 'a');
                writer.write(toRead);
            }
            reader = new BufferedReader(new FileReader(file));
            reader.mark(toRead.length * 2);
        }

        @TearDown(Level.Iteration)
        public void closeReader() throws IOException {
            if (reader != null) {
                reader.close();
            }
        }
    }

    @Benchmark
    @Fork(FORKS)
    @Measurement(iterations = ITERATIONS)
    @Warmup(iterations = WARMUP_ITERATIONS)
    public void fileWrite(WritingState state) throws IOException {
        state.writer.write(state.payload);
        state.writer.flush();
    }

    @Benchmark
    @Fork(FORKS)
    @Measurement(iterations = ITERATIONS)
    @Warmup(iterations = WARMUP_ITERATIONS)
    public void fileRead(ReadingState state) throws IOException {
        state.reader.read(state.toRead);
        state.reader.reset();
    }
}
