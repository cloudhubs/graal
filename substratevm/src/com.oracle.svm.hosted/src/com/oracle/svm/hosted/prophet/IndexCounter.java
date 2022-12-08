package com.oracle.svm.hosted.prophet;

import java.util.concurrent.ConcurrentHashMap;

public class IndexCounter extends AbstractCounter {

    private final String name;
    private final ConcurrentHashMap<Object, Integer> indexes = new ConcurrentHashMap<>();

    public IndexCounter(String name, Logger userLogger) {
        super(userLogger);
        this.name = name;
    }

    public void inc(Object index) {
        indexes.compute(index, (__, prev) -> prev != null ? prev + 1 : 1);
    }

    public String dump() {
        return String.format("%s: %s", name, indexes);
    }
}