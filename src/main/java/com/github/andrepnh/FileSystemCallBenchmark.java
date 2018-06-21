package com.github.andrepnh;

import org.openjdk.jmh.annotations.*;

import java.io.*;
import java.util.Arrays;
import java.util.UUID;

import static com.github.andrepnh.BenchmarkParams.*;

public class FileSystemCallBenchmark {
    @State(Scope.Benchmark)
    public static class WritingState {
        private File file;

        public BufferedWriter writer;

        public char[] payload;

        @Setup(Level.Iteration)
        public void prepareForWrite() throws IOException {
            file = new File(UUID.randomUUID().toString());
            assert file.createNewFile();
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
                    // TODO files not deleted
                    assert file.delete();
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
            assert file.createNewFile();
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                Arrays.fill(toRead, 'a');
                writer.write(toRead);
            }
            reader = new BufferedReader(new FileReader(file));
            reader.mark(toRead.length * 2);
        }

        @TearDown(Level.Iteration)
        public void deleteFile() throws IOException {
            try {
                if (reader != null) {
                    reader.close();
                }
            } finally {
                if (file != null) {
                    assert file.delete();
                }
            }
        }
    }

    @Benchmark
    @Fork(FORKS)
    @Measurement(iterations = ITERATIONS)
    @Warmup(iterations  = WARMUP_ITERATIONS)
    public void fileWrite(WritingState state) throws IOException {
        state.writer.write(state.payload);
    }

    @Benchmark
    @Fork(FORKS)
    @Measurement(iterations = ITERATIONS)
    @Warmup(iterations  = WARMUP_ITERATIONS)
    public void fileRead(ReadingState state) throws IOException {
        state.reader.read(state.toRead);
        state.reader.reset();
    }
}
