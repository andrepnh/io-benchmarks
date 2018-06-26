package com.github.andrepnh;

import com.google.common.collect.ImmutableList;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.github.andrepnh.BenchmarkParams.PAYLOAD_LENGTH;

public class ServerSocketPool {
    private static final int WRITES_TO_READ_RATIO = 100;

    private final ImmutableList<ServerSocket> servers;

    private final ExecutorService executor;

    private final Semaphore writeAheadPermits;

    public ServerSocketPool(Set<Integer> ports, int writeAheadLimit) {
        servers = ImmutableList.copyOf(ports.stream()
            .map(port -> {
                try {
                    return new ServerSocket(port);
                } catch (IOException ex) {
                    throw new IllegalStateException(ex);
                }
            }).collect(Collectors.toList()));
        executor = Executors.newFixedThreadPool(servers.size());
        writeAheadPermits = new Semaphore(writeAheadLimit);
    }

    public List<Socket> startServers() {
        for (ServerSocket server: servers) {
            executor.submit(() -> acceptConnectionAndWriteAhead(server));
        }
        try {
            TimeUnit.MILLISECONDS.sleep(200);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }

        return servers.stream()
            .map(server -> {
                try {
                    return new Socket("localhost", server.getLocalPort());
                } catch (IOException ex) {
                    throw new IllegalStateException(ex);
                }
            }).collect(Collectors.toList());
    }

    public void stopServers() {
        executor.shutdownNow();
    }

    public void awaitStop(long timeout, TimeUnit unit) throws InterruptedException {
        executor.awaitTermination(timeout, unit);
    }

    private void acceptConnectionAndWriteAhead(ServerSocket server) {
        try (Socket clientSocket = server.accept();
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()))) {
            final char[] payload = new char[PAYLOAD_LENGTH];
            Arrays.fill(payload, 'a');
            for (int i = 0; i < WRITES_TO_READ_RATIO; i++) {
                writer.write(payload);
            }
            writer.flush();
            while (!Thread.interrupted() && !clientSocket.isClosed()) {
                try {
                    writeAheadPermits.acquire();
                } catch (InterruptedException e) {
                    break;
                }
                for (int i = 0; i < WRITES_TO_READ_RATIO; i++) {
                    if (!clientSocket.isClosed()) {
                        writer.write(payload);
                        writer.flush();
//                        System.out.print("w");
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
