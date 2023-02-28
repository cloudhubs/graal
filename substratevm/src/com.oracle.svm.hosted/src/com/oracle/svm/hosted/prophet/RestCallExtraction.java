package com.oracle.svm.hosted.prophet;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.reachability.ReachabilityAnalysisMethod;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.options.Option;
import com.oracle.svm.hosted.analysis.Inflation;


public class RestCallExtraction {
    /*
     * \
     * EXAMPLE FORMAT FOR REST CALLS
     * "restCalls": [
     * {
     * "msRoot": "C:\\seer-lab\\cil-tms\\tms-cms",
     * "source":
     * "C:\\seer-lab\\cil-tms\\tms-cms\\src\\main\\java\\edu\\baylor\\ecs\\cms\\service\\EmsService.java",
     * "httpMethod": "POST",
     * "parentMethod": "edu.baylor.ecs.cms.service.EmsService.createExam",
     * "returnType": "edu.baylor.ecs.cms.dto.ExamDto",
     * "collection": false
     * },
     */

     /*
        NOTE: 
        'msRoot' can be obtained in Utils or RAD
        'source' can be obtained in RAD repo in the RadSourceService file in generateRestEntityContext method where getSourceFiles is
        
        parentMethod and httpMethod are extracted, will try to extract return Type
        httpMethod does need exchange built out
        */
    private final static String REST_TEMPLATE_PACKAGE = "org.springframework.web.client.RestTemplate.";
    public static void extractClassRestCalls(Class<?> clazz, AnalysisMetaAccess metaAccess, Inflation bb) {
        AnalysisType analysisType = metaAccess.lookupJavaType(clazz);
        try {
            for (AnalysisMethod method : analysisType.getDeclaredMethods()) {
                try {
                    StructuredGraph decodedGraph = ReachabilityAnalysisMethod.getDecodedGraph(bb, method);
                    for (Node node : decodedGraph.getNodes()) {
                        if (node instanceof Invoke) {
                            Invoke invoke = (Invoke) node;
                            AnalysisMethod targetMethod = ((AnalysisMethod) invoke.getTargetMethod());
                            if (targetMethod.getQualifiedName().startsWith(REST_TEMPLATE_PACKAGE)) {
                                System.out.println("===========================================");
                                System.out.println("Method qualified name: " + method.getQualifiedName());
                                System.out.println("Target method qualified name: " + targetMethod.getQualifiedName());
                                parseHttpMethodType(targetMethod.getQualifiedName());
                                parseParentMethod(method.getQualifiedName());
                                System.out.println("canonincal name of class = " + clazz.getCanonicalName()); 
                                CallTargetNode callTargetNode = invoke.callTarget();
                                NodeInputList<ValueNode> arguments = callTargetNode.arguments();
                                ValueNode zero = arguments.get(0);
                                ValueNode one = arguments.get(1);
                                if (one instanceof InvokeWithExceptionNode) {
                                    // todo figure out when this does not work
                                    System.out.println("\tFirst arg is invoke:");
                                    CallTargetNode callTarget = ((InvokeWithExceptionNode) one).callTarget();
                                    System.out.println("\t\tcallTarget.targetMethod() = " + callTarget.targetMethod());
                                    System.out.println("\t\targs:");
                                    for (ValueNode argument : callTarget.arguments()) {
                                        System.out.println("\t\targument = " + argument);
                                    }
                                }

                                System.out.println("arg 0 = " + zero + ", arg 1 = " + one);
                                System.out.println("===========================================");
                            }
                        }
                    }
                } catch (Exception | LinkageError ex) {
                    ex.printStackTrace();
                }
            }
        } catch (Exception | LinkageError ex) {
            ex.printStackTrace();
        }
    }


    /**
     * given a target method's qualified name, return http method type
     * @param input targe method's qualified name
     * @return http method type extracted
     */
    private static String parseHttpMethodType(String input){
        Map<String, String> httpMethodTypes = new HashMap<>();
        httpMethodTypes.put("getFor", "GET");
        httpMethodTypes.put("postFor", "POST");
        httpMethodTypes.put("delete", "DELETE");
        httpMethodTypes.put("exchange", "EXCHANGE");

        String httpMethodType = null;
        String inputSubStr = input.substring(REST_TEMPLATE_PACKAGE.length());
        inputSubStr = inputSubStr.substring(0, inputSubStr.indexOf("("));
        
        //iterate over map of http method types and verify http method type
        for (Map.Entry<String, String> entry : httpMethodTypes.entrySet()) {
            if (inputSubStr.startsWith(entry.getKey())){
                httpMethodType = entry.getValue();
                break;
            }
        }
        System.out.println("HTTP METHOD TYPE = " + httpMethodType);
        return httpMethodType;
    }
    /**
     * extract the method the rest call is being in
     * @param input the method's qualified name
     * @return the method the call is being made in
     */
    private static String parseParentMethod(String input){
        String parentMethod = null;
        
        parentMethod = input.substring(0, input.indexOf("("));
        System.out.println("PARENT METHOD = " + parentMethod);
        return parentMethod;
    }


}
