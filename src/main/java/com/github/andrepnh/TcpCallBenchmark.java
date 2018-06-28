package com.github.andrepnh;

import org.openjdk.jmh.annotations.*;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.andrepnh.BenchmarkParams.*;

public class TcpCallBenchmark {
    @State(Scope.Thread)
    public static class SocketWriting {
        private static final AtomicInteger SERIAL = new AtomicInteger();

        private Thread serverThread;

        private ServerSocket serverSocket;

        private Socket clientSocket;

        public OutputStream clientOutputStream;

        public byte[] payload;

        @Setup(Level.Iteration)
        public void openServerSocket() throws IOException {
            payload = Payload.copy();
            int port = 10000 + SERIAL.getAndIncrement();
            serverSocket = new ServerSocket(port);
            serverThread = new Thread(() -> {
                try {
                    try (Socket clientSocket = serverSocket.accept();
                         BufferedInputStream inputStream= new BufferedInputStream(clientSocket.getInputStream())) {
                        byte[] readBuffer = new byte[payload.length];
                        while (inputStream.read(readBuffer) != -1) {
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
            clientOutputStream = new BufferedOutputStream(clientSocket.getOutputStream());
        }

        @TearDown(Level.Iteration)
        public void closeSockets() throws Exception {
            clientOutputStream.close();
            clientSocket.close();
            serverThread.interrupt();
            serverThread.join();
        }
    }

    @Benchmark
    @Fork(FORKS)
    @Measurement(iterations = ITERATIONS)
    @Warmup(iterations  = WARMUP_ITERATIONS)
    public void tcpWrite(SocketWriting state) throws IOException {
        state.clientOutputStream.write(state.payload);
        state.clientOutputStream.flush();
    }

}
