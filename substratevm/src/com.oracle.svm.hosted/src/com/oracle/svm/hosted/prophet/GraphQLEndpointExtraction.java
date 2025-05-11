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

    /**
     * Constants and a set for identifying GraphQL controller annotations.
     * These annotations are used to determine if a method is a GraphQL endpoint.
     * - QUERY_MAPPING: Represents the annotation for GraphQL query methods.
     * - MUTATION_MAPPING: Represents the annotation for GraphQL mutation methods.
     * - controllerAnnotationNames: A set containing the simple names of the supported annotations.
     */
    private final static String QUERY_MAPPING = "org.springframework.graphql.data.method.annotation.QueryMapping";
    private final static String MUTATION_MAPPING = "org.springframework.graphql.data.method.annotation.MutationMapping";
    private static final Set<String> controllerAnnotationNames = new HashSet<>(Arrays.asList("QueryMapping", "MutationMapping"));

    /**
     * Extracts GraphQL endpoints from a given class by analyzing its methods and annotations.
     * This method identifies methods annotated with GraphQL-specific annotations (e.g., `QueryMapping`, `MutationMapping`)
     * and collects their metadata, such as the GraphQL method type, parent method, return type, and parameters.
     *
     * Key Features:
     * - Iterates through all declared methods of the class.
     * - Checks for supported GraphQL annotations to determine if a method is an endpoint.
     * - Extracts method metadata, including parameter annotations, return type, and path.
     * - Handles collection return types and nested annotations.
     * - Returns a set of `GraphQLEndpoint` objects representing the extracted endpoints.
     *
     * @param clazz The class to analyze for GraphQL endpoints.
     * @param metaAccess Provides access to meta-information about the class.
     * @param bb An `Inflation` object for additional analysis context.
     * @param msName The name of the microservice or module being analyzed.
     * @return A set of `GraphQLEndpoint` objects representing the extracted endpoints.
     */
    public static Set<GraphQLEndpoint> extractEndpoints(Class<?> clazz, AnalysisMetaAccess metaAccess, Inflation bb, String msName) {
        AnalysisType analysisType = metaAccess.lookupJavaType(clazz);
        Set<GraphQLEndpoint> endpoints = new HashSet<GraphQLEndpoint>();
        try {

            for (AnalysisMethod method : ((AnalysisMethod[]) analysisType.getDeclaredMethods())) {
                try {
                    Annotation[] annotations = method.getWrapped().getAnnotations();
                    for (Annotation annotation : annotations) {

                        ArrayList<String> parameterAnnotationsList = new ArrayList<>();
                        String graphqlMethod = null, parentMethod = null, returnTypeResult = null, path = "";
                        boolean returnTypeCollection = false, isEndpoint = false;
                        if (controllerAnnotationNames.contains(annotation.annotationType().getSimpleName())) {
                            isEndpoint = true;
                            parentMethod = method.getQualifiedName().substring(0, method.getQualifiedName().indexOf("("));
                            path = method.getName();
                            if (annotation.annotationType().getName().startsWith(QUERY_MAPPING)) {
                                graphqlMethod = "QUERY";
                            } else if (annotation.annotationType().getName().startsWith(MUTATION_MAPPING)) {
                                graphqlMethod = "MUTATION";
                            }

                            parameterAnnotationsList = extractArguments(method);
                            returnTypeResult = extractReturnType(method);
                            if (returnTypeResult.startsWith("[L") && isCollection(returnTypeResult)) {
                                returnTypeCollection = true;
                                returnTypeResult = returnTypeResult.substring(2);
                            } else {
                                returnTypeCollection = isCollection(returnTypeResult);
                            }
                        }

                        if (isEndpoint) {
                            endpoints.add(new GraphQLEndpoint(graphqlMethod, parentMethod, parameterAnnotationsList, returnTypeResult, path, returnTypeCollection, clazz.getCanonicalName(), msName));
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

    /**
     * Determines if the given return type represents a collection.
     * This method checks for array types (indicated by "[L") or generic collection types
     * (indicated by the presence of angle brackets "<...>").
     *
     * @param returnType The return type as a string.
     * @return `true` if the return type is a collection, otherwise `false`.
     */
    private static boolean isCollection(String returnType) {
        if (returnType == null || returnType.equals("null")) {
            return false; // Not a collection if null or "null"
        }
        // Check for array or generic collection types
        return returnType.startsWith("[L") || returnType.matches(".*[<].*[>]");
    }


    /**
     * Extracts the return type of a given method as a string.
     * This method retrieves the generic return type of the provided `AnalysisMethod`
     * and processes it to return a simplified representation.
     *
     * Key Features:
     * - Handles return types that are classes by removing the "class " prefix.
     * - Returns the full string representation of the return type for other cases.
     *
     * @param method The `AnalysisMethod` whose return type is to be extracted.
     * @return A string representing the return type of the method.
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

    /**
     * Extracts the arguments of a given method along with their annotations, types, and names.
     * This method processes the parameters of the provided `AnalysisMethod` and constructs
     * a list of strings representing each parameter in the format:
     *
     * `@AnnotationName ParameterType ParameterName`
     *
     * Key Features:
     * - Iterates through all parameters of the method.
     * - Collects annotations for each parameter and appends them to the result.
     * - Extracts the simple type name and parameter name for clarity.
     * - Returns a list of formatted strings representing the method's parameters.
     *
     * @param method The `AnalysisMethod` whose parameters are to be extracted.
     * @return A list of strings representing the parameters with annotations, types, and names.
     */
    public static ArrayList<String> extractArguments(AnalysisMethod method) {

        ArrayList<String> parameterAnnotationsList = new ArrayList<>();
        Parameter[] params = method.getParameters();
        Annotation[][] annotations1 = method.getParameterAnnotations();

        for (int i = 0; i < params.length; i++) {
            Annotation[] annotations2 = annotations1[i];
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