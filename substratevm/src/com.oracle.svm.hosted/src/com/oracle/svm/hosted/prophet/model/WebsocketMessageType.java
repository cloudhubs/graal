package com.oracle.svm.hosted.prophet.model;

public class WebsocketMessageType {

    private String wsDataType;
    private String msName;
    private String wsHandler;
    private String parentMethod;
    private String returnType;
    private boolean isCollection;
    private String connectionInClassName;
    private WebsocketParameter param;

    public WebsocketMessageType(String wsDataType, String msName, String wsHandler, String parentMethod,
                                 boolean isCollection, String connectionInClassName,
                                WebsocketParameter param) {
        this.wsDataType = wsDataType;
        this.msName = msName;
        this.wsHandler = wsHandler;
        this.parentMethod = parentMethod;
        this.isCollection = isCollection;
        this.connectionInClassName = connectionInClassName;
        this.param = param;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.msName != null ? this.msName : "").append(",")
                .append(parentMethod != null ? parentMethod : "").append(",")
                .append(wsDataType != null ? wsDataType : "").append(",")
                .append(isCollection).append(",")
                .append(connectionInClassName != null ? connectionInClassName : "").append(",")
                .append(param != null ? param.toString() : "");
        return sb.toString();
    }

    public String getWsDataType() {
        return wsDataType;
    }

    public String getMsName() {
        return msName;
    }

    public String getWsHandler() {
        return wsHandler;
    }

    public String getParentMethod() {
        return parentMethod;
    }

    public String getReturnType() {
        return returnType;
    }

    public boolean isCollection() {
        return isCollection;
    }

    public String getConnectionInClassName() {
        return connectionInClassName;
    }

    public WebsocketParameter getParam() {
        return param;
    }

    // Setter methods
    public void setWsDataType(String wsDataType) {
        this.wsDataType = wsDataType;
    }

    public void setMsName(String msName) {
        this.msName = msName;
    }

    public void setWsHandler(String wsHandler) {
        this.wsHandler = wsHandler;
    }

    public void setParentMethod(String parentMethod) {
        this.parentMethod = parentMethod;
    }

    public void setReturnType(String returnType) {
        this.returnType = returnType;
    }

    public void setCollection(boolean isCollection) {
        this.isCollection = isCollection;
    }

    public void setConnectionInClassName(String connectionInClassName) {
        this.connectionInClassName = connectionInClassName;
    }

    public void setParam(WebsocketParameter param) {
        this.param = param;
    }
}