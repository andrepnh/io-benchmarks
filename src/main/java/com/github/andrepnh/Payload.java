package com.github.andrepnh;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import io.undertow.util.HttpString;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;
import okhttp3.OkHttpClient;
import okhttp3.Request;

public final class Payload {
  public static final byte[] ACTUAL;

  public static final ImmutableMap<HttpString, ImmutableList<String>> RESPONSE_HEADERS;

  static {
    OkHttpClient client = new OkHttpClient();
    // We use as payload something which is unlikely to change or become unavailable, is probably
    // served by CDNs,
    // is available both under http and https and is preferably small in size.
    var request =
        new Request.Builder()
            .url("https://en.wikipedia.org/static/images/project-logos/enwiki.png")
            .get()
            .build();
    try (var response = client.newCall(request).execute();
        var body = response.body()) {
      ACTUAL = body.bytes();
      checkState(ACTUAL.length > 0);
      RESPONSE_HEADERS =
          ImmutableMap.copyOf(
              response
                  .headers()
                  .toMultimap()
                  .entrySet()
                  .stream()
                  .map(
                      entry ->
                          Maps.immutableEntry(
                              HttpString.tryFromString(entry.getKey()),
                              ImmutableList.copyOf(entry.getValue())))
                  .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    } catch (IOException e) {
      throw new IllegalStateException(e);
    } finally {
      Utils.close(client);
    }
  }

  public static byte[] copy() {
    var copy = new byte[Payload.ACTUAL.length];
    System.arraycopy(Payload.ACTUAL, 0, copy, 0, copy.length);
    return copy;
  }

  private Payload() {}
}
