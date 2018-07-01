package com.github.andrepnh;

import com.google.common.collect.Iterators;
import org.eclipse.collections.api.map.ImmutableMap;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.impl.factory.Maps;
import org.openjdk.jmh.annotations.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkState;

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
                    // Requesting file deletion on JVM termination takes care of deleting files when tbe benchmark
                    // process is interrupted. Now we're just making sure files are deleted as soon as no longer necessary
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
        public void prepareForRead() throws IOException {
            file = new File(UUID.randomUUID().toString());
            checkState(file.createNewFile(), "Could not create file %s", file.getName());
            file.deleteOnExit();
            try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file))) {
                bos.write(Payload.ACTUAL);
            }
            inputStream = new BufferedInputStream(new FileInputStream(file));
            inputStream.mark(toRead.length * 2);
        }

        @TearDown(Level.Iteration)
        public void closeReader() throws IOException {
            if (inputStream != null) {
                inputStream.close();
            }
        }
    }

    @State(Scope.Thread)
    public static class NoResetReadingState {
        private static final int WRITES_TO_READ = 1000;

        private static final int PARALLEL_WRITERS = Runtime.getRuntime().availableProcessors() - 1;

        private ExecutorService executor;

        private ImmutableMap<File, InputStream> fileInputStreams;

        public Semaphore writePermits;

        public Iterator<Map.Entry<InputStream, byte[]>> cyclicReadContext;

        @Setup(Level.Iteration)
        public void prepareForRead() {
            executor = Executors.newFixedThreadPool(PARALLEL_WRITERS);
            writePermits = new Semaphore(PARALLEL_WRITERS * WRITES_TO_READ);
            MutableMap<File, InputStream> files = Maps.mutable.ofInitialCapacity(PARALLEL_WRITERS);
            for (int i = 0; i < PARALLEL_WRITERS; i++) {
                try {
                    File file = new File(UUID.randomUUID().toString());
                    checkState(file.createNewFile(), "Could not create file %s", file.getName());
                    file.deleteOnExit();
                    BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(file));
                    files.put(file, inputStream);
                    executor.submit(() -> {
                        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file))) {
                            while (!Thread.interrupted()) {
                                writePermits.acquire();
                                for (int j = 0; j < WRITES_TO_READ && !Thread.currentThread().isInterrupted(); j++) {
                                    bos.write(Payload.ACTUAL);
                                }
                            }
                        } catch (InterruptedException ex) {
                            // OutputStream closed, we're out of the loop so NOOP
                        } catch (IOException ex) {
                            throw new IllegalStateException(ex);
                        }
                    });
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }
            fileInputStreams = files.toImmutable();
            cyclicReadContext = Iterators.cycle(fileInputStreams.valuesView()
                .collect(inputStream -> Map.entry(inputStream, new byte[Payload.ACTUAL.length]))
                .toBag());
            try {
                // Giving some head start for writers
                TimeUnit.MILLISECONDS.sleep(200);
            } catch (InterruptedException e) {
                // NOOP
            }
        }

        @TearDown(Level.Iteration)
        public void closeReaderAndWriters() {
            executor.shutdownNow();
            try {
                executor.awaitTermination(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                // NOOP
            }
            fileInputStreams.forEachKeyValue((file, inputStream) -> {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (!file.delete()) {
                    System.err.format("Could not delete file %s, it will have to be deleted manually\n", file.getName());
                }
            });
        }
    }

    @State(Scope.Benchmark)
    public static class PrePairedWritesState {
        public static final int WRITE_READ_PAIRS = 100000;

        private File file;

        public BufferedInputStream inputStream;

        public final byte[] toRead = new byte[Payload.ACTUAL.length];

        @Setup(Level.Iteration)
        public void prepareForRead() throws IOException {
            file = new File(UUID.randomUUID().toString());
            checkState(file.createNewFile(), "Could not create file %s", file.getName());
            file.deleteOnExit();
            try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file))) {
                for (int i = 0; i < WRITE_READ_PAIRS + 10; i++) {
                    bos.write(Payload.ACTUAL);
                }
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
    @BenchmarkMode({Mode.SingleShotTime, Mode.Throughput})
    public void fileWrite(WritingState state) throws IOException {
        state.outputStream.write(state.payload);
        state.outputStream.flush();
    }

    @Benchmark
    @BenchmarkMode({Mode.SingleShotTime, Mode.Throughput})
    public void fileReadWithoutReset(NoResetReadingState state) throws IOException {
        Map.Entry<InputStream, byte[]> context = state.cyclicReadContext.next();
        context.getKey().read(context.getValue());
        state.writePermits.release();
    }

    @Benchmark
    @BenchmarkMode({Mode.SingleShotTime, Mode.Throughput})
    @OperationsPerInvocation(PrePairedWritesState.WRITE_READ_PAIRS)
    public int fileReadWithPrePairedWrites(PrePairedWritesState state) throws IOException {
        int foo = 0;
        for (int i = 0; i < PrePairedWritesState.WRITE_READ_PAIRS; i++) {
            foo += state.inputStream.read(state.toRead);
        }
        return foo;
    }
}
