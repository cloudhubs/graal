package com.oracle.svm.hosted.prophet.model;

import java.util.List;

public class GraphQLEndpoint {

    private String graphQLMethod;
    private String parentMethod;
    private List<String> arguments;
    private String returnType;
    private String path;
    private boolean isCollection;
    private String endpointInClassName;
    private String msName;

    public GraphQLEndpoint(String graphQLMethod, String parentMethod, List<String> args,
                           String returnType, String path, Boolean isCollection,
                           String endpointInClassName, String msName) {

        this.graphQLMethod = graphQLMethod;
        this.parentMethod = parentMethod;
        this.arguments = args;
        this.returnType = returnType;
        this.path = path;
        this.isCollection = isCollection;
        this.endpointInClassName = endpointInClassName;
        this.msName = msName;

    }
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.msName).append(",").append(endpointInClassName).append(",").append(parentMethod).append(",")
        .append(toStringModified(arguments)).append(",").append(path).append(",").append(graphQLMethod)
        .append(",").append(returnType).append(",").append(isCollection);
        return sb.toString();
    }
    private String toStringModified(List<String> args){
        StringBuilder sb = new StringBuilder();
        
        for (int i = 0; i < args.size(); i++){
            String str = args.get(i);
            sb.append(str.replaceAll(" ", "_"));
            if (i < args.size() - 1){
                sb.append("&");
            }
        }

        return sb.toString();
    }
    // Getter methods
    public String getGraphQLMethod() {
        return graphQLMethod;
    }
    public String getMsName() {
        return this.msName;
    }
    public String getEndpointInClassName() {
        return this.endpointInClassName;
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
    public void setGraphQLMethod(String httpMethod) {
        this.graphQLMethod = httpMethod;
    }
    public void setMsName(String msName) {
        this.msName = msName;
    }
    public void setEndpointInClassName(String className) {
        this.endpointInClassName = className;
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
