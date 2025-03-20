package com.oracle.svm.hosted.prophet.model;

public class WebsocketEndpoint {

    private String uri;
    private String msName;
    private String wsHandler;
    private String parentMethod;
    private String returnType;
    private boolean isCollection;
    private String connectionInClassName;
    private WebsocketParameter param;

    public WebsocketEndpoint(String parentMethod, String returnType, String uri, Boolean isCollection,
                             String connectionInClassName, String msName, WebsocketParameter param, String wsHandler) {

        this.parentMethod = parentMethod;
        this.returnType = returnType;
        this.uri = uri;
        this.isCollection = isCollection;
        this.connectionInClassName = connectionInClassName;
        this.msName = msName;
        this.param = param;
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
                .append(param != null ? param.getIsPath() : "").append(",")
                .append(param != null ? param.getIsBody() : "").append(",")
                .append(param != null ? param.getParamType() : "").append(",")
                .append(param != null ? param.getParamCount() : "").append(",")
                .append(isCollection);
        return sb.toString();
    }

    public String getMsName() {
        return this.msName;
    }

    public String getUri() {
        return uri;
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
    public void setMsName(String msName) {
        this.msName = msName;
    }

    public void setWsHandler(String wsHandler) {
        this.wsHandler = wsHandler;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }
}