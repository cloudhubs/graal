package com.oracle.svm.hosted.prophet.model;
/*\
 *  EXAMPLE FORMAT FOR REST CALLS
 *  "restCalls": [
        {
          "msRoot": "C:\\seer-lab\\cil-tms\\tms-cms",
          "source": "C:\\seer-lab\\cil-tms\\tms-cms\\src\\main\\java\\edu\\baylor\\ecs\\cms\\service\\EmsService.java",
          "httpMethod": "POST",
          "parentMethod": "edu.baylor.ecs.cms.service.EmsService.createExam",
          "returnType": "edu.baylor.ecs.cms.dto.ExamDto",
          "collection": false
        },
 */
public class RestCall {
    private String msRoot;
    private String source;
    private String httpMethod;
    private String parentMethod;
    private String returnType;
    private String url;
    private boolean isCollection;

    public RestCall(String httpMethod, String parentMethod, String returnType) {
        this.httpMethod = httpMethod;
        this.parentMethod = parentMethod;
        this.returnType = returnType;
    }
}
