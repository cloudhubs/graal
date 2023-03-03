package com.oracle.svm.hosted.prophet;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.reachability.ReachabilityAnalysisMethod;
import jdk.vm.ci.meta.ResolvedJavaMethod.Parameter;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.virtual.AllocatedObjectNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;

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
    public static void extractClassRestCalls(Class<?> clazz, AnalysisMetaAccess metaAccess, Inflation bb, Map<String, Object> propMap) {
        AnalysisType analysisType = metaAccess.lookupJavaType(clazz);
        try {
            for (AnalysisMethod method : analysisType.getDeclaredMethods()) {
                try {
                    
                    StructuredGraph decodedGraph = ReachabilityAnalysisMethod.getDecodedGraph(bb, method);
                    for (Node node : decodedGraph.getNodes()) {
                        if (node instanceof Invoke) {
                            Invoke invoke = (Invoke) node;
                            AnalysisMethod targetMethod = ((AnalysisMethod) invoke.getTargetMethod());
                            if (targetMethod.getQualifiedName().startsWith(REST_TEMPLATE_PACKAGE) && method.getQualifiedName().equals("edu.baylor.ecs.cms.service.EmsService.getExams()")) {
                                System.out.println("===========================================");
                                System.out.println("Method qualified name: " + method.getQualifiedName());
                                System.out.println("Target method qualified name: " + targetMethod.getQualifiedName());
                                // Parameter[] parameters = targetMethod.getParameters();
                                // for(jdk.vm.ci.meta.ResolvedJavaMethod.Parameter a : parameters){
                                //     System.out.println("\tparameter = " + a + ", getName = " + a.getName() + ", getparameterizedType().getTypeName() = " + a.getParameterizedType().getTypeName());
                                // }
                                // System.out.println("targetMethod.getWrapped().getName() = " + targetMethod.getWrapped().getName() + ", just the getWrapped() = " + targetMethod.getWrapped());
                                // System.out.println("targetMethod.getSignature() = " + targetMethod.getSignature() + ", getSignature().getReturnType() = " + targetMethod.getSignature().getReturnType(targetMethod.getType()));
                                parseHttpMethodType(targetMethod.getQualifiedName());
                                parseParentMethod(method.getQualifiedName());                     
                                System.out.println("----------------");


                                CallTargetNode callTargetNode = invoke.callTarget();
                                System.out.println("callTargetNode = " + callTargetNode);
                                NodeInputList<ValueNode> arguments = callTargetNode.arguments();
                                System.out.println("arguments = " + arguments);
                                ValueNode stringValNode = null;
                                ValueNode allocObjValNode = null;

                                for (ValueNode v : arguments){
                                    System.out.println("\targument = " + v);
                                    if (v instanceof Invoke){
                                        System.out.println("\t\tand IS an instance of Invoke");
                                        System.out.println("\t\t\tcall target = " + ((Invoke)v).callTarget().arguments());
                                        stringValNode = v;
                                    }
                                    else if (v instanceof AllocatedObjectNode){
                                        System.out.println("\t\tand IS an instance of AllocatedObjectNode");
                                        System.out.println("\t\t\tallocated object usages = ");
                                        for (Node usage : ((AllocatedObjectNode)v).usages()){
                                            System.out.println("\t\t\t\tusage = " + usage);
                                        }

                                        allocObjValNode = v;
                                    }
                                    else{
                                        System.out.println("\t\tand NOT an instance of invoke");
                                    }
                                } 

                                // if (one instanceof InvokeWithExceptionNode) {
                                if (stringValNode != null && allocObjValNode != null) {
                                    // CallTargetNode callTarget = ((InvokeWithExceptionNode) one).callTarget();
                                    CallTargetNode stringValCallTarget = ((Invoke) stringValNode).callTarget();
                                    System.out.println("stringValNode = " + stringValNode);
                                    System.out.println("\tstringValCallTargetMethod args:");
                                    ValueNode stringValCallTargetArg = null;
                                    for (ValueNode arg : stringValCallTarget.arguments()) {
                                        System.out.println("\t\targ = " + arg);

                                    }
                                    // todo assert it is really a toString invocation
                                    /*AllocatedObjectNode toStringReceiver = (AllocatedObjectNode) callTarget.arguments().get(0);
                                    System.out.println("ToString receiver: " + toStringReceiver);
                                    StringBuilder stringBuilder = new StringBuilder();
                                    for (Node usage : toStringReceiver.usages()) {
                                        System.out.println("\t usage : " + usage);
                                        if (usage instanceof CallTargetNode) {
                                            CallTargetNode usageAsCallTarget = (CallTargetNode) usage;
                                            AnalysisMethod m = ((AnalysisMethod) usageAsCallTarget.targetMethod());
                                            if (m.getQualifiedName().startsWith("java.lang.AbstractStringBuilder.append")) {
                                                System.out.println("\t\t is a calltarget to " + m);
                                                ValueNode fstArg = usageAsCallTarget.arguments().get(1);
                                                System.out.println("\t\t" + fstArg);
                                                if (fstArg instanceof LoadFieldNode) {
                                                    System.out.println("\t\t\tload field " + fstArg);
                                                    LoadFieldNode loadfieldNode = (LoadFieldNode) fstArg;
                                                    AnalysisField field = (AnalysisField) loadfieldNode.field();
                                                    for (Annotation annotation : field.getAnnotations()) {
                                                        if (annotation.annotationType().getName().contains("Value")) {
                                                            System.out.println("\t\t\tLoad field with value annotation");
                                                            Method valueMethod = annotation.annotationType().getMethod("value");
                                                            String propTemplate = ((String) valueMethod.invoke(annotation));
                                                            System.out.println("\t\t\textracted value: " + propTemplate);
                                                            String res = tryResolve(propTemplate, propMap);
                                                            System.out.println("\t\t\t\t resolved: " + res);
                                                            if (res != null) {
                                                                stringBuilder.append(res);
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    System.out.println("Concatenated url: " + stringBuilder.toString());
                                    */
                                }
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
    private static String tryResolve(String expr, Map<String, Object> propMap) {
        String mergedKey = expr.substring(2, expr.length() - 1);
        String[] path = mergedKey.split("\\.");
        var curr = propMap;
        for (int i = 0; i < path.length; i++) {
            String key = path[i];
            Object value = curr.get(key);
            if (value == null) {
                return null;
            }
            if (value instanceof String && i == path.length - 1) {
                return ((String) value);
            }
            if (value instanceof Map) {
                curr = ((Map<String, Object>) value);
            }
        }
        return null;
    }

}
