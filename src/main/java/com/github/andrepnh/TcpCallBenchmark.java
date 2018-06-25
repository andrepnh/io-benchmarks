package com.github.andrepnh;

import org.openjdk.jmh.annotations.*;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.andrepnh.BenchmarkParams.*;

public class TcpCallBenchmark {
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
                    serverSocket.close();
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
        }
    }

    @State(Scope.Thread)
    public static class SocketReading {
        private static final AtomicInteger SERIAL = new AtomicInteger();

        private static final int WRITE_TO_READS_RATIO = 1000;

        private Thread serverThread;

        private ServerSocket serverSocket;

        private Socket clientSocket;

        public Reader clientSocketReader;

        private char[] payload;

        public char[] readBuffer;

        public final Semaphore writePermits = new Semaphore(50000);

        @Setup(Level.Iteration)
        public void openServerSocket() throws IOException, InterruptedException {
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
                    char[] largePayload = new char[PAYLOAD_LENGTH * writePermits.availablePermits() / WRITE_TO_READS_RATIO];
                    Arrays.fill(largePayload, 'a');
                    writer.write(largePayload);
                    writer.flush();
                    largePayload = null;
                    while (!Thread.interrupted() && !clientSocket.isClosed()) {
                        try {
                            writePermits.acquire();
                        } catch (InterruptedException e) {
                            break;
                        }
                        for (int i = 0; i < WRITE_TO_READS_RATIO; i++) {
                            if (!clientSocket.isClosed()) {
                                try {
                                    writer.write(payload);
                                    writer.flush();
                                    System.out.print("w");
                                } catch (IOException e) {
                                    throw new IllegalStateException(e);
                                }
                            }
                        }
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
            serverThread.interrupt();
            serverThread.join();
            serverSocket.close();
            clientSocketReader.close();
            clientSocket.close();
        }
    }

    @Benchmark
    @Fork(FORKS)
    @Measurement(iterations = ITERATIONS)
    @Warmup(iterations  = WARMUP_ITERATIONS)
    public void tcpWrite(SocketWriting state) throws IOException {
        state.clientSocketWriter.write(state.payload);
        state.clientSocketWriter.flush();
    }

    @Benchmark
    @Fork(value = FORKS, jvmArgsAppend = {"-Xms4096m", "-Xmx4096m"})
    @Measurement(iterations = ITERATIONS)
    @Warmup(iterations  = WARMUP_ITERATIONS)
    public void tcpRead(SocketReading state) throws IOException {
        state.clientSocketReader.read(state.readBuffer);
        System.out.print("R");
        state.writePermits.release();
    }
}
