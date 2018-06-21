package com.github.andrepnh;

import io.undertow.Undertow;
import io.undertow.util.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.openjdk.jmh.annotations.*;

import java.io.IOException;
import java.util.Arrays;

import static com.github.andrepnh.BenchmarkParams.*;


public class HttpCallBenchmark {
    @State(Scope.Thread)
    public static class LocalHttpState {
        private Undertow server;

        public OkHttpClient client;

        public Request request;

        @Setup(Level.Iteration)
        public void startServer() {
            server = Undertow.builder()
                .addHttpListener(8080, "localhost")
                .setHandler(serverExchange -> {
                    serverExchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
                    serverExchange.getRequestReceiver().receivePartialBytes((exchange, content, last) -> {
                        if (last) {
                            exchange.getResponseSender().send("");
                        }
                    });
                }).build();
            server.start();
            client = new OkHttpClient();
            request = new Request.Builder().url("http://localhost:8080").build();
        }

        @TearDown(Level.Iteration)
        public void stopServer() {
            // TODO properly dispose client
            server.stop();
        }
    }

    @State(Scope.Thread)
    public static class RemoteHttpState {
        public OkHttpClient client;

        public Request request;

        @Setup(Level.Iteration)
        public void prepareRequest() {
            // TODO properly dispose client
            client = new OkHttpClient();
            request = new Request.Builder()
                // TODO might be https actually
                // TODO different "payload" size
                .url("http://google.com")
                .build();
        }
    }

    @State(Scope.Thread)
    public static class RemoteHttpsState {
        public OkHttpClient client;

        public Request request;

        @Setup(Level.Iteration)
        public void prepareRequest() {
            // TODO properly dispose client
            client = new OkHttpClient();
            // TODO different "payload" size
            request = new Request.Builder().url("https://google.com").build();
        }
    }

    @Benchmark
    @Fork(FORKS)
    @Measurement(iterations = ITERATIONS)
    @Warmup(iterations  = WARMUP_ITERATIONS)
    public String localHttpRequest(LocalHttpState state) throws IOException {
        return state.client.newCall(state.request).execute().body().string();
    }

    @Benchmark
    @Fork(FORKS)
    @Measurement(iterations = ITERATIONS)
    @Warmup(iterations  = WARMUP_ITERATIONS)
    public String remoteHttpRequest(RemoteHttpState state) throws IOException {
        return state.client.newCall(state.request).execute().body().string();
    }

    @Benchmark
    @Fork(FORKS)
    @Measurement(iterations = ITERATIONS)
    @Warmup(iterations  = WARMUP_ITERATIONS)
    public String remoteHttpsRequest(RemoteHttpsState state) throws IOException {
        return state.client.newCall(state.request).execute().body().string();
    }
}
