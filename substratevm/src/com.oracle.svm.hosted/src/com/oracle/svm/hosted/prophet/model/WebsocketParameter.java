package com.oracle.svm.hosted.prophet.model;

public class RESTParameter {
    
    private Boolean isBody;
    private Boolean isPath;
    private String paramType;
    private int paramCount = 0;

    public RESTParameter(Boolean isBody, Boolean isPath){
        this.isBody = isBody;
        this.isPath = isPath;
    }
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("isBody " + isBody + ", isPath = " + isPath + ", paramType = " + paramType + ", paramCount = " + paramCount);
        return sb.toString(); 
    }
    public void setParamType(String type){
        this.paramType = type;
    }
    public String getParamType() {
        return this.paramType;
    }
    public Boolean getIsBody() {
        return this.isBody;
    }
    public void setIsBody(Boolean isBody) {
        this.isBody = isBody;
    }
    
    public Boolean getIsPath() {
        return this.isPath;
    }
    
    public void setIsPath(Boolean isPath) {
        this.isPath = isPath;
    }
    public int getParamCount() {
        return this.paramCount;
    }
    
    public void setParamCount(int c) {
        this.paramCount = c;
    }

}
