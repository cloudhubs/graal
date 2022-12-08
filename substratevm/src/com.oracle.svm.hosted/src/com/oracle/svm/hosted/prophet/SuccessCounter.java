package com.oracle.svm.hosted.prophet;

import java.util.concurrent.atomic.AtomicInteger;

public class SuccessCounter extends AbstractCounter {

    private final AtomicInteger all = new AtomicInteger();
    private final AtomicInteger successful = new AtomicInteger();

    private final String allNames;
    private final String sucessfulTriesName;

    public SuccessCounter(String sucessfulTriesName, String allNames, Logger userLogger) {
        super(userLogger);
        this.sucessfulTriesName = sucessfulTriesName;
        this.allNames = allNames;
    }

    public String dump() {
        double percent = (successful.get() / (double) all.get()) * 100.0;
        return String.format("%d/%d %,.2f%% : %s/%s", successful.get(), all.get(), percent, sucessfulTriesName, allNames);
    }

    public void incAll() {
        all.incrementAndGet();
    }

    public void incSuccess() {
        successful.incrementAndGet();
    }
}
