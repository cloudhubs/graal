package com.oracle.svm.hosted.prophet.model;

import java.util.Set;
import java.util.List;

public class Module {

    private Name name;

    private Set<Entity> entities;
    private List<RestCall> restCalls;
    private List<Endpoint> endpoints;

    public Module(Name name, Set<Entity> entities, List<RestCall> restCalls, List<Endpoint> endpoints) {
        this.name = name;
        this.entities = entities;
        this.restCalls = restCalls;
        this.endpoints = endpoints;
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

    // Getter methods
    public List<RestCall> getRestCalls() {
        return restCalls;
    }

    public List<Endpoint> getEndpoints() {
        return endpoints;
    }

    // Setter methods
    public void setRestCalls(List<RestCall> restCalls) {
        this.restCalls = restCalls;
    }

    public void setEndpoints(List<Endpoint> endpoints) {
        this.endpoints = endpoints;
    }
}
