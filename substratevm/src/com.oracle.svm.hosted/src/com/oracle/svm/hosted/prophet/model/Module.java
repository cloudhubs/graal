package com.oracle.svm.hosted.prophet.model;

import java.util.Set;

public class Module {

    private Name name;
    private Set<?> layerItems;

    public Module(Name name, Set<?> layerItems) {
        this.name = name;
        this.layerItems = layerItems;
    }

    public Name getName() {
        return name;
    }

    public void setName(Name name) {
        this.name = name;
    }

    public Set<?> getLayerItems() {
        return layerItems;
    }

    public void setLayerItems(Set<?> layerItems) {
        this.layerItems = layerItems;
    }
}