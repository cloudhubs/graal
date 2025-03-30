package com.oracle.svm.hosted.prophet.model;

public class GraphQLCall {

    private String parentMethod;
    private String returnType;
    private String uri;
    private boolean isCollection;
    private String graphqlCallInClassName;
    private String msName;
    private String param;
    private String document;

    public GraphQLCall(String parentMethod,
                       String returnType, String uri, Boolean isCollection,
                       String graphqlCallInClassName, String msName, String param, String document) {

        this.parentMethod = parentMethod;
        this.returnType = returnType;
        this.uri = uri;
        this.isCollection = isCollection;
        this.graphqlCallInClassName = graphqlCallInClassName;
        this.msName = msName;
        this.param = param;
        this.document = document;
    }
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.msName).append(",").append(graphqlCallInClassName).append(",").append(parentMethod).append(",").append(uri)
        .append(",").append(returnType).append(",")
        .append(param).append(",").append(isCollection).append(",").append(document);
        return sb.toString();
    }
    // Getter methods
    public String getParam(){
        return this.param;
    }
    public String getMsName() {
        return this.msName;
    }
    public String getgraphqlCallInClassName() {
        return this.graphqlCallInClassName;
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

    public String getDocument() {
        return document;
    }

    public boolean isCollection() {
        return isCollection;
    }

    // Setter methods
    public void setMsName(String msName) {
        this.msName = msName;
    }

    public void setGraphqlCallInClassName(String className) {
        this.graphqlCallInClassName = className;
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

    public void setDocument(String document) {
        this.document = document;
    }

    public void setCollection(boolean isCollection) {
        this.isCollection = isCollection;
    }
}
