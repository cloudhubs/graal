package com.oracle.svm.hosted.prophet;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.reachability.ReachabilityAnalysisMethod;
import com.oracle.svm.core.meta.DirectSubstrateObjectConstant;
import com.oracle.svm.hosted.analysis.Inflation;
import com.oracle.svm.hosted.prophet.model.GraphQLCall;
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

import static com.oracle.svm.hosted.prophet.WebsocketConnectionExtraction.extractHostedClass;

public class GraphQLCallExtraction {

    // Constants representing key method and class names used in GraphQL call extraction.
    // These are used to identify specific methods and operations in the analyzed code.
    private final static String GraphQLClient = "GraphQlClient"; // Represents the GraphQL client class.
    private final static String RetrieveMethod = "retrieve"; // Method for retrieving data asynchronously.
    private final static String RetrieveSyncMethod = "retrieveSync"; // Method for retrieving data synchronously.
    private final static String VariableMethod = "variable"; // Method for setting variables in GraphQL queries.
    private final static String ToEntityMethod = "toEntity"; // Method for mapping the response to a single entity.
    private final static String ToEntityListMethod = "toEntityList"; // Method for mapping the response to a list of entities.
    private final static String DocumentMethod = "document"; // Method for specifying the GraphQL document or query.

    private static Set<GraphQLCall> graphqlCalls = new HashSet<>();

    /**
     * Extracts GraphQL call details from a given class by analyzing its methods and their associated
     * graphs. This method identifies specific GraphQL-related invocations, such as `retrieve`,
     * `variable`, `document`, and `toEntity`, to extract relevant information like URI, parameters,
     * and return types.
     *
     * The method uses GraalVM's analysis tools to decode the method graphs and traverse their nodes
     * to detect relevant invocations. Extracted data is stored in `GraphQLCall` objects and returned
     * as a set.
     *
     * Key Features:
     * - Detects `GraphQlClient` invocations to identify GraphQL operations.
     * - Extracts URIs, parameters, and documents from method arguments.
     * - Identifies return types by analyzing `toEntity` and `toEntityList` calls.
     * - Handles nested invocations and resolves dynamic values using a property map (`propMap`).
     *
     * @param clazz The class to analyze for GraphQL call details.
     * @param metaAccess The meta-access interface for type and method analysis.
     * @param bb The inflation object used for decoding graphs.
     * @param propMap A map of properties for resolving dynamic values (e.g., placeholders in URIs).
     * @param msName The name of the microservice or module being analyzed.
     * @return A set of `GraphQLCall` objects containing extracted GraphQL call details.
     */
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

    /**
     * Escapes a string for CSV by removing double quotes and newline characters.
     * If the input is null or empty, it returns an empty string.
     *
     * @param input The input string to be escaped.
     * @return The escaped string with double quotes and newlines removed.
     */
    private static String escapeForCSV(String input) {
        if (input == null || input.isEmpty()) return "";

        return input.replace("\"", "").replace("\n", "");
    }

    /**
     * Extracts a URI portion from a `CallTargetNode` by analyzing its arguments and traversing
     * through various node types such as `LoadFieldNode`, `PiNode`, `ConstantNode`, and others.
     * The method resolves dynamic values using a property map (`propMap`) and recursively processes
     * nested nodes to construct the full URI.
     *
     * Key Features:
     * - Handles `LoadFieldNode` to extract annotations and resolve values using `propMap`.
     * - Processes `PiNode` and its inputs recursively to extract URI components.
     * - Handles `ConstantNode` to extract constant values.
     * - Recursively processes `Invoke` nodes to extract nested URI portions.
     * - Skips unsupported or null node types gracefully.
     *
     * @param node The `CallTargetNode` to analyze for URI extraction.
     * @param propMap A map of properties for resolving dynamic values (e.g., placeholders in URIs).
     * @return A string representing the extracted URI portion.
     */
    private static String extractURI(CallTargetNode node, Map<String, Object> propMap) {
        String uriPortion = "";

        for (ValueNode arg : node.arguments()) {
            NodeIterable<Node> inputsList = arg.inputs();
            if (arg instanceof LoadFieldNode) {
                LoadFieldNode loadfieldNode = (LoadFieldNode) arg;
                AnalysisField field = (AnalysisField) loadfieldNode.field();

                for (java.lang.annotation.Annotation annotation : field.getWrapped().getAnnotations()) {
                    if (annotation.annotationType().getName().contains("Value")) {
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
                for (Node inputNode : ((PiNode) arg).inputs()) {
                    if (inputNode instanceof Invoke) {
                        uriPortion = uriPortion + extractURI(((Invoke) inputNode).callTarget(), propMap);
                    }
                }
            } else if (arg instanceof ConstantNode) {
                ConstantNode cn = (ConstantNode) arg;
                if (!(cn.getValue() instanceof PrimitiveConstant)) {
                    DirectSubstrateObjectConstant dsoc = (DirectSubstrateObjectConstant) cn.getValue();
                    uriPortion = uriPortion + dsoc.getObject().toString();
                }

            } else if (arg instanceof Invoke) {
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
     * Extracts the method name from a fully qualified method signature by removing
     * the parameter list. For example, given "com.example.Class.method(String)",
     * it will return "com.example.Class.method".
     *
     * @param input The fully qualified method signature.
     * @return The method name without the parameter list.
     */
    private static String cleanParentMethod(String input) {
        String parentMethod = null;

        parentMethod = input.substring(0, input.indexOf("("));
        return parentMethod;
    }

    /**
     * Resolves a placeholder expression (e.g., `${key.subkey}`) by traversing a nested map (`propMap`).
     * The method extracts the key path from the expression, splits it into parts, and navigates
     * through the map to find the corresponding value.
     *
     * Key Features:
     * - Supports nested key resolution using dot-separated paths.
     * - Returns the resolved value as a string if found.
     * - Handles invalid map structures gracefully with error logging.
     * - Returns `null` if the key is not found or the value is not a string.
     *
     * @param expr The placeholder expression to resolve (e.g., `${key.subkey}`).
     * @param propMap A map containing the key-value pairs for resolution.
     * @return The resolved string value, or `null` if resolution fails.
     */
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
