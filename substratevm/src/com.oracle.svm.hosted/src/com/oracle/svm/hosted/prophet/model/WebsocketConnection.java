/**
 * Authors:
 * - Vsevolod Pokhvalenko
 */
package com.oracle.svm.hosted.prophet.model;

public class WebsocketConnection {

    private String parentMethod;
    private String returnType;
    private String wsHandler;
    private String uri;
    private boolean isCollection;
    private String connectionInClassName;
    private String msName;
    private WebsocketParameter param;

    public WebsocketConnection(String parentMethod, String returnType, String uri, Boolean isCollection,
                               String connectionInClassName, String msName, WebsocketParameter param, String wsHandler) {

        this.msName = msName;
        this.connectionInClassName = connectionInClassName;
        this.parentMethod = parentMethod;
        this.uri = uri;
        this.returnType = returnType;
        this.param = param;
        this.isCollection = isCollection;
        this.wsHandler = wsHandler;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.msName != null ? this.msName : "").append(",")
                .append(connectionInClassName != null ? connectionInClassName : "").append(",")
                .append(parentMethod != null ? parentMethod : "").append(",")
                .append(uri != null ? uri : "").append(",")
                .append(returnType != null ? returnType : "").append(",")
                .append(param != null ? param : "").append(",")
                .append(isCollection);
        return sb.toString();
    }

    // Getter methods
    public WebsocketParameter getParam(){
        return this.param;
    }

    public String getMsName() {
        return this.msName;
    }

    public String getConnectionInClassName() {
        return this.connectionInClassName;
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
    public void setMsName(String msName) {
        this.msName = msName;
    }

    public void setConnectionInClassName(String className) {
        this.connectionInClassName = className;
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

    public String getWsHandler() {
        return wsHandler;
    }
}