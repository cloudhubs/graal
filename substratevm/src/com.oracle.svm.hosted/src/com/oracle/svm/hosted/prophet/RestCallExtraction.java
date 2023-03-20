package com.oracle.svm.hosted.prophet;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.reachability.ReachabilityAnalysisMethod;
import com.oracle.svm.core.meta.DirectSubstrateObjectConstant;
import com.oracle.svm.hosted.analysis.Inflation;

import jdk.vm.ci.meta.ResolvedJavaMethod.Parameter;
import jdk.vm.ci.meta.PrimitiveConstant;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.virtual.AllocatedObjectNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.nodeinfo.Verbosity;
import com.oracle.svm.hosted.prophet.model.Entity;
import java.util.Optional;

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
    */
    private final static String REST_TEMPLATE_PACKAGE = "org.springframework.web.client.RestTemplate.";
    private final static String HTTP_ENTITY_PACKAGE = "org.springframework.http.HttpEntity";

    public static void extractClassRestCalls(Class<?> clazz, AnalysisMetaAccess metaAccess, Inflation bb, Map<String, Object> propMap) {
        AnalysisType analysisType = metaAccess.lookupJavaType(clazz);
        try {
            for (AnalysisMethod method : analysisType.getDeclaredMethods()) {
                try {
                    // if (!method.getQualifiedName().contains("getExams")){
                    //     continue;
                    // }
                    // if (!method.getQualifiedName().contains("updateUser")){
                    //     continue;
                    // }
                    StructuredGraph decodedGraph = ReachabilityAnalysisMethod.getDecodedGraph(bb, method);
                    for (Node node : decodedGraph.getNodes()) {
                        if (node instanceof Invoke) {
                            Invoke invoke = (Invoke) node;
                            AnalysisMethod targetMethod = ((AnalysisMethod) invoke.getTargetMethod());
                            if (targetMethod.getQualifiedName().startsWith(REST_TEMPLATE_PACKAGE)) {
                                System.out.println("===========================================");
                                System.out.println("Method qualified name: " + method.getQualifiedName());
                                System.out.println("Target method qualified name: " + targetMethod.getQualifiedName());
                                Parameter[] parameters = targetMethod.getParameters();

                                // System.out.println("targetMethod.getWrapped().getName() = " + targetMethod.getWrapped().getName() + ", just the getWrapped() = " + targetMethod.getWrapped());
                                // System.out.println("targetMethod.getSignature() = " + targetMethod.getSignature() + ", getSignature().getReturnType() = " + targetMethod.getSignature().getReturnType(targetMethod.getType()));
                                String HTTP_METHOD_TYPE = parseHttpMethodType(targetMethod.getQualifiedName());

                                String PARENT_METHOD = cleanParentMethod(method.getQualifiedName());                   
                                CallTargetNode callTargetNode = invoke.callTarget();
                                // System.out.println("callTargetNode = " + callTargetNode);
                                NodeInputList<ValueNode> arguments = callTargetNode.arguments();
                                // System.out.println("arguments = " + arguments);
                                String URI = "";
                                String RETURN_TYPE = null;
                                Boolean callIsCollection = false;

                                for (ValueNode v : arguments){
                                    if (v instanceof Invoke){
                                        // System.out.println("\t\tand IS an instance of Invoke");
                                        URI += extractURI(((Invoke)v).callTarget(), propMap);
                                    }
                                    //NOTE: need to find definitive way of knowing if node holds return value
                                    //return type seems to be in substratemethod prior to a invoke of restTemplate.whatevercall 
                                    else if (v instanceof ConstantNode && !v.isNullConstant() && !v.isIllegalConstant()){
                                        ConstantNode cn = (ConstantNode)v;
                                        // System.out.println("CONSTANT NODE = " + cn);
                                        //EXTRACT RETURN TYPE
                                        if (cn.toString().contains("com.oracle.svm.core.hub.DynamicHub")){
                                            Boolean returnTypeLikely = false;
                                            for (Node cnUsage : cn.usages()){
                                                for (Node subUsage : cnUsage.usages()){
                                                    if (subUsage instanceof Invoke && subUsage.toString().contains("RestTemplate")){
                                                        returnTypeLikely = true;
                                                        break;
                                                    }
                                                }
                                                if (returnTypeLikely){
                                                    break;
                                                }
                                            }
                                            if (returnTypeLikely){
                                                DirectSubstrateObjectConstant dsoc = (DirectSubstrateObjectConstant)cn.getValue();
                                                RETURN_TYPE = dsoc.getObject().toString();
                                                callIsCollection = isCollection(RETURN_TYPE);
                                                RETURN_TYPE = cleanReturnType(RETURN_TYPE);
                                            }
                                        }
                                        //MIGHT be URI or portion of URI
                                        else{

                                            DirectSubstrateObjectConstant dsoc = (DirectSubstrateObjectConstant)cn.getValue();
                                            URI += dsoc.getObject().toString();  
                                        } 
     
                                    }
                                } 
                                //RestTemplate is an EXCHANGE, get specific HTTP type
                                if (HTTP_METHOD_TYPE != null && HTTP_METHOD_TYPE.equals("EXCHANGE")){
                                    HTTP_METHOD_TYPE = extractHttpType(callTargetNode);
                                }
                                //TO-DO: In future try to get what type of HTTP Entity.
                                if (RETURN_TYPE == null || RETURN_TYPE.contains("edu.fudan.common.util.Response")){
                                    RETURN_TYPE = RestCallExtraction.HTTP_ENTITY_PACKAGE;
                                }
                                System.out.println("PARENT METHOD = " + PARENT_METHOD);
                                System.out.println("RETURN TYPE = " + RETURN_TYPE);
                                System.out.println("HTTP_METHOD_TYPE = " + HTTP_METHOD_TYPE);
                                System.out.println("URI = " + URI);
                                System.out.println("IS COLLECTION = " + callIsCollection);
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

    private static String cleanReturnType(String returnType){
        String parsedType = null;
        if (returnType == null || returnType.equals("null")){
            return parsedType;
        }
        //remove 'class [L' example: 'class [Ljava.lang.Object]' -> 'java.lang.Object'
        if (isCollection(returnType)){
            parsedType = returnType.substring(8);
        }
        //remove 'class ' example: 'class [Ljava.lang.Object]' -> '[Ljava.lang.Object'
        else{
            parsedType = returnType.substring(6);         
        }
        return parsedType;
    } 

    private static boolean isCollection(String returnType){
        if (returnType == null || returnType.equals("null")){
            return false;
        }
        //graal api indicates collections in return type with "class [L" before the type name
        return returnType.startsWith("class [L");
    }
    private static String extractHttpType(CallTargetNode node){
        String httpType = "";
        // System.out.println("------------");
        // System.out.println("NODE CALL TARGET: " + node);
        // System.out.println("NODE CALL TARGET ARGS: " + node.arguments());
        for (ValueNode arg : node.arguments()){
            // System.out.println("arg = " + arg);
            if (arg instanceof Invoke){
                // System.out.println(arg + " is Invoke");
                httpType = extractHttpType(((Invoke)arg).callTarget());
            }
            else if (arg instanceof PiNode){
                // System.out.println(arg + " is a PiNode");
                // System.out.println(((PiNode)arg).inputs());
                for (Node inputNode : ((PiNode)arg).inputs()){
                    if (inputNode instanceof Invoke){
                        // System.out.println(inputNode + " is Invoke");
                        httpType = extractHttpType(((Invoke)inputNode).callTarget());
                    }
                }
            }
            else if (arg instanceof LoadFieldNode){
                // System.out.println(tabs + "arg is a LOAD_FIELD_NODE, arg = " + arg);
                LoadFieldNode loadfieldNode = (LoadFieldNode) arg;
                // System.out.println("loadfieldnode = " + loadfieldNode.getValue());
                AnalysisField field = (AnalysisField) loadfieldNode.field();
                if (field.getDeclaringClass().getName().contains("HttpMethod")){
                    httpType = field.getName();
                }
            }
        }
        return httpType;
    }
    private static String extractURI(CallTargetNode node, Map<String, Object> propMap){
        // System.out.println("NODE CALL TARGET: " + node);
        // System.out.println("NODE CALL TARGET ARGS: " + node.arguments());
        String uriPortion = "";
        
        /*
         * Loop over the arguments in the call target node
         * if the node in the argument is an Invoke, call its target
         * else if node is a loadfieldnode, go over annotations and get 'value' annotation
         * get value based off prop map
         */
        for (ValueNode arg : node.arguments()){
            NodeIterable<Node> inputsList = arg.inputs(); 
            if (arg instanceof LoadFieldNode){
                // System.out.println("arg is a LOAD_FIELD_NODE, arg = " + arg);
                LoadFieldNode loadfieldNode = (LoadFieldNode) arg;
                AnalysisField field = (AnalysisField) loadfieldNode.field();
                
                for (java.lang.annotation.Annotation annotation : field.getAnnotations()) {
                    if (annotation.annotationType().getName().contains("Value")) {
                        // System.out.println("Load field with value annotation");
                        // System.out.println("methods = " + annotation.annotationType().getMethods());
                        try{
                            Method valueMethod = annotation.annotationType().getMethod("value");
                            valueMethod.setAccessible(true);
                            String res = "";
                            if (propMap != null){
                                res = tryResolve(((String)valueMethod.invoke(annotation)), propMap);
                            }
                            uriPortion = uriPortion + res;
                        }catch(Exception ex){
                            System.err.println("ERROR = " + ex);
                        }
                    }
                }
                
            }
            else if (arg instanceof PiNode){
                // System.out.println(arg + " is a PiNode");
                // System.out.println("pi node inputs: " + ((PiNode)arg).inputs());
                for (Node inputNode : ((PiNode)arg).inputs()){
                    if (inputNode instanceof Invoke){
                        // System.out.println(inputNode + " is Invoke");
                        uriPortion = uriPortion + extractURI(((Invoke)inputNode).callTarget(), propMap);
                    }
                }
            }
            else if (arg instanceof ConstantNode){
                ConstantNode cn = (ConstantNode)arg;
                //PrimitiveConstants can not be converted to DirectSubstrateObjectConstant
                if (!(cn.getValue() instanceof PrimitiveConstant)){
                    DirectSubstrateObjectConstant dsoc = (DirectSubstrateObjectConstant)cn.getValue();
                    uriPortion = uriPortion + dsoc.getObject().toString();
                }

            }
            else if (arg instanceof Invoke){
                // System.out.println("arg = " + arg + " && is an instance of invoke");
                uriPortion = uriPortion + extractURI(((Invoke)arg).callTarget(), propMap);
            }
            else{
                for (Node n : inputsList){
                    if (n instanceof Invoke){;
                        uriPortion = uriPortion + extractURI(((Invoke)n).callTarget(), propMap);
                    }
                }
            }

        }  
        return uriPortion;    
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
        return httpMethodType;
    }
    /**
     * extract the method the rest call is being in
     * @param input the method's qualified name
     * @return the method the call is being made in
     */
    private static String cleanParentMethod(String input){
        String parentMethod = null;
        
        parentMethod = input.substring(0, input.indexOf("("));
        return parentMethod;
    }
    //TO-DO: find a safer way to cast Map<String, Object> value
    @SuppressWarnings("unchecked")
    private static String tryResolve(String expr, Map<String, Object> propMap) {
        
        String mergedKey = expr.substring(2, expr.length() - 1);
        String[] path = mergedKey.split("\\.");
        Map<String, Object> curr = propMap;
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
                try{
                    curr = ((Map<String, Object>) value);
                }
                catch(ClassCastException ex){
                    ex.printStackTrace();
                }
            }
        }
        return null;
    }

}
