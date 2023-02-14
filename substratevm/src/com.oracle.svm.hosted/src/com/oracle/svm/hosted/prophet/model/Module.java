package com.oracle.svm.hosted.prophet.model;

import java.util.Set;

public class Module {

    private Name name;
    //TODO: name will need refactored
    private Set<?> entities;

    public Module(Name name, Set<?> entities) {
        this.name = name;
        this.entities = entities;
    }

    public Name getName() {
        return name;
    }

    public void setName(Name name) {
        this.name = name;
    }

    public Set<?> getEntities() {
        return entities;
    }

    public void setEntities(Set<?> entities) {
        this.entities = entities;
    }
}