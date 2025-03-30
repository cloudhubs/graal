package com.oracle.svm.hosted.prophet;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.hosted.analysis.Inflation;
import com.oracle.svm.hosted.prophet.model.Endpoint;
import com.oracle.svm.hosted.prophet.model.GraphQLEndpoint;
import jdk.vm.ci.meta.ResolvedJavaMethod.Parameter;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class GraphQLEndpointExtraction {

    private final static String QUERY_MAPPING = "org.springframework.graphql.data.method.annotation.QueryMapping";
    private final static String MUTATION_MAPPING = "org.springframework.graphql.data.method.annotation.MutationMapping";

    // annotations for controller to get endpoints
    private static final Set<String> controllerAnnotationNames = new HashSet<>(Arrays.asList("QueryMapping", "MutationMapping"));

    public static Set<GraphQLEndpoint> extractEndpoints(Class<?> clazz, AnalysisMetaAccess metaAccess, Inflation bb, String msName) {
        AnalysisType analysisType = metaAccess.lookupJavaType(clazz);
        Set<GraphQLEndpoint> endpoints = new HashSet<GraphQLEndpoint>();
        try {

            for (AnalysisMethod method : ((AnalysisMethod[]) analysisType.getDeclaredMethods())) {
                try {
                    // What I will need to extract: String httpMethod, String parentMethod, String
                    // arguments, String returnType
                    Annotation[] annotations = method.getWrapped().getAnnotations();
                    for (Annotation annotation : annotations) {

                        ArrayList<String> parameterAnnotationsList = new ArrayList<>();
                        String httpMethod = null, parentMethod = null, returnTypeResult = null, path = "";
                        boolean returnTypeCollection = false, isEndpoint = false;
                        if (controllerAnnotationNames.contains(annotation.annotationType().getSimpleName())) {
                            isEndpoint = true;
                            // Code to get the parentMethod attribute:
                            // following the rad-source format for the parentMethod JSON need to
                            // parse before the first parenthesis
                            parentMethod = method.getQualifiedName().substring(0, method.getQualifiedName().indexOf("("));
                            path = method.getName(); // Save parentMethod as path
                            if (annotation.annotationType().getName().startsWith(QUERY_MAPPING)) {
                                httpMethod = "QUERY";
                            } else if (annotation.annotationType().getName().startsWith(MUTATION_MAPPING)) {
                                httpMethod = "MUTATION";
                            }

                            parameterAnnotationsList = extractArguments(method);
                            returnTypeResult = extractReturnType(method);
                            if (returnTypeResult.startsWith("[L") && isCollection(returnTypeResult)) {
                                returnTypeCollection = true;
                                returnTypeResult = returnTypeResult.substring(2);
                            } else {
                                returnTypeCollection = isCollection(returnTypeResult);
                            }
                            // Special case for request mapping
                        }

                        if (isEndpoint) {
                            endpoints.add(new GraphQLEndpoint(httpMethod, parentMethod, parameterAnnotationsList, returnTypeResult, path, returnTypeCollection, clazz.getCanonicalName(), msName));
                        }
                    }

                } catch (Exception | LinkageError ex) {
                    ex.printStackTrace();
                }
            }
        } catch (Exception | LinkageError ex) {
            ex.printStackTrace();
        }

        return endpoints;
    }

    private static boolean isCollection(String returnType) {
        if (returnType == null || returnType.equals("null")) {
            return false;
        }
        // graal api indicates collections in return type with "class [L" OR
        return returnType.startsWith("[L") || returnType.matches(".*[<].*[>]");
    }

    /**
     * Method extracts and cleans the return type value of a controller method (based on a
     * collection or object/primitive data type)
     *
     * @param method an AnalysisMethod
     * @return the method's return type as a string value
     */
    public static String extractReturnType(AnalysisMethod method) {
        Method javaMethod = (Method) method.getJavaMethod();
        Type returnType = javaMethod.getGenericReturnType();

        if (returnType.toString().length() == 5 && returnType.toString().substring(0, 5).equalsIgnoreCase("class")) {
            return returnType.toString().substring(6);
        } else {
            return returnType.toString();
        }

    }

    public static ArrayList<String> extractArguments(AnalysisMethod method) {

        // Code to get the argument attribute:
        // Example: "arguments": "[@PathVariable Integer id]",
        ArrayList<String> parameterAnnotationsList = new ArrayList<>();
        Parameter[] params = method.getParameters();
        Annotation[][] annotations1 = method.getParameterAnnotations();

        for (int i = 0; i < params.length; i++) {
            Annotation[] annotations2 = annotations1[i];
            // Parameter Annotations (e.g., @PathVariable) are optional, thus can be empty (null)
            String parameterAnnotation = "";
            for (int j = 0; j < annotations2.length; j++) {
                Annotation annotation3 = annotations2[j];
                parameterAnnotation += "@" + annotation3.annotationType().getSimpleName();
            }
            String parameterType = params[i].getParameterizedType().toString();
            String parameterName = params[i].getName();
            String simpleParameterType = parameterType.substring(parameterType.lastIndexOf(".") + 1);
            String fullParameter = parameterAnnotation + " " + simpleParameterType + " " + parameterName;
            parameterAnnotationsList.add(fullParameter);
        }

        return parameterAnnotationsList;

    }

}