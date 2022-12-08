package com.oracle.svm.hosted.prophet;

import java.time.Instant;

public class Logger {

    private final String componentName;

    private final boolean ignoreTrace;

    private Logger(String componentName, boolean ignoreTrace) {
        this.componentName = componentName;
        this.ignoreTrace = ignoreTrace;
    }

    public static Logger loggerFor(Class<?> clazz, boolean ignoreTrace) {
        return new Logger(clazz.getName(), ignoreTrace);
    }

    public static Logger loggerFor(Class<?> clazz) {
        return new Logger(clazz.getName(), false);
    }

    public void warn(String msg) {
        print("WARN", msg);
    }

    private void print(String prefix, String msg) {
        System.out.printf("%s : %s : %s : %s\n", timestamp(), componentName, prefix, msg);
    }

    private static String timestamp() {
        return Instant.now().toString();
    }

    public void warn(Throwable ex) {
        warn(ex.getClass().getName() + " : " + ex.getMessage());
    }

    public void debug(String msg) {
        print("DEBUG", msg);
    }

    public void info(String msg) {
        print("INFO", msg);
    }

    public void trace(String msg) {
        if (!ignoreTrace) {
            print("TRACE", msg);
        }
    }
}