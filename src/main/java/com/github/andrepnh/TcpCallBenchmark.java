package com.github.andrepnh;

import com.google.common.collect.Iterators;
import com.google.common.collect.Sets;
import org.openjdk.jmh.annotations.*;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

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
        private static final AtomicInteger SERIAL = new AtomicInteger(20000);

        private ServerSocketPool serverPool;

        private List<SocketReading2> clientSockets;

        // TODO deadlock with low permits
        public final Semaphore writePermits = new Semaphore(500000);

        public Reader clientSocketReader;

        public Iterator<SocketReading2> cyclicClientSockets;

        @Setup(Level.Iteration)
        public void openServerSocket() {
            serverPool = new ServerSocketPool(
                Sets.newHashSet(SERIAL.getAndIncrement(), SERIAL.getAndIncrement(), SERIAL.getAndIncrement()),
                50000);
            clientSockets = serverPool.startServers()
                .stream()
                .map(clientSocket -> new SocketReading2(clientSocket, PAYLOAD_LENGTH))
                .collect(Collectors.toList());
            cyclicClientSockets = Iterators.cycle(clientSockets);
        }

        @TearDown(Level.Iteration)
        public void closeSockets() {
            serverPool.stopServers();
            try {
                serverPool.awaitStop(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                // NOOP
            }
            clientSockets.forEach(SocketReading2::closeUnchecked);
        }
    }

    private static class SocketReading2 implements AutoCloseable {
        private final Socket socket;

        private final Reader socketReader;

        private final char[] buffer;

        public SocketReading2(Socket socket, int bufferLength) {
            this.socket = socket;
            try {
                this.socketReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
            this.buffer = new char[bufferLength];
        }

        public Reader getSocketReader() {
            return socketReader;
        }

        public char[] getBuffer() {
            return buffer;
        }

        public void closeUnchecked() {
            try {
                close();
            } catch(Exception ex) {
                throw new IllegalStateException(ex);
            }
        }

        @Override
        public void close() throws Exception {
            socketReader.close();
            socket.close();
        }
    }

//    @Benchmark
    @Fork(FORKS)
    @Measurement(iterations = ITERATIONS)
    @Warmup(iterations  = WARMUP_ITERATIONS)
    public void tcpWrite(SocketWriting state) throws IOException {
        state.clientSocketWriter.write(state.payload);
        state.clientSocketWriter.flush();
    }

    @Benchmark
    @Fork(value = FORKS)
    @Measurement(iterations = ITERATIONS)
    @Warmup(iterations  = WARMUP_ITERATIONS)
    public void tcpRead(SocketReading state) throws IOException {
        SocketReading2 clientSocket = state.cyclicClientSockets.next();
        clientSocket.getSocketReader().read(clientSocket.getBuffer());
//        System.out.print("R");
        state.writePermits.release();
    }
}
