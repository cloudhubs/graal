package com.oracle.svm.hosted.prophet.model;

public class RestCall {

    private String httpMethod;
    private String parentMethod;
    private String returnType;
    private String uri;
    private boolean isCollection;
    private String restCallInClassName;

    public RestCall(String httpMethod, String parentMethod,
            String returnType, String uri, Boolean isCollection, String restCallInClassName) {

        this.httpMethod = httpMethod;
        this.parentMethod = parentMethod;
        this.returnType = returnType;
        this.uri = uri;
        this.isCollection = isCollection;
        this.restCallInClassName = restCallInClassName;
    }
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(restCallInClassName).append(", ").append(parentMethod).append(", ").append(uri)
            .append(", ").append(returnType).append(", ").append(isCollection);
        return sb.toString();
    }
    // Getter methods
    public String getHttpMethod() {
        return httpMethod;
    }

    public String getRestCallInClassName() {
        return this.restCallInClassName;
    }

    public String getParentMethod() {
        return parentMethod;
    }

    public String getReturnType() {
        return returnType;
    }

    public String getUri() {
        return uri;
    }

    public boolean isCollection() {
        return isCollection;
    }

    // Setter methods
    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }
    public void setRestCallInClassName(String className) {
        this.restCallInClassName = className;
    }
    public void setParentMethod(String parentMethod) {
        this.parentMethod = parentMethod;
    }

    public void setReturnType(String returnType) {
        this.returnType = returnType;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public void setCollection(boolean isCollection) {
        this.isCollection = isCollection;
    }
}
