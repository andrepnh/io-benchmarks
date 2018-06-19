package com.github.andrepnh;

import com.sun.org.apache.xpath.internal.operations.Bool;
import org.openjdk.jmh.annotations.*;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Benchmarks {
    public static final int PAYLOAD_LENGTH = 64;

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

    @State(Scope.Thread)
    public static class SocketEchoState {
        private static final AtomicInteger SERIAL = new AtomicInteger();

        private AsyncEchoServer server;

        private SyncEchoClient client;

        public char[] payload;

        @Setup(Level.Trial)
        public void openServerSocket() throws IOException, ExecutionException, InterruptedException {
            int port = 25252 + SERIAL.getAndIncrement();
            server = new AsyncEchoServer();
            Log.l("starting server on %d", port);
            server.start(port);
            Log.l("startied server on %d", port);
            client = new SyncEchoClient();
            Log.l("starting client connection on %d", port);
            client.connect(port);
            Log.l("started client connection on %d", port);
            payload = new char[PAYLOAD_LENGTH];
            Arrays.fill(payload, 'a');
        }

        @TearDown(Level.Iteration)
        public void closeSockets() throws Exception {
            try {
                if (client != null) {
                    Log.l("Closing client from teardown");
                    client.close();
                    Log.l("Closed client from teardown");
                }
            } finally {
                if (server != null) {
                    Log.l("Closing server from teardown");
                    server.close();
                    Log.l("Closed server from teardown");
                }
            }
        }
    }

    @State(Scope.Thread)
    public static class SocketWriting {
        private static final AtomicInteger SERIAL = new AtomicInteger();

        private Thread serverThread;

        private ServerSocket serverSocket;

        private Socket clientSocket;

        public Writer clientSocketWriter;

        public char[] payload;

        @Setup(Level.Iteration)
        public void openServerSocket() throws IOException, ExecutionException, InterruptedException {
            int port = 10000 + SERIAL.getAndIncrement();
            serverSocket = new ServerSocket(port);
            serverThread = new Thread(() -> {
                try {
                    try (Socket clientSocket = serverSocket.accept();
                         BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {
                        char[] readBuffer = new char[payload.length];
                        while (reader.read(readBuffer) != -1) {
                            if (Thread.interrupted()) {
                                break;
                            }
                        }
                    }
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }, SocketWriting.class.getSimpleName() + ":" + port);
            serverThread.start();
            clientSocket = new Socket("localhost", port);
            clientSocketWriter = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
            payload = new char[PAYLOAD_LENGTH];
            Arrays.fill(payload, 'a');
        }

        @TearDown(Level.Iteration)
        public void closeSockets() throws Exception {
            clientSocketWriter.close();
            clientSocket.close();
            serverThread.interrupt();
            serverThread.join();
            serverSocket.close();
        }
    }

    @State(Scope.Thread)
    public static class SocketReading {
        public static final AtomicInteger COUNTER = new AtomicInteger();

        private static final AtomicInteger SERIAL = new AtomicInteger();

        private Thread serverThread;

        private ServerSocket serverSocket;

        private Socket clientSocket;

        public Reader clientSocketReader;

        private char[] payload;

        public char[] readBuffer;

        public final Semaphore writePermits = new Semaphore(50);

        @Setup(Level.Iteration)
        public void openServerSocket() throws IOException, ExecutionException, InterruptedException {
            payload = new char[PAYLOAD_LENGTH];
            readBuffer = new char[PAYLOAD_LENGTH];
            Arrays.fill(payload, 'a');
            Arrays.fill(readBuffer, 'a');
            int port = 20000 + SERIAL.getAndIncrement();
            serverSocket = new ServerSocket(port);
            serverThread = new Thread(() -> {
                try (Socket clientSocket = serverSocket.accept();
                     BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()))) {
                    // Some head start; notice the writer will block once the reader has gone through half the payloads
                    for (int i = 0; i < writePermits.availablePermits() * 2; i++) {
                        writer.write(payload);
                    }
                    writer.flush();
                    while (!Thread.interrupted() && !clientSocket.isClosed()) {
//                            System.out.println("server loop");
                        try {
                            writePermits.acquire();
                        } catch (InterruptedException e) {
                            break;
                        }
                        writer.write(payload);
                        writer.flush();
//                            System.out.println("server wrote");
//                            System.out.println("barrier released");
                    }
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }, SocketReading.class.getSimpleName() + ":" + port);
            serverThread.start();
            TimeUnit.MILLISECONDS.sleep(200);
            clientSocket = new Socket("localhost", port);
            clientSocketReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        }

        @TearDown(Level.Iteration)
        public void closeSockets() throws Exception {
            clientSocketReader.close();
            clientSocket.close();
            serverThread.interrupt();
            serverThread.join();
            serverSocket.close();
        }
    }

    //    @Benchmark
    public char[] localCall() {
        // Since most benchmarks end up reading from or writing to an array, we should do this here too
        char[] payload = new char[PAYLOAD_LENGTH];
        Arrays.fill(payload, 'a');
        return payload;
    }

    //    @Benchmark
    public void fileWrite(WritingState state) throws IOException {
        state.writer.write(state.payload);
    }

    //    @Benchmark
    public void fileRead(ReadingState state) throws IOException {
        state.reader.read(state.toRead);
        state.reader.reset();
    }

    //    @Benchmark
//    @Warmup(timeUnit = TimeUnit.MILLISECONDS, time = 200, iterations = 2)
//    @Measurement(timeUnit = TimeUnit.MILLISECONDS, time = 200, iterations = 2)
    public char[] tcpConnect(SocketEchoState state) throws IOException, ExecutionException, InterruptedException {
        Log.l("sending...");
        return state.client.send(state.payload);
    }

    //    @Benchmark
    public void tcpWrite(SocketWriting state) throws IOException {
        state.clientSocketWriter.write(state.payload);
    }

    @Benchmark
    public void tcpRead(SocketReading state) throws IOException, InterruptedException {
//        if (SocketReading.COUNTER.incrementAndGet() % 1000000 == 0) {
//            System.out.println(SocketReading.COUNTER.get());
//        }
//        System.out.println("benchmark called");
//        System.out.println("benchmark woke from phaser");
        state.clientSocketReader.read(state.readBuffer);
        state.writePermits.release();
//        System.out.println("benchmark done");
    }
}
