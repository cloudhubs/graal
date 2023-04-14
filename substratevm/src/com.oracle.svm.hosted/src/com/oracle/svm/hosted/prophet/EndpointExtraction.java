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
import org.graalvm.polyglot.HostAccess;

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

    private final static String PUT_MAPPING = "org.springframework.web.bind.annotation.PutMapping";
    private final static String GET_MAPPING = "org.springframework.web.bind.annotation.GetMapping";
    private final static String POST_MAPPING = "org.springframework.web.bind.annotation.PostMapping";
    private final static String DELETE_MAPPING = "org.springframework.web.bind.annotation.DeleteMapping";

    //annotations for controller to get endpoints
    private static final Set<String> controllerAnnotationNames = new HashSet<>(Arrays.asList("GetMapping", "PutMapping", "DeleteMapping", "PostMapping"));
    public static Set<Endpoint> extractEndpoints(Class<?> clazz, AnalysisMetaAccess metaAccess, Inflation bb, String msName) {
        AnalysisType analysisType = metaAccess.lookupJavaType(clazz);
        Set<Endpoint> endpoints = new HashSet<Endpoint>();
        try {

            //Obtaining the class path (which will be combined with the method's path for a full URI)
            Annotation[] annotationsClass = clazz.getAnnotations();
            String[] fullPath = null;
            boolean hasFullPath = false;
            for (Annotation annotationClass : annotationsClass) {
                if (annotationClass.annotationType().getSimpleName().equals("RequestMapping")) {
                    Method pathMethod = annotationClass.annotationType().getMethod("value");
                    fullPath = (String[]) pathMethod.invoke(annotationClass);
                    hasFullPath = true;
                    //System.out.println(fullPath[0]);
                }
            }

            for (AnalysisMethod method : analysisType.getDeclaredMethods()) {
                try {      
                    // What I will need to extract: String httpMethod, String parentMethod, String arguments, String returnType
                    Annotation[] annotations = method.getAnnotations();
                    for (Annotation annotation : annotations) {
                        
                        ArrayList<String> parameterAnnotationsList = new ArrayList<>();
                        String httpMethod = null, parentMethod = null, returnTypeResult = null, path = "";
                        boolean returnTypeCollection = false, isEndpoint = false;
                        if (controllerAnnotationNames.contains(annotation.annotationType().getSimpleName())) {
                            isEndpoint = true;
                             //Code to get the parentMethod attribute:
                            //following the rad-source format for the parentMethod JSON need to parse before the first parenthesis
                            parentMethod = method.getQualifiedName().substring(0,method.getQualifiedName().indexOf("("));
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
                            
                            boolean hasPath = false;
                            try{
                                //System.out.println("Annotation object: " + annotation.toString());
                                // path is optional, thus attempting to get it and return null if so.
                                path = ((String[]) annotation.annotationType().getMethod("value").invoke(annotation))[0];
                                hasPath = true;
                            }catch(ArrayIndexOutOfBoundsException ex){
                                hasPath = false;
                            }

                             //Have to also consider the "path" JSON attribute of the annotation, (Example from train-tickets admin-user service):
                             //@org.springframework.web.bind.annotation.GetMapping(path={"/welcome"}, headers={}, name="", produces={}, params={}, 
                             //value={}, consumes={})
                             //This is with the assumption that a value == path when within an annotation!
                            if(!hasPath){
                                try{
                                    path = ((String[]) annotation.annotationType().getMethod("path").invoke(annotation))[0];
                                }catch(ArrayIndexOutOfBoundsException ex){}
                            }

                            parameterAnnotationsList = extractArguments(method);
                            returnTypeResult = extractReturnType(method);
                            if(returnTypeResult.startsWith("[L") && isCollection(returnTypeResult)){
                                returnTypeCollection = true;
                                returnTypeResult = returnTypeResult.substring(2);
                            }else{
                                returnTypeCollection = isCollection(returnTypeResult);
                            }
                            //Special case for request mapping 
                        }else if (annotation.annotationType().getSimpleName().equals("RequestMapping")){
                            isEndpoint = true;
                            //Code to get the parentMethod attribute:
                            parentMethod = method.getQualifiedName().substring(0,method.getQualifiedName().indexOf("("));


                            /* example of this case (in cms microservice):
                             *  @CrossOrigin
                                @RequestMapping(path = "/{id}/detail", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
                                public List<Question> getExamDetail(@PathVariable Integer id) {
                             */
                            String[] pathArr = (String[]) annotation.annotationType().getMethod("path").invoke(annotation);
                            //path = pathArr.length > 0 ? pathArr[0] : null;
                            path = pathArr.length > 0 ? pathArr[0] : "";


                            //case where this is needed:
                            /*
                             * ems microservice:
                             *  @CrossOrigin
                                 @RequestMapping(value = "/submit/{id}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
                                 public Exam submitExam(@PathVariable("id") Integer id) {
                             * 
                             * NOTE: This is a second attempt at fetching the path! (if the first approach above does not work!)
                             */
                            if(pathArr.length <= 0){
                                String[] valueArr = (String[]) annotation.annotationType().getMethod("value").invoke(annotation);
                                path = valueArr.length > 0 ? valueArr[0] : "";
                            }



                            //cant use a string[] because of ClassCastException 
                            Object[] methods = (Object[]) annotation.annotationType().getMethod("method").invoke(annotation);
                            if (methods.length > 0 && methods != null) {
                                httpMethod = methods[0].toString();
                            }

                            parameterAnnotationsList = extractArguments(method); 
                            returnTypeResult = extractReturnType(method);
                            if(returnTypeResult.startsWith("[L") && isCollection(returnTypeResult)){
                                returnTypeCollection = true;
                                returnTypeResult = returnTypeResult.substring(2);
                            }else{
                                returnTypeCollection = isCollection(returnTypeResult);
                            }
                        }

                        if(isEndpoint) {

                            //add the controller path as well (if it has a RequestMapping annotation)
                            String returnedPath = path;
                            if(hasFullPath){
                                returnedPath = fullPath[0] + path;
                            }
                                    
                            // System.out.println("HTTP Method: " + httpMethod);
                            // System.out.println("Path: " + returnedPath);
                            // System.out.println("parentMethod: " + parentMethod);
                            // for(String value : parameterAnnotationsList){
                            //     System.out.println("argument: " + value);
                            // }
                            // System.out.println("Return type: " + returnTypeResult);
                            // System.out.println("Is Collection: " + returnTypeCollection);
                            // System.out.println("============");

                            endpoints.add(new Endpoint(httpMethod, parentMethod, parameterAnnotationsList, returnTypeResult, returnedPath, returnTypeCollection, clazz.getCanonicalName(), msName));
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

            boolean exceptionOccurred = false;
            Class<?> collectionType = null, elementType = null;
            try{
                collectionType = (Class<?>) type.getRawType();
                elementType = (Class<?>) typeArgs[0];
            }catch(ClassCastException ex){
                //example where this would occur:
                /*
                    @CrossOrigin
                    @DeleteMapping("/{cateogryId}")
                    public ResponseEntity<?> deleteCateogry(@PathVariable Long cateogryId) 
                 */
                exceptionOccurred = true;
            }


            //objective: convert interface java.util.List -> java.util.List
            //double checking that it is "interface" portion removed (and not something else)
            if(collectionType.toString().substring(0,9).equalsIgnoreCase("interface")){
                String result = collectionType.toString().substring(10);

                if(!exceptionOccurred){
                    result = result + "<" + elementType.toString().substring(6) + ">";
                }else{
                    result = result + "<?>";
                }
                return result;
                //System.out.println("TESTING PARSING: " + result);
            }else {
                //TODO: need to handle Set, or other collection times (where this will break)

                if(!exceptionOccurred){
                    return collectionType.toString() + "<" + elementType.toString() + ">";
                }else{
                    return collectionType.toString() + "<?>";
                }
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

            //case of just void, need to check the length of the string first
            //case of just a non-collection (object or primitive value) returned:
            if(returnType.toString().length() == 5 && returnType.toString().substring(0,5).equalsIgnoreCase("class")){
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
        ArrayList<String> parameterAnnotationsList = new ArrayList<>();
        Parameter[] params = method.getParameters();
        Annotation[][] annotations1 = method.getParameterAnnotations();
    
        for (int i = 0; i < params.length; i++) {
            Annotation[] annotations2 = annotations1[i];
            //Parameter Annotations (e.g., @PathVariable) are optional, thus can be empty (null)
            String parameterAnnotation = "";
            for (int j = 0; j < annotations2.length; j++) {
                Annotation annotation3 = annotations2[j];
                parameterAnnotation += "@" + annotation3.annotationType().getSimpleName();
            }
            String parameterType = params[i].getParameterizedType().toString();
            String parameterName = params[i].getName();
            String simpleParameterType = parameterType.substring(parameterType.lastIndexOf(".")+1);
            String fullParameter = parameterAnnotation + " " + simpleParameterType + " " + parameterName;
            parameterAnnotationsList.add(fullParameter);
        }

        return parameterAnnotationsList;

    }

}