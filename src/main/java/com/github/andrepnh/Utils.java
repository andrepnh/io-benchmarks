package com.github.andrepnh;

import okhttp3.OkHttpClient;

public final class Utils {
  /** Properly disposes an OkHttpClient to avoid stray running threads */
  public static void close(OkHttpClient client) {
    client.connectionPool().evictAll();
    client.dispatcher().cancelAll();
    client.dispatcher().executorService().shutdownNow();
  }

  private Utils() {}
}
