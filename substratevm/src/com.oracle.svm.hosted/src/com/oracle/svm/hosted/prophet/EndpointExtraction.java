package com.oracle.svm.hosted.prophet;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.reachability.ReachabilityAnalysisMethod;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.analysis.Inflation;
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

import org.graalvm.polyglot.*;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EndpointExtraction {

    private final static String REST_TEMPLATE_PACKAGE = "org.springframework.web.client.RestTemplate.";
    private final static String PUT_MAPPING = "org.springframework.web.bind.annotation.PutMapping";
    private final static String GET_MAPPING = "org.springframework.web.bind.annotation.GetMapping";
    private final static String POST_MAPPING = "org.springframework.web.bind.annotation.PostMapping";
    private final static String DELETE_MAPPING = "org.springframework.web.bind.annotation.DeleteMapping";

    //annotations for controller to get endpoints
    private static final Set<String> controllerAnnotationNames = new HashSet<>(Arrays.asList("GetMapping", "PutMapping", "DeleteMapping", "PostMapping"));
    public static void extractEndpoints(Class<?> clazz, AnalysisMetaAccess metaAccess, Inflation bb) {
        AnalysisType analysisType = metaAccess.lookupJavaType(clazz);
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
                            
                            String httpMethod = null, path = null;
                            if (annotation.annotationType().getName().startsWith(PUT_MAPPING)) {
                                path = ((String[]) annotation.annotationType().getMethod("value").invoke(annotation))[0];
                                httpMethod = "PUT";
                            } else if (annotation.annotationType().getName().startsWith(GET_MAPPING)) {
                                path = ((String[]) annotation.annotationType().getMethod("value").invoke(annotation))[0];
                                httpMethod = "GET";
                            } else if (annotation.annotationType().getName().startsWith(POST_MAPPING)) {
                                path = ((String[]) annotation.annotationType().getMethod("value").invoke(annotation))[0];
                                httpMethod = "POST";
                            } else if (annotation.annotationType().getName().startsWith(DELETE_MAPPING)) {
                                path = ((String[]) annotation.annotationType().getMethod("value").invoke(annotation))[0];
                                httpMethod = "DELETE";
                            }

                            ArrayList<String> parameterAnnotationsList = extractArguments(method);
                            System.out.println("HTTP Method: " + httpMethod);
                            System.out.println("Path: " + path);
                            System.out.println("parentMethod: " + parentMethod);
                            for(String value : parameterAnnotationsList){
                                System.out.println("argument: " + value);
                            }

                            // Class<?>[] returnTypes = (Class<?>[]) annotation.getClass().getDeclaredMethod("value").invoke(annotation);
                            // for (Class<?> returnType : returnTypes) {
                            //     System.out.println("Return type: " + returnType.getName());
                            // }
                            System.out.println(method.toString());

                            System.out.println("============");
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
                            System.out.println("HTTP Method: " + httpMethod);
                            System.out.println("Path: " + path);
                            System.out.println("parentMethod: " + parentMethod);
                            for(String value : parameterAnnotationsList){
                                System.out.println("argument: " + value);
                            }
                            
                       
                            //System.out.println(method.getSignature());
                            
                            System.out.println("============");
                        }
                        
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
