package com.oracle.svm.hosted.prophet;

public abstract class AbstractCounter {

    protected final Logger userLogger;

    protected AbstractCounter(Logger userLogger) {
        this.userLogger = userLogger;
    }

    public abstract String dump();

    public void log() {
        userLogger.info(dump());
    }
}
