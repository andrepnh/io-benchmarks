package com.github.andrepnh;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

public class Tcp {
  @State(Scope.Thread)
  public static class SocketWriting {
    private static final AtomicInteger SERIAL = new AtomicInteger();

    private Thread serverThread;

    private ServerSocket serverSocket;

    public Socket clientSocket;

    public OutputStream clientOutputStream;

    public byte[] payload;

    public int port;

    @Setup(Level.Iteration)
    public void openServerSocket() throws IOException {
      payload = Payload.copy();
      port = 10000 + SERIAL.getAndIncrement();
      serverSocket = new ServerSocket(port);
      serverThread =
          new Thread(
              () -> {
                try {
                  try (var clientSocket = serverSocket.accept();
                      var inputStream = new BufferedInputStream(clientSocket.getInputStream())) {
                    var readBuffer = new byte[payload.length];
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
              },
              SocketWriting.class.getSimpleName() + ":" + port);
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
  public void tcpWrite(SocketWriting state) throws IOException {
    state.clientOutputStream.write(state.payload);
    state.clientOutputStream.flush();
  }

  // To mimic resource open/close we have on http benchmarks
  @Benchmark
  public void tcpWriteWithStreamCreationAndDisposition(SocketWriting state) throws IOException {
    try (var stream = new BufferedOutputStream(state.clientSocket.getOutputStream())) {
      stream.write(state.payload);
    }
  }

  // To mimic open/close and first connection we might have on http benchmarks, specially if there's
  // no warmup or keep alive
  @Benchmark
  public void tcpWriteWithSocketCreationAndDisposition(SocketWriting state) throws IOException {
    try (var socket = new Socket("localhost", state.port);
        var stream = new BufferedOutputStream(socket.getOutputStream())) {
      stream.write(state.payload);
    }
  }
}
