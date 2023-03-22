package com.oracle.svm.hosted.prophet.model;

import java.util.Set;
import java.util.List;

public class Module {

    private Name name;

    private Set<Entity> entities;
    private Set<RestCall> restCalls;
    private Set<Endpoint> endpoints;

    public Module(Name name, Set<Entity> entities, Set<RestCall> restCalls, Set<Endpoint> endpoints) {
        this.name = name;
        this.entities = entities;
        this.restCalls = restCalls;
        this.endpoints = endpoints;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("MODULE NAME = ").append(name).append("\n").append("\nENTITIES = \n").append(setToString(entities))
                .append("\nREST_CALLS = \n").append(setToString(restCalls)).append("\nENDPOINTS = \n" ).append(setToString(endpoints))
                .append('\n');
        return sb.toString();
    }

    private String setToString(Set<?> l){
        StringBuilder sb = new StringBuilder();
        for (var v : l){
            sb.append("\t").append(v.toString()).append("\n");
        }
        return sb.toString();
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
    public Set<RestCall> getRestCalls() {
        return restCalls;
    }

    public Set<Endpoint> getEndpoints() {
        return endpoints;
    }

    // Setter methods
    public void setRestCalls(Set<RestCall> restCalls) {
        this.restCalls = restCalls;
    }

    public void setEndpoints(Set<Endpoint> endpoints) {
        this.endpoints = endpoints;
    }

}
