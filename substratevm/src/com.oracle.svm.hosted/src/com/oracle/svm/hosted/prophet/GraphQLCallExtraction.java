package com.oracle.svm.hosted.prophet;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.reachability.ReachabilityAnalysisMethod;
import com.oracle.svm.core.meta.DirectSubstrateObjectConstant;
import com.oracle.svm.hosted.analysis.Inflation;
import com.oracle.svm.hosted.prophet.model.GraphQLCall;
import com.oracle.svm.hosted.prophet.model.RESTParameter;
import jdk.vm.ci.meta.PrimitiveConstant;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.oracle.svm.hosted.prophet.WebsocketCallExtraction.extractHostedClass;

public class GraphQLCallExtraction {

    /*
       NOTE:
       'msRoot' can be obtained in Utils or RAD
       'source' can be obtained in RAD repo in the RadSourceService file in generateRestEntityContext method where getSourceFiles is
   */
    private final static String GraphQLClient = "GraphQlClient";
    private final static String RetrieveMethod = "retrieve";
    private final static String RetrieveSyncMethod = "retrieveSync";
    private final static String VariableMethod = "variable";
    private final static String ToEntityMethod = "toEntity";
    private final static String ToEntityListMethod = "toEntityList";
    private final static String DocumentMethod = "document";

    private static Set<GraphQLCall> graphqlCalls = new HashSet<>();

    public static Set<GraphQLCall> extractClassRestCalls(Class<?> clazz, AnalysisMetaAccess metaAccess, Inflation bb, Map<String, Object> propMap, String msName) {
        AnalysisType analysisType = metaAccess.lookupJavaType(clazz);
        try {
            for (AnalysisMethod method : ((AnalysisMethod[]) analysisType.getDeclaredMethods())) {
                if (method.isAbstract()) {
                    continue;
                }
                try {

                    String URI = "";
                    String RETURN_TYPE = null;
                    Boolean callIsCollection = false;
                    String PARENT_METHOD = "";
                    String param = "";
                    String document = "";

                    StructuredGraph decodedGraph = ReachabilityAnalysisMethod.getDecodedGraph(bb, method);
                    for (Node node : decodedGraph.getNodes()) {
                        if (node instanceof Invoke) {
                            Invoke invoke = (Invoke) node;
                            AnalysisMethod targetMethod = ((AnalysisMethod) invoke.getTargetMethod());

                            String qualifiedName = targetMethod.getQualifiedName();
                            if (qualifiedName.contains(GraphQLClient)) {
                                if (qualifiedName.contains(RetrieveMethod) || qualifiedName.contains(RetrieveSyncMethod)) {

                                    PARENT_METHOD = cleanParentMethod(method.getQualifiedName());
                                    CallTargetNode callTargetNode = invoke.callTarget();
                                    NodeInputList<ValueNode> arguments = callTargetNode.arguments();

                                    for (ValueNode v : arguments) {
                                        if (v instanceof Invoke) {
                                            URI += extractURI(((Invoke) v).callTarget(), propMap);
                                        } else if (v instanceof ConstantNode && !v.isNullConstant() && !v.isIllegalConstant()) {
                                            ConstantNode cn = (ConstantNode) v;

                                            DirectSubstrateObjectConstant dsoc = (DirectSubstrateObjectConstant) cn.getValue();
                                            URI += dsoc.getObject().toString();
                                        }
                                    }

                                }

                                if (qualifiedName.contains(VariableMethod)) {

                                    CallTargetNode callTargetNode = invoke.callTarget();
                                    NodeInputList<ValueNode> arguments = callTargetNode.arguments();

                                    for (ValueNode v : arguments) {
                                        if (v instanceof Invoke) {
                                            param = extractURI(((Invoke) v).callTarget(), propMap);
                                        } else if (v instanceof ConstantNode && !v.isNullConstant() && !v.isIllegalConstant()) {
                                            ConstantNode cn = (ConstantNode) v;

                                            DirectSubstrateObjectConstant dsoc = (DirectSubstrateObjectConstant) cn.getValue();
                                            param = dsoc.getObject().toString();
                                        }
                                    }
                                }

                                if (qualifiedName.contains(DocumentMethod)) {

                                    CallTargetNode callTargetNode = invoke.callTarget();
                                    NodeInputList<ValueNode> arguments = callTargetNode.arguments();

                                    for (ValueNode v : arguments) {
                                        if (v instanceof Invoke) {
                                            document = extractURI(((Invoke) v).callTarget(), propMap);
                                        } else if (v instanceof ConstantNode && !v.isNullConstant() && !v.isIllegalConstant()) {
                                            ConstantNode cn = (ConstantNode) v;

                                            DirectSubstrateObjectConstant dsoc = (DirectSubstrateObjectConstant) cn.getValue();
                                            document = dsoc.getObject().toString();
                                        }
                                    }

                                    document = escapeForCSV(document);
                                }

                                if (qualifiedName.contains(ToEntityMethod) || qualifiedName.contains(ToEntityListMethod)) {

                                    CallTargetNode callTargetNode = invoke.callTarget();
                                    NodeInputList<ValueNode> arguments = callTargetNode.arguments();

                                    for (ValueNode v : arguments) {
                                        if (v instanceof ConstantNode && !v.isNullConstant() && !v.isIllegalConstant()) {
                                            ConstantNode cn = (ConstantNode) v;
                                            Object payloadObject = cn.getValue();

                                            if (payloadObject instanceof DirectSubstrateObjectConstant) {
                                                DirectSubstrateObjectConstant dsoc = (DirectSubstrateObjectConstant) payloadObject;
                                                Object possibleClass = dsoc.getObject();
                                                if (possibleClass instanceof com.oracle.svm.core.hub.DynamicHub) {
                                                    Class<?> actualClass = extractHostedClass(possibleClass);
                                                    RETURN_TYPE = actualClass.getName(); // full path
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (URI != null && !URI.isEmpty()) {
                        graphqlCalls.add(new GraphQLCall(PARENT_METHOD, RETURN_TYPE, URI, callIsCollection,
                                clazz.getCanonicalName(), msName, param, document));
                    }
                } catch (Exception | LinkageError ex) {
                    ex.printStackTrace();
                }
            }
        } catch (Exception | LinkageError ex) {
            ex.printStackTrace();
        }
        return graphqlCalls;
    }

    // Escapes double quotes and wraps the string in quotes for CSV safety
    private static String escapeForCSV(String input) {
        if (input == null || input.isEmpty()) return ""; // Return empty string if input is null or empty

        return input.replace("\"", "").replace("\n", "");
    }


    private static RESTParameter getParamDetails(CallTargetNode node, String URI) {

        RESTParameter param = new RESTParameter(false, false);
        //check if URI has slashes then it has path parameters
        //check for slashes at end of string
        int count = 0;
        boolean slashFound = false;
        for (int i = 0; i < URI.length(); i++) {
            char c = URI.charAt(i);
            if (i == URI.length() - 1 && c == '/') {
                count++;
            } else if (c == '/' && URI.charAt(i + 1) == '/') {
                count++;
            }
        }
        param.setParamCount(count);
        if (count > 0) {
            param.setIsPath(true);
        }
        param = setIfBodyAndType(param, node);

        return param;
    }

    //assumes there is only one HTTP_ENTITY object in each REST call method
    private static RESTParameter setIfBodyAndType(RESTParameter param, CallTargetNode node) {

        for (ValueNode arg : node.arguments()) {

            if (arg instanceof PiNode) {

                for (Node inputNode : ((PiNode) arg).inputs()) {

                    if (inputNode instanceof Invoke) {
                        param = setIfBodyAndType(param, ((Invoke) inputNode).callTarget());

                    }
                }
            } else if (arg instanceof Invoke) {

//                param = handleIfInvokeInRESTParam(param, arg);
            } else {

            }
        }
        return param;
    }

    private static String cleanReturnType(String returnType) {
        String parsedType = null;
        if (returnType == null || returnType.equals("null")) {
            return parsedType;
        }
        //remove 'class [L' example: 'class [Ljava.lang.Object]' -> 'java.lang.Object'
        if (isCollection(returnType)) {
            parsedType = returnType.substring(8);
        }
        //remove 'class ' example: 'class [Ljava.lang.Object]' -> '[Ljava.lang.Object'
        else {
            parsedType = returnType.substring(6);
        }
        return parsedType;
    }

    private static boolean isCollection(String returnType) {
        if (returnType == null || returnType.equals("null")) {
            return false;
        }
        //graal api indicates collections in return type with "class [L" before the type name
        return returnType.startsWith("class [L");
    }

    private static String extractURI(CallTargetNode node, Map<String, Object> propMap) {
        // System.out.println("NODE CALL TARGET: " + node);
        // System.out.println("NODE CALL TARGET ARGS: " + node.arguments());
        String uriPortion = "";

        /*
         * Loop over the arguments in the call target node
         * if the node in the argument is an Invoke, call its target
         * else if node is a loadfieldnode, go over annotations and get 'value' annotation
         * get value based off prop map
         */
        for (ValueNode arg : node.arguments()) {
            NodeIterable<Node> inputsList = arg.inputs();
            if (arg instanceof LoadFieldNode) {
                // System.out.println("arg is a LOAD_FIELD_NODE, arg = " + arg);
                LoadFieldNode loadfieldNode = (LoadFieldNode) arg;
                AnalysisField field = (AnalysisField) loadfieldNode.field();

                for (java.lang.annotation.Annotation annotation : field.getWrapped().getAnnotations()) {
                    if (annotation.annotationType().getName().contains("Value")) {
                        // System.out.println("Load field with value annotation");
                        // System.out.println("methods = " + annotation.annotationType().getMethods());
                        try {
                            Method valueMethod = annotation.annotationType().getMethod("value");
                            valueMethod.setAccessible(true);
                            String res = "";
                            if (propMap != null) {
                                res = tryResolve(((String) valueMethod.invoke(annotation)), propMap);
                            }
                            uriPortion = uriPortion + res;
                        } catch (Exception ex) {
                            System.err.println("ERROR = " + ex);
                        }
                    }
                }

            } else if (arg instanceof PiNode) {
                // System.out.println(arg + " is a PiNode");
                // System.out.println("pi node inputs: " + ((PiNode)arg).inputs());
                for (Node inputNode : ((PiNode) arg).inputs()) {
                    if (inputNode instanceof Invoke) {
                        // System.out.println(inputNode + " is Invoke");
                        uriPortion = uriPortion + extractURI(((Invoke) inputNode).callTarget(), propMap);
                    }
                }
            } else if (arg instanceof ConstantNode) {
                ConstantNode cn = (ConstantNode) arg;
                //PrimitiveConstants can not be converted to DirectSubstrateObjectConstant
                if (!(cn.getValue() instanceof PrimitiveConstant)) {
                    DirectSubstrateObjectConstant dsoc = (DirectSubstrateObjectConstant) cn.getValue();
                    uriPortion = uriPortion + dsoc.getObject().toString();
                }

            } else if (arg instanceof Invoke) {
                // System.out.println("arg = " + arg + " && is an instance of invoke");
                uriPortion = uriPortion + extractURI(((Invoke) arg).callTarget(), propMap);
            } else {
                for (Node n : inputsList) {
                    if (n instanceof Invoke) {
                        ;
                        uriPortion = uriPortion + extractURI(((Invoke) n).callTarget(), propMap);
                    }
                }
            }

        }
        return uriPortion;
    }

    /**
     * extract the method the rest call is being in
     *
     * @param input the method's qualified name
     * @return the method the call is being made in
     */
    private static String cleanParentMethod(String input) {
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
                try {
                    curr = ((Map<String, Object>) value);
                } catch (ClassCastException ex) {
                    ex.printStackTrace();
                }
            }
        }
        return null;
    }

}
