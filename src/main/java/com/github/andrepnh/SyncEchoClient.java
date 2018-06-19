package com.github.andrepnh;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class SyncEchoClient implements AutoCloseable {
    private final AsynchronousSocketChannel client;

    public SyncEchoClient() throws IOException {
        client = AsynchronousSocketChannel.open();
    }

    public void connect(int port) throws ExecutionException, InterruptedException {
        InetSocketAddress hostAddress = new InetSocketAddress("localhost", port);
        Future<Void> future = client.connect(hostAddress);
        future.get();
    }

    public char[] send(char[] payload) throws ExecutionException, InterruptedException {
        ByteBuffer buffer = ByteBuffer.wrap(new String(payload).getBytes(StandardCharsets.UTF_8));
        Log.l("Client writing...");
        client.write(buffer).get();
        Log.l("Client finished writing");
        buffer.flip();
        Log.l("Client reading...");
        client.read(buffer).get();
        Log.l("Client finished reading");
        char[] echo = new String(buffer.array()).trim().toCharArray();
        buffer.clear();
        return echo;
    }

    @Override
    public void close() throws Exception {
        Log.l("closing client");
        client.close();
        Log.l("client closed");
    }
}
