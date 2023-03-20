package com.oracle.svm.hosted.prophet;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.reachability.ReachabilityAnalysisMethod;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.analysis.Inflation;
import com.oracle.svm.hosted.prophet.model.Endpoint;
import com.oracle.svm.hosted.prophet.model.Entity;
import com.oracle.svm.hosted.prophet.model.Field;
import com.oracle.svm.hosted.prophet.model.Module;
import com.oracle.svm.hosted.prophet.model.Name;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.options.Option;
import jdk.vm.ci.meta.ResolvedJavaMethod.Parameter;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import com.oracle.graal.pointsto.infrastructure.WrappedSignature;
import org.graalvm.polyglot.*;
import com.oracle.svm.core.meta.DirectSubstrateObjectConstant;
import org.graalvm.compiler.nodes.ConstantNode;
import com.oracle.truffle.api.nodes.RootNode;
import org.graalvm.compiler.nodes.CallTargetNode;
import java.lang.reflect.Method;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.transform.Result;

import java.util.Collection;

public class EndpointExtraction {

    private final static String REST_TEMPLATE_PACKAGE = "org.springframework.web.client.RestTemplate.";
    private final static String PUT_MAPPING = "org.springframework.web.bind.annotation.PutMapping";
    private final static String GET_MAPPING = "org.springframework.web.bind.annotation.GetMapping";
    private final static String POST_MAPPING = "org.springframework.web.bind.annotation.PostMapping";
    private final static String DELETE_MAPPING = "org.springframework.web.bind.annotation.DeleteMapping";

    //annotations for controller to get endpoints
    private static final Set<String> controllerAnnotationNames = new HashSet<>(Arrays.asList("GetMapping", "PutMapping", "DeleteMapping", "PostMapping"));
    public static List<Endpoint> extractEndpoints(Class<?> clazz, AnalysisMetaAccess metaAccess, Inflation bb) {
        AnalysisType analysisType = metaAccess.lookupJavaType(clazz);
        List<Endpoint> endpoints = new List<Endpoint>();
        try {
            for (AnalysisMethod method : analysisType.getDeclaredMethods()) {
                try {      
                    // What I will need to extract: String httpMethod, String parentMethod, String arguments, String returnType
                    Annotation[] annotations = method.getAnnotations();
                    for (Annotation annotation : annotations) {
                        
                        if (controllerAnnotationNames.contains(annotation.annotationType().getSimpleName())) {
                            
                             //Code to get the parentMethod attribute:
                            //following the rad-source format for the parentMethod JSON need to parse before the first parenthesis
                            String parentMethod = method.getQualifiedName().substring(0,method.getQualifiedName().indexOf("("));
                            
                            String httpMethod = null;
                            if (annotation.annotationType().getName().startsWith(PUT_MAPPING)) {
                                httpMethod = "PUT";
                            } else if (annotation.annotationType().getName().startsWith(GET_MAPPING)) {
                                httpMethod = "GET";
                            } else if (annotation.annotationType().getName().startsWith(POST_MAPPING)) {
                                httpMethod = "POST";
                            } else if (annotation.annotationType().getName().startsWith(DELETE_MAPPING)) {
                                httpMethod = "DELETE";
                            }

                            //try to get the path (might be null)
                             //Example of a path to parse: @org.springframework.web.bind.annotation.DeleteMapping(path={}, headers={}, name="", produces={}, 
                             //params={}, value={"/{userId}"}, consumes={})
                            String path = null;
                            boolean hasPath = false;
                            try{
                                //System.out.println("Annotation object: " + annotation.toString());
                                // path is optional, thus attempting to get it and return null if so.
                                path = ((String[]) annotation.annotationType().getMethod("value").invoke(annotation))[0];
                                hasPath = true;
                            }catch(ArrayIndexOutOfBoundsException ex){
                                hasPath = false;
                             }

                             //Have to also consider the "path" JSON attribute of the annotation, Example:
                             //@org.springframework.web.bind.annotation.GetMapping(path={"/welcome"}, headers={}, name="", produces={}, params={}, 
                             //value={}, consumes={})
                             //This is with the assumption that a value == path when within an annotation!
                             if(!hasPath){
                                try{
                                    path = ((String[]) annotation.annotationType().getMethod("path").invoke(annotation))[0];
                                }catch(ArrayIndexOutOfBoundsException ex){

                                }
                            }

                            ArrayList<String> parameterAnnotationsList = extractArguments(method);
                            // System.out.println("HTTP Method: " + httpMethod);
                            // System.out.println("Path: " + path);
                            // System.out.println("parentMethod: " + parentMethod);
                            // for(String value : parameterAnnotationsList){
                            //     System.out.println("argument: " + value);
                            // }

                            String returnTypeResult = extractReturnType(method);
                            boolean returnTypeCollection = false;
                            if(returnTypeResult.startsWith("[L") && isCollection(returnTypeResult)){
                                returnTypeCollection = true;
                                returnTypeResult = returnTypeResult.substring(2);
                            }else{
                                returnTypeCollection = isCollection(returnTypeResult);
                            }
                            //System.out.println("Return type: " + returnTypeResult);
                            //System.out.println("Is Collection: " + returnTypeCollection);

                            //System.out.println("============");
                            //Special case for request mapping 
                        }else if (annotation.annotationType().getSimpleName().equals("RequestMapping")){
                            
                            //Code to get the parentMethod attribute:
                            String parentMethod = method.getQualifiedName().substring(0,method.getQualifiedName().indexOf("("));


                            String[] pathArr = (String[]) annotation.annotationType().getMethod("path").invoke(annotation);
                            String path = pathArr.length > 0 ? pathArr[0] : null;

                            //cant use a string[] because of ClassCastException 
                            Object[] methods = (Object[]) annotation.annotationType().getMethod("method").invoke(annotation);
                            String httpMethod = null;
                            if (methods.length > 0 && methods != null) {
                                httpMethod = methods[0].toString();
                            }

                            ArrayList<String> parameterAnnotationsList = extractArguments(method);
                            //System.out.println("HTTP Method: " + httpMethod);
                            //System.out.println("Path: " + path);
                            //System.out.println("parentMethod: " + parentMethod);
                            //for(String value : parameterAnnotationsList){
                                //System.out.println("argument: " + value);
                            //}
                    
                            String returnTypeResult = extractReturnType(method);
                            boolean returnTypeCollection = false;
                            if(returnTypeResult.startsWith("[L") && isCollection(returnTypeResult)){
                                returnTypeCollection = true;
                                returnTypeResult = returnTypeResult.substring(2);
                            }else{
                                returnTypeCollection = isCollection(returnTypeResult);
                            }
                            //System.out.println("Return type: " + returnTypeResult);
                            //System.out.println("Is Collection: " + returnTypeCollection);

                            //System.out.println("============");
                        }
                        
                        endpoints.add(new Endpoint(null, null, null, null, null, true, clazz.getCanonicalName()));
                    }

                    // StructuredGraph decodedGraph = ReachabilityAnalysisMethod.getDecodedGraph(bb, method);
                    // for (Node node : decodedGraph.getNodes()) {
                    //     if (node instanceof Invoke) {
                    //         Invoke invoke = (Invoke) node;
                    //         AnalysisMethod targetMethod = ((AnalysisMethod) invoke.getTargetMethod());
                    //         if (targetMethod.getQualifiedName().startsWith(REST_TEMPLATE_PACKAGE)) {
                    //             System.out.println("Method Qualified Name = " + method.getQualifiedName());
                    //             System.out.println("Target Method Qualified Name = " + targetMethod.getQualifiedName());
                    //             CallTargetNode callTargetNode = invoke.callTarget();
                    //             NodeInputList<ValueNode> arguments = callTargetNode.arguments();
                    //             ValueNode zero = arguments.get(0);
                    //             ValueNode one = arguments.get(1);
                    //             if (one instanceof InvokeWithExceptionNode) {
                    //                 // todo figure out when this does not work
                    //                 System.out.println("\tFirst arg is invoke:");
                    //                 CallTargetNode callTarget = ((InvokeWithExceptionNode) one).callTarget();
                    //                 System.out.println(callTarget.targetMethod());
                    //                 System.out.println("\targs:");
                    //                 for (ValueNode argument : callTarget.arguments()) {
                    //                     System.out.println("\targument = " + argument);
                    //                 }
                    //             }
                    //             System.out.println(zero + " " + one);
                    //             System.out.println("===");
                    //         }
                    //     }
                    // }
                } catch (Exception | LinkageError ex) {
                    ex.printStackTrace();
                }
            }
        } catch (Exception | LinkageError ex) {
            ex.printStackTrace();
        }
    }

    private static boolean isCollection(String returnType){
        if (returnType == null || returnType.equals("null")){
            return false;
        }
        //graal api indicates collections in return type with "class [L" OR
        return returnType.startsWith("[L") || returnType.matches(".*[<].*[>]");
    }

    /**
     * Method extracts and cleans the return type value of a controller method (based on a collection or object/primitive data type)
     * @param method an AnalysisMethod
     * @return the method's return type as a string value
     */
    public static String extractReturnType(AnalysisMethod method){
        Method javaMethod = (Method) method.getJavaMethod();
        Type returnType = javaMethod.getGenericReturnType();

        //checking if return type is a collection
        if(returnType instanceof ParameterizedType) {
            ParameterizedType type = (ParameterizedType) returnType;
            Type[] typeArgs = type.getActualTypeArguments();
            Class<?> collectionType = (Class<?>) type.getRawType();
            Class<?> elementType = (Class<?>) typeArgs[0];


            //objective: convert interface java.util.List -> java.util.List
            //double checking that it is "interface" portion removed (and not something else)
            if(collectionType.toString().substring(0,9).equalsIgnoreCase("interface")){
                String result = collectionType.toString().substring(10);
                result = result + "<" + elementType.toString().substring(6) + ">";
                return result;
                //System.out.println("TESTING PARSING: " + result);
            }else {
                //TODO: need to handle Set, or other collection times (where this will break)
                return collectionType.toString() + "<" + elementType.toString() + ">";
            }
            // }else if(collectionType.toString().substring(0,3).equalsIgnoreCase("set")){
            //     //handle the case of a set:

            //     String result = collectionType.toString().substring(4);
            //     result = result + "<" + elementType.toString().substring(6) + ">";
            //     return result;
            // }

            //System.out.println("Collection: " + collectionType);
            //System.out.println("Element: " + elementType);
        }else {
            //case of just a non-collection (object or primitive value) returned:
            if(returnType.toString().substring(0,5).equalsIgnoreCase("class")){
                return returnType.toString().substring(6);
            }else{
                return returnType.toString();
            }
            //System.out.println("Return Type: " + returnType);
        }
    }

    /* Helper function to extract arguments from a method */
    public static ArrayList<String> extractArguments(AnalysisMethod method) {
        //Code to get the argument attribute:
        // Example: "arguments": "[@PathVariable Integer id]",
        Annotation[][] annotations1 = method.getParameterAnnotations();
        ArrayList<String> parameterAnnotationsList = new ArrayList<>();
        for (int i = 0; i < annotations1.length; i++) {
            Annotation[] annotations2 = annotations1[i];
            for (int j = 0; j < annotations2.length; j++) {
                Annotation annotation3 = annotations2[j];
                parameterAnnotationsList.add("@" + annotation3.annotationType().getSimpleName());
            }
        }

        Parameter[] params = method.getParameters();
        if(parameterAnnotationsList.size() > 0){
            int j = 0;
            for(Parameter p: params){
                String parameterType = p.getParameterizedType().toString();
                parameterAnnotationsList.set(j, parameterAnnotationsList.get(j) + " " + 
                parameterType.substring(parameterType.lastIndexOf(".")+1,parameterType.length()) + " " +
                p.getName());

                j++;
            }
        }
        return parameterAnnotationsList;
    }
}