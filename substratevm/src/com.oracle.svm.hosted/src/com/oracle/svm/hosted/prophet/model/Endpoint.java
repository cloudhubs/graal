package com.oracle.svm.hosted.prophet.model;

import java.util.List;

public class Endpoint {
    
    private String httpMethod;
    private String parentMethod;
    private List<String> arguments;
    private String returnType;
    private String path;
    private boolean isCollection;

    public Endpoint(String httpMethod, String parentMethod, List<String> args, 
                    String returnType, String path, Boolean isCollection) {

        this.httpMethod = httpMethod;
        this.parentMethod = parentMethod;
        this.arguments = args;
        this.returnType = returnType;
        this.path = path;
        this.isCollection = isCollection;
    }
    // Getter methods
    public String getHttpMethod() {
        return httpMethod;
    }

    public String getParentMethod() {
        return parentMethod;
    }

    public List<String> getArguments() {
        return arguments;
    }

    public String getReturnType() {
        return returnType;
    }

    public String getPath() {
        return path;
    }

    public boolean isCollection() {
        return isCollection;
    }

    // Setter methods
    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public void setParentMethod(String parentMethod) {
        this.parentMethod = parentMethod;
    }

    public void setArguments(List<String> arguments) {
        this.arguments = arguments;
    }

    public void setReturnType(String returnType) {
        this.returnType = returnType;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setCollection(boolean isCollection) {
        this.isCollection = isCollection;
    }
}
