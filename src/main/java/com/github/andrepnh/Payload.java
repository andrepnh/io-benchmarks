package com.github.andrepnh;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import io.undertow.util.HttpString;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkState;

public final class Payload {
    public static final byte[] ACTUAL;

    public static final ImmutableMap<HttpString, ImmutableList<String>> RESPONSE_HEADERS;

    static {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
            .url("https://en.wikipedia.org/static/images/project-logos/enwiki.png")
            .get()
            .build();
        try (
            Response response = client.newCall(request).execute();
             ResponseBody body = response.body()) {
            ACTUAL = body.bytes();
            checkState(ACTUAL.length > 0);
            RESPONSE_HEADERS = ImmutableMap.copyOf(response.headers().toMultimap().entrySet().stream()
                .map(entry -> Maps.immutableEntry(HttpString.tryFromString(entry.getKey()),
                    ImmutableList.copyOf(entry.getValue())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            Utils.close(client);
        }
    }

    public static byte[] copy() {
        byte[] copy = new byte[Payload.ACTUAL.length];
        System.arraycopy(Payload.ACTUAL, 0, copy, 0, copy.length);
        return copy;
    }

    private Payload() { }
}
