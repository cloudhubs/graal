package com.oracle.svm.hosted.prophet.model;

public class WebsocketMessageType {

    private String wsDataType;
    private String msName;
    private String wsHandler;

    public WebsocketMessageType(String wsDataType, String msName, String wsHandler) {

        this.wsDataType = wsDataType;
        this.msName = msName;
        this.wsHandler = wsHandler;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.msName != null ? this.msName : "").append(",")
                .append(wsDataType != null ? wsDataType : "").append(",")
                .append(wsHandler != null ? wsHandler : "").append(",");
        return sb.toString();
    }


    public String getMsName() {
        return this.msName;
    }

    public String getWsDataType() {
        return wsDataType;
    }

    public String getWsHandler() {
        return wsHandler;
    }

    // Setter methods
    public void setMsName(String msName) {
        this.msName = msName;
    }

    public void setWsHandler(String wsHandler) {
        this.wsHandler = wsHandler;
    }

    public void setWsDataType(String wsDataType) {
        this.wsDataType = wsDataType;
    }

}