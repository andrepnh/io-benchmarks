package com.github.andrepnh;

public final class Log {
    public static void l(String format, Object... args) {
//        Object[] fullArgs = new Object[(args == null ? 0 : args.length) + 2];
//        fullArgs[0] = padRight(System.nanoTime(), 15);
//        fullArgs[1] = padRight(Thread.currentThread().getName(), 15);
//        System.arraycopy(args, 0, fullArgs, 2, args.length);
//        System.out.printf("%s - %s - " + format + "\n", fullArgs);
    }

    private static String padRight(Object s, int n) {
        return String.format("%1$-" + n + "s", s.toString());
    }
}
