package com.oracle.svm.hosted.prophet;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.reachability.ReachabilityAnalysisMethod;
import com.oracle.svm.core.meta.DirectSubstrateObjectConstant;
import com.oracle.svm.hosted.analysis.Inflation;
import com.oracle.svm.hosted.prophet.model.WebsocketConnection;
import com.oracle.svm.hosted.prophet.model.WebsocketEndpoint;
import com.oracle.svm.hosted.prophet.model.WebsocketMessageType;
import com.oracle.svm.hosted.prophet.model.WebsocketParameter;
import jdk.vm.ci.meta.PrimitiveConstant;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.virtual.AllocatedObjectNode;
import org.graalvm.compiler.nodes.virtual.CommitAllocationNode;
import org.graalvm.compiler.nodes.virtual.VirtualArrayNode;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class WebsocketCallExtraction {

    /*
       NOTE:
       'msRoot' can be obtained in Utils or RAD
       'source' can be obtained in RAD repo in the RadSourceService file in generateWebsocketEntityContext method where getSourceFiles is
   */
    private final static String WEBSOCKET_TEXT_MESSAGE = "org.springframework.web.socket.TextMessage";

    static String URI = "";

    public static Class<?> extractHostedClass(Object dynamicHub) throws NoSuchFieldException, IllegalAccessException {
        // The hosted class is typically stored in a field of the DynamicHub
        Field hostedClassField = dynamicHub.getClass().getDeclaredField("hostedJavaClass");

        // Make the field accessible, since it is usually private
        hostedClassField.setAccessible(true);

        // Retrieve the actual class type from the DynamicHub
        return (Class<?>) hostedClassField.get(dynamicHub);
    }

    // Helper class to hold both URI and handler data
    private static class ExtractionResult {
        StringBuilder uriBuilder;
        StringBuilder handlerBuilder;

        public ExtractionResult(StringBuilder uriBuilder, StringBuilder handlerBuilder) {
            this.uriBuilder = uriBuilder;
            this.handlerBuilder = handlerBuilder;
        }
    }

    private static ExtractionResult logPrecedingNodesRecursiveURIandHandler(
            Node currentNode, Set<Node> visitedNodes, StringBuilder uriBuilder, StringBuilder handlerBuilder) {
        if (visitedNodes.contains(currentNode)) {
            return new ExtractionResult(uriBuilder, handlerBuilder);
        }

        visitedNodes.add(currentNode);

        System.out.println("Node: " + currentNode + ", Class: " + currentNode.getClass().getName());

        // Check if the current node is the specific `Invoke#StringConcatHelper.simpleConcat` node
        if (currentNode instanceof InvokeWithExceptionNode) {
            InvokeWithExceptionNode invokeNode = (InvokeWithExceptionNode) currentNode;
            if (invokeNode.callTarget().targetName().contains("StringConcatHelper.simpleConcat")) {
                System.out.println("Found target Invoke: " + invokeNode);

                // Process the concatenation components
                NodeInputList<ValueNode> concatArgs = invokeNode.callTarget().arguments();
                for (ValueNode concatArg : concatArgs) {
                    if (concatArg instanceof ConstantNode) {
                        ConstantNode constantNode = (ConstantNode) concatArg;
                        uriBuilder.append(constantNode.asJavaConstant().toValueString());
                    }
                }
                System.out.println("Concatenated URI: " + uriBuilder.toString());
            }
        }

        // Check if the current node is an Invoke for handler instantiation
        if (currentNode instanceof InvokeWithExceptionNode) {
            InvokeWithExceptionNode invokeNode = (InvokeWithExceptionNode) currentNode;
            if (invokeNode.callTarget().targetName().contains(".<init>")) { // Look for constructors
                System.out.println("Found handler constructor: " + invokeNode);

                // Extract the handler class name
                handlerBuilder.append(invokeNode.callTarget().targetName().split("\\.")[0]); // Assuming format Class.<init>
                System.out.println("Handler: " + handlerBuilder.toString());
            }
        }

        // Continue traversing predecessors
        for (Node predecessor : currentNode.cfgPredecessors()) {
            logPrecedingNodesRecursive(predecessor, visitedNodes, uriBuilder, handlerBuilder);
        }

        return new ExtractionResult(uriBuilder, handlerBuilder);
    }

    private static StringBuilder logPrecedingNodesRecursive(Node currentNode, Set<Node> visitedNodes, StringBuilder uriBuilder, StringBuilder handlerBuilder) {
        if (visitedNodes.contains(currentNode)) {
            return uriBuilder;
        }

        visitedNodes.add(currentNode);

        System.out.println("Node: " + currentNode + ", Class: " + currentNode.getClass().getName());

        // Check if the current node is the specific `Invoke#StringConcatHelper.simpleConcat` node
        if (currentNode instanceof InvokeWithExceptionNode) {
            InvokeWithExceptionNode invokeNode = (InvokeWithExceptionNode) currentNode;
            if (invokeNode.callTarget().targetName().contains("StringConcatHelper.simpleConcat")) {
                System.out.println("Found target Invoke: " + invokeNode);

                // Process the concatenation components
                NodeInputList<ValueNode> concatArgs = invokeNode.callTarget().arguments();
                for (ValueNode concatArg : concatArgs) {
                    if (concatArg instanceof ConstantNode) {
                        ConstantNode constantNode = (ConstantNode) concatArg;
                        uriBuilder.append(constantNode.asJavaConstant().toValueString());
                    }
                }
                System.out.println("Concatenated URI: " + uriBuilder.toString());
            }
        }

        // Check if the current node is an Invoke for handler instantiation
        if (currentNode instanceof InvokeWithExceptionNode) {
            InvokeWithExceptionNode invokeNode = (InvokeWithExceptionNode) currentNode;
            if (invokeNode.callTarget().targetName().contains(".<init>")) { // Look for constructors
                System.out.println("Found handler constructor: " + invokeNode);

                // Extract the handler class name
                handlerBuilder.append(invokeNode.callTarget().targetName().split("\\.")[0]); // Assuming format Class.<init>
                System.out.println("Handler: " + handlerBuilder.toString());
            }
        }

        // Continue traversing predecessors
        for (Node predecessor : currentNode.cfgPredecessors()) {
            logPrecedingNodesRecursive(predecessor, visitedNodes, uriBuilder, handlerBuilder);
        }
        return uriBuilder;
    }

    public static Set<WebsocketConnection> extractClassWebsocketConnection(Class<?> clazz, AnalysisMetaAccess metaAccess, Inflation bb, Map<String, Object> propMap, String msName) {
        Set<WebsocketConnection> websocketConnections = new HashSet<>();
        AnalysisType analysisType = metaAccess.lookupJavaType(clazz);
        String wsHandler = null;

        try {
            for (AnalysisMethod method : ((AnalysisMethod[]) analysisType.getDeclaredMethods())) {

                if (method.isAbstract()) {
                    continue;
                }

                try {
                    StructuredGraph decodedGraph = ReachabilityAnalysisMethod.getDecodedGraph(bb, method);

                    String uri = null;
                    String returnType = null;
                    WebsocketParameter param = null;
                    boolean isCollection = false;
                    StringBuilder uriBuilder = new StringBuilder();

                    // Loop through all nodes in the graph
                    for (Node node : decodedGraph.getNodes()) {
                        // Detect URI.create invocations
                        if (node instanceof Invoke) {
                            Invoke invoke = (Invoke) node;
                            AnalysisMethod targetMethod = (AnalysisMethod) invoke.getTargetMethod();

                            // Briefly explained:
                            // 1) Check if there are at least two arguments.
                            // 2) If the second argument is AllocatedObjectNode, extract the type name from its stamp.
                            if (targetMethod.getQualifiedName().contains("WebSocketStompClient.connect")) {
                                CallTargetNode ct = invoke.callTarget();
                                if (!ct.arguments().isEmpty()) {
                                    uri = extractURI(ct, propMap);
                                }
                                if (ct.arguments().size() > 1) {
                                    ValueNode secondArg = ct.arguments().get(2);
                                    if (secondArg instanceof ParameterNode) {
                                        ParameterNode paramNode = (ParameterNode) secondArg;
                                        ObjectStamp stamp = (ObjectStamp) paramNode.uncheckedStamp();
                                        wsHandler = stamp.type().toJavaName();
                                    }
                                }
                            }

                            // Detect WebSocketClient.doHandshake method
                            if (targetMethod.getQualifiedName().contains("WebSocketClient.doHandshake")) {
                                System.out.println("Detected WebSocketClient.doHandshake invocation.");

                                // Extract handler from the invocation parameters
                                CallTargetNode callTargetNode = invoke.callTarget();
                                NodeInputList<ValueNode> arguments = callTargetNode.arguments();

                                if (arguments.size() > 1) {
                                    uri = extractURI(callTargetNode, propMap);
                                    System.out.println("Extracted URI: " + URI);
                                }

                                for (ValueNode arg : arguments) {
                                    // Check if the argument is an AllocatedObjectNode (potential handler)
                                    if (arg instanceof AllocatedObjectNode) {
                                        AllocatedObjectNode allocatedObject = (AllocatedObjectNode) arg;
                                        ObjectStamp objectStamp = (ObjectStamp) allocatedObject.stamp(NodeView.DEFAULT);
                                        wsHandler = objectStamp.type().toJavaName();
                                        System.out.println("Extracted WebSocket Handler: " + wsHandler);
                                    }
                                }
                            }

                        }
                    }

                    // Store extracted data in WebsocketConnection class
                    if (uri != null || returnType != null) {
                        String parentMethod = cleanParentMethod(method.getQualifiedName());
                        websocketConnections.add(new WebsocketConnection(parentMethod, returnType, uri, isCollection, clazz.getCanonicalName(), msName, param, wsHandler));

                        // Logging
                        System.out.println("PARENT METHOD = " + parentMethod);
                        System.out.println("RETURN TYPE = " + returnType);
                        System.out.println("URI = " + uri);
                        System.out.println("IS COLLECTION = " + isCollection);
                    }
                } catch (Exception | LinkageError ex) {
                    ex.printStackTrace();
                }
            }
        } catch (Exception | LinkageError ex) {
            ex.printStackTrace();
        }
        return websocketConnections;
    }

    public static Set<WebsocketEndpoint> extractClassWebsocketEndpoints(Class<?> clazz, AnalysisMetaAccess metaAccess, Inflation bb, Map<String, Object> propMap, String msName) {
        Set<WebsocketEndpoint> websocketEndpoints = new HashSet<>();
        AnalysisType analysisType = metaAccess.lookupJavaType(clazz);

        try {
            for (AnalysisMethod method : ((AnalysisMethod[]) analysisType.getDeclaredMethods())) {
                if (method.isAbstract()) {
                    continue;
                }
                try {
                    StructuredGraph decodedGraph = ReachabilityAnalysisMethod.getDecodedGraph(bb, method);

                    String uri = null;
                    String wsHandler = null;
                    String returnType = null;

                    WebsocketParameter param = null;
                    boolean isCollection = false;
                    StringBuilder uriBuilder = new StringBuilder();
                    StringBuilder handlerBuilder = new StringBuilder();

                    // Loop through all nodes in the graph
                    for (Node node : decodedGraph.getNodes()) {
                        // Detect URI.create invocations
                        if (node instanceof Invoke) {
                            Invoke invoke = (Invoke) node;
                            AnalysisMethod targetMethod = (AnalysisMethod) invoke.getTargetMethod();

                            if (targetMethod.getQualifiedName().contains("WebSocketHandlerRegistry.addHandler")) {
                                // Log all nodes that precede this invoke node
                                Set<Node> visitedNodes = new HashSet<>();

                                // Start recursive traversal and logging
                                ExtractionResult extractionResult = logPrecedingNodesRecursiveURIandHandler(node, visitedNodes, uriBuilder, handlerBuilder);
                                uri = extractionResult.uriBuilder.toString();
                                wsHandler = extractionResult.handlerBuilder.toString();
                            }

                            // **New** check for StompEndpointRegistry.addEndpoint
                            if (targetMethod.getQualifiedName().contains("StompEndpointRegistry.addEndpoint")) {
                                // Extract the first parameter as a URI
                                CallTargetNode ct = invoke.callTarget();
                                if (!ct.arguments().isEmpty()) {
                                    uri = extractURI(ct, propMap);
                                }
                                wsHandler = "StompEndpointHandler";
                            }
                        }
                    }

                    // Store extracted data in WebsocketConnection class
                    if (uri != null || wsHandler != null) {
                        String parentMethod = cleanParentMethod(method.getQualifiedName());
                        websocketEndpoints.add(new WebsocketEndpoint(parentMethod, returnType, uri, isCollection, clazz.getCanonicalName(), msName, param, wsHandler));

                        // Logging
                        System.out.println("PARENT METHOD = " + parentMethod);
                        System.out.println("RETURN TYPE = " + wsHandler);
                        System.out.println("URI = " + uri);
                        System.out.println("IS COLLECTION = " + isCollection);
                    }
                } catch (Exception | LinkageError ex) {
                    ex.printStackTrace();
                }
            }
        } catch (Exception | LinkageError ex) {
            ex.printStackTrace();
        }
        return websocketEndpoints;
    }

    public static Set<WebsocketMessageType> extractClassWebsocketMessageTypes(Class<?> clazz, AnalysisMetaAccess metaAccess, Inflation bb, Map<String, Object> propMap, String msName) {
        Set<WebsocketMessageType> websocketMessageTypes = new HashSet<>();
        AnalysisType analysisType = metaAccess.lookupJavaType(clazz);

        try {
            for (AnalysisMethod method : ((AnalysisMethod[]) analysisType.getDeclaredMethods())) {
                if (method.isAbstract()) {
                    continue;
                }
                try {
                    StructuredGraph decodedGraph = ReachabilityAnalysisMethod.getDecodedGraph(bb, method);

                    String wsDataType = null;
                    String wsHandler = null;
                    boolean isCollection = false;
                    StringBuilder uriBuilder = new StringBuilder();
                    WebsocketParameter param = null;

                    // Loop through all nodes in the graph
                    for (Node node : decodedGraph.getNodes()) {
                        // Detect URI.create invocations
                        if (node instanceof Invoke) {
                            Invoke invoke = (Invoke) node;
                            AnalysisMethod targetMethod = (AnalysisMethod) invoke.getTargetMethod();

                            if (targetMethod.getQualifiedName().contains("StompSessionHandlerAdapter.getPayloadType")) {
                                // Retrieve the return type
                                AnalysisType retType = (AnalysisType) targetMethod.getSignature().getReturnType(null);
                                if (retType != null) {
                                    wsDataType = retType.getName();
                                    // Extract the handler name from the declaring class
                                    wsHandler = targetMethod.getDeclaringClass().getName();
                                }
                            }

                            if (targetMethod.getQualifiedName().contains("AbstractWebSocketMessage.getPayload")) {
                                System.out.println("Detected WebSocket getPayload() call");

                                // Detect the readValue() call and extract the payload type (e.g., ChatMessage)
                                for (Node nextNode : decodedGraph.getNodes()) {
                                    if (nextNode instanceof Invoke) {
                                        Invoke nextInvoke = (Invoke) nextNode;
                                        AnalysisMethod nextTargetMethod = (AnalysisMethod) nextInvoke.getTargetMethod();

                                        if (nextTargetMethod.getQualifiedName().contains("ObjectMapper.readValue")) {
                                            CallTargetNode nextCallTargetNode = nextInvoke.callTarget();
                                            NodeInputList<ValueNode> nextArguments = nextCallTargetNode.arguments();

                                            for (ValueNode arg : nextArguments) {
                                                if (arg instanceof ConstantNode) {
                                                    ConstantNode constantNode = (ConstantNode) arg;
                                                    Object payloadObject = constantNode.getValue();

                                                    if (payloadObject instanceof DirectSubstrateObjectConstant) {
                                                        DirectSubstrateObjectConstant dsoc = (DirectSubstrateObjectConstant) payloadObject;
                                                        Object actualMessage = dsoc.getObject();

                                                        if (actualMessage instanceof com.oracle.svm.core.hub.DynamicHub) {
                                                            Class<?> actualClass = extractHostedClass(actualMessage);
                                                            wsDataType = actualClass.getSimpleName();
                                                            System.out.println("Actual message type: " + wsDataType);
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    wsHandler = clazz.getSimpleName();

                    // Store extracted data in WebsocketConnection class
                    if (wsHandler != null && wsDataType != null) {
                        String parentMethod = cleanParentMethod(method.getQualifiedName());
                        websocketMessageTypes.add(new WebsocketMessageType(wsDataType, msName, wsHandler, parentMethod, isCollection, clazz.getCanonicalName(), param));

                        // Logging
                        System.out.println("PARENT METHOD = " + parentMethod);
                        System.out.println("RETURN TYPE = " + wsHandler);
                        System.out.println("IS COLLECTION = " + isCollection);
                    }
                } catch (Exception | LinkageError ex) {
                    ex.printStackTrace();
                }
            }
        } catch (Exception | LinkageError ex) {
            ex.printStackTrace();
        }
        return websocketMessageTypes;
    }


    private static WebsocketParameter setIfBodyAndType(WebsocketParameter param, CallTargetNode node) {
        for (ValueNode arg : node.arguments()) {
            if (arg instanceof PiNode) {
                for (Node inputNode : ((PiNode) arg).inputs()) {
                    if (inputNode instanceof Invoke) {
                        param = setIfBodyAndType(param, ((Invoke) inputNode).callTarget());
                    }
                }
            } else if (arg instanceof Invoke) {
                param = handleIfInvokeInWebsocketParam(param, arg);
            } else {
            }
        }
        return param;
    }

    //nodes passed into here are only if they are instanceof Invoke
    private static WebsocketParameter handleIfInvokeInWebsocketParam(WebsocketParameter param, ValueNode node) {
//         System.out.println("\targ is an invoke and = " + node);
        for (Node inNode : node.inputs()) {
//             System.out.println("\t\tinvoke input = " + inNode);
            if (inNode instanceof Invoke) {
                param = handleIfInvokeInWebsocketParam(param, ((ValueNode) inNode));
            }
        }
//         System.out.println("\t\tpredecessor = " + node.predecessor() + ", class = " + node.predecessor().getClass());
        Node predecessor = node.predecessor();
        if (predecessor instanceof BeginNode && predecessor.predecessor() instanceof Invoke) {
//             System.out.println("\t\t\tpredecessor instance of Begin and predecessor.BeginNode is an invoke");
//             System.out.println("\t\t\tpredecessor of BeginNode = " + predecessor.predecessor());
            Node bNodePredecessor = predecessor.predecessor();

            if (((Invoke) predecessor.predecessor()).callTarget().targetMethod().toString().contains(WebsocketCallExtraction.WEBSOCKET_TEXT_MESSAGE)) {
//                 System.out.println("callTarget = " + ((Invoke)predecessor.predecessor()).callTarget());
                int inputAmnt = 0;
                for (Node ctIn : ((Invoke) predecessor.predecessor()).callTarget().inputs()) {
//                     System.out.println("ctIn = " + ctIn);
                    inputAmnt++;
                }

                //if virtualnode(?) has a zero but Allocated node has 3 inputs, there is a body with param. Seems there is always two inputs by default. Whatever the inputs minus 2 is how many params I think
                int paramCount = inputAmnt - 2;
                if (paramCount > 0) {
                    param.setParamCount(param.getParamCount() + paramCount);
                    param.setIsBody(true);
                }
            }

            param = setIfBodyAndType(param, ((Invoke) predecessor.predecessor()).callTarget());
        } else {
            param = setIfBodyAndType(param, ((Invoke) node).callTarget());
        }
        return param;
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

                for (java.lang.annotation.Annotation annotation : field.getWrapped().getAnnotations()) {
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
                if (cn.asJavaConstant() != null && cn.asJavaConstant().isNull()) {
                    // Skip, it's null
                } else if (!(cn.getValue() instanceof PrimitiveConstant)) {
                    DirectSubstrateObjectConstant dsoc = (DirectSubstrateObjectConstant) cn.getValue();
                    if (dsoc.getObject() != null) {
                        uriPortion += dsoc.getObject().toString();
                    }
                }
            }
            else if (arg instanceof AllocatedObjectNode) {
                // Handle allocated objects, which may contain constant values
                AllocatedObjectNode allocatedObject = (AllocatedObjectNode) arg;
                for (Node input : allocatedObject.inputs()) {
                    if (input instanceof CommitAllocationNode) {
                        CommitAllocationNode varr = (CommitAllocationNode) input;
                        for (ValueNode element : varr.getValues()) {

                            if (element instanceof ConstantNode) {
                                ConstantNode cn = (ConstantNode) element;
                                // PrimitiveConstants cannot be converted to DirectSubstrateObjectConstant
                                if (cn.asJavaConstant() != null && cn.asJavaConstant().isNull()) {
                                    // Skip, it's null
                                } else if (!(cn.getValue() instanceof PrimitiveConstant)) {
                                    DirectSubstrateObjectConstant dsoc = (DirectSubstrateObjectConstant) cn.getValue();
                                    if (dsoc.getObject() != null) {
                                        uriPortion += dsoc.getObject().toString();
                                    }
                                }
                            }
                        }
                    }
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
