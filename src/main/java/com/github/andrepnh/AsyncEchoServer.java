package com.github.andrepnh;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.atomic.AtomicInteger;

public class AsyncEchoServer implements AutoCloseable {
    private static final AtomicInteger CONNECTION_HANDLER_SERIAL = new AtomicInteger();

    private final AsynchronousServerSocketChannel serverChannel;

    private ConnectionHandler connHandler;

    public AsyncEchoServer() throws IOException {
        serverChannel = AsynchronousServerSocketChannel.open();
    }

    public void start(int port) throws IOException {
        InetSocketAddress hostAddress = new InetSocketAddress("localhost", port);
        serverChannel.bind(hostAddress);
        connHandler = new ConnectionHandler(serverChannel);
        serverChannel.accept(CONNECTION_HANDLER_SERIAL.getAndIncrement(), connHandler);
    }

    @Override
    public void close() throws Exception {
        if (connHandler != null) {
            connHandler.close();
        }
        Log.l("closing server socket");
        serverChannel.close();
        Log.l("client closed");


    }

    private static class ConnectionHandler implements CompletionHandler<AsynchronousSocketChannel, Integer>, AutoCloseable {
        private final AsynchronousServerSocketChannel serverChannel;

        private AsynchronousSocketChannel clientChannel;

        public ConnectionHandler(AsynchronousServerSocketChannel serverChannel) {
            this.serverChannel = serverChannel;
        }

        @Override
        public void completed(AsynchronousSocketChannel clientChannel, Integer handlerId) {
            if (serverChannel.isOpen()) {
                serverChannel.accept(null, this);
            }

            if (clientChannel != null && clientChannel.isOpen()) {
                Log.l("Got a new client connected");
                this.clientChannel = clientChannel;
                EchoHandler handler = new EchoHandler(clientChannel);
                HandlerContext context = HandlerContext.reading();
                clientChannel.read(context.getBuffer(), context, handler);
            }
        }

        @Override
        public void failed(Throwable exc, Integer handlerId) {
            Log.l("Got exception on conn", exc);
            exc.printStackTrace();
        }

        @Override
        public void close() throws Exception {
            if (clientChannel != null) {
                Log.l("Closing client socket");
                clientChannel.close();
                Log.l("Client socket closed");
            }
        }
    }

    private static class EchoHandler implements CompletionHandler<Integer, HandlerContext> {
        private final AsynchronousSocketChannel clientChannel;

        public EchoHandler(AsynchronousSocketChannel clientChannel) {
            this.clientChannel = clientChannel;
        }

        @Override
        public void completed(Integer result, HandlerContext context) {
            if (context.isReading()) {
                Log.l("Got a read");
                ByteBuffer byteBuffer = context.flipForWrite();
                Log.l("Writing it...");
                clientChannel.write(byteBuffer, context, this);
                byteBuffer.clear();
            } else {
                Log.l("Got a write");
                ByteBuffer byteBuffer = context.flipForRead();
                Log.l("Preparing next read");
                clientChannel.read(byteBuffer, context, this);
            }
        }

        @Override
        public void failed(Throwable exc, HandlerContext channelContext) {
            Log.l("Got exception on echo", exc);
            exc.printStackTrace();
        }
    }

    private static class HandlerContext {
        private ByteBuffer buffer;

        private boolean reading;

        private HandlerContext(ByteBuffer buffer, boolean reading) {
            this.buffer = buffer;
            this.reading = reading;
        }

        public static HandlerContext reading() {
            return new HandlerContext(ByteBuffer.allocate(Benchmarks.PAYLOAD_LENGTH), true);
        }

        public ByteBuffer flipForWrite() {
            assert reading;
            buffer.flip();
            reading = false;
            return buffer;
        }

        public ByteBuffer flipForRead() {
            assert !reading;
            buffer = ByteBuffer.allocate(Benchmarks.PAYLOAD_LENGTH);
            reading = true;
            return buffer;
        }

        public boolean isReading() {
            return reading;
        }

        public ByteBuffer getBuffer() {
            return buffer;
        }
    }
}


