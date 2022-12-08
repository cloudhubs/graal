package com.oracle.svm.hosted.prophet.model;

import java.util.Set;

public class Module {

    private Name name;

    private Set<Entity> entities;

    public Module(Name name, Set<Entity> entities) {
        this.name = name;
        this.entities = entities;
    }

    public Name getName() {
        return name;
    }

    public void setName(Name name) {
        this.name = name;
    }

    public Set<Entity> getEntities() {
        return entities;
    }

    public void setEntities(Set<Entity> entities) {
        this.entities = entities;
    }
}
