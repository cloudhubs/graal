package com.oracle.svm.hosted.prophet;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.reachability.ReachabilityAnalysisMethod;
import com.oracle.svm.core.meta.DirectSubstrateObjectConstant;
import com.oracle.svm.hosted.analysis.Inflation;
import com.oracle.svm.hosted.prophet.model.WebsocketConnection;
import com.oracle.svm.hosted.prophet.model.WebsocketParameter;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod.Parameter;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.virtual.CommitAllocationNode;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebsocketCallExtractionFull {

    /*
       NOTE:
       'msRoot' can be obtained in Utils or RAD
       'source' can be obtained in RAD repo in the RadSourceService file in generateWebsocketEntityContext method where getSourceFiles is
   */
    private final static String WEBSOCKET_CLIENT_PACKAGE = "org.springframework.web.socket.client.standard.StandardWebSocketClient";
    private final static String WEBSOCKET_TEXT_MESSAGE = "org.springframework.web.socket.TextMessage";
    private final static String WEBSOCKET_SESSION_PACKAGE = "org.springframework.web.socket.WebSocketSession";

    static String URI = "";

    private static Set<WebsocketConnection> websocketConnections = new HashSet<>();

    public static Set<WebsocketConnection> extractClassWebsocketConnection(Class<?> clazz, AnalysisMetaAccess metaAccess, Inflation bb, Map<String, Object> propMap, String msName) {
        AnalysisType analysisType = metaAccess.lookupJavaType(clazz);
        try {
            for (AnalysisMethod method : ((AnalysisMethod[]) analysisType.getDeclaredMethods())) {
                try {

                    StructuredGraph decodedGraph = ReachabilityAnalysisMethod.getDecodedGraph(bb, method);
                    for (Node node : decodedGraph.getNodes()) {
                        if (node instanceof ConstantNode) {
                            ConstantNode constantNode = (ConstantNode) node;
                            System.out.println("ConstantNode: " + constantNode);
                        } else if (node instanceof Invoke) {
                            Invoke invoke = (Invoke) node;
                            System.out.println("Invoke: " + invoke);
                        }
                    }
                    for (Node node : decodedGraph.getNodes()) {
                        if (node instanceof Invoke) {
                            Invoke invoke = (Invoke) node;
                            AnalysisMethod targetMethod = ((AnalysisMethod) invoke.getTargetMethod());

                            // Check for the specific method 'URI.create'
                            if (targetMethod.getQualifiedName().contains("URI.create")) {
                                CallTargetNode callTargetNode = invoke.callTarget();
                                NodeInputList<ValueNode> arguments = callTargetNode.arguments();

                                // Initialize a variable to store the concatenated URI string parts
                                StringBuilder uriBuilder = new StringBuilder();

                                // Extract URI components from the arguments, checking for both constants and concatenations
                                for (ValueNode arg : arguments) {
                                    if (arg instanceof ConstantNode) {
                                        ConstantNode constantNode = (ConstantNode) arg;

                                        if (constantNode.getValue() instanceof DirectSubstrateObjectConstant) {
                                            DirectSubstrateObjectConstant dsoc = (DirectSubstrateObjectConstant) constantNode.getValue();
                                            String extractedURI = dsoc.getObject().toString();
                                            uriBuilder.append(extractedURI);
                                        }
                                    }
                                    // Detect if a concatenation method is used (StringConcatHelper or StringBuilder.append)
                                    else if (arg instanceof Invoke) {
                                        Invoke argInvoke = (Invoke) arg;
                                        AnalysisMethod argTargetMethod = ((AnalysisMethod) argInvoke.getTargetMethod());

                                        if (argTargetMethod.getQualifiedName().contains("StringConcatHelper.simpleConcat")
                                                || argTargetMethod.getQualifiedName().contains("StringBuilder.append")) {
                                            // Recursively process the concatenated arguments
                                            NodeInputList<ValueNode> concatArgs = argInvoke.callTarget().arguments();
                                            for (ValueNode concatArg : concatArgs) {
                                                if (concatArg instanceof ConstantNode) {
                                                    ConstantNode concatConstant = (ConstantNode) concatArg;
                                                    if (concatConstant.getValue() instanceof DirectSubstrateObjectConstant) {
                                                        DirectSubstrateObjectConstant concatDsoc = (DirectSubstrateObjectConstant) concatConstant.getValue();
                                                        String partOfURI = concatDsoc.getObject().toString();
                                                        uriBuilder.append(partOfURI);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                String fullURI = uriBuilder.toString();
                                URI = uriBuilder.toString();
                                System.out.println("Extracted WebSocket URI (with concatenation): " + fullURI);
                            }
                        }

                        // Step to ensure that we are inside WebSocketClient.doHandshake
                        // Detect WebSocketClient.doHandshake method
                        if (node instanceof Invoke) {
                            Invoke invoke = (Invoke) node;
                            AnalysisMethod targetMethod = ((AnalysisMethod) invoke.getTargetMethod());

                            // Check for WebSocketClient.doHandshake method
                            if (targetMethod.getQualifiedName().contains("WebSocketClient.doHandshake")) {
                                System.out.println("Detected WebSocketClient.doHandshake invocation.");

                                // Look for the URI being used within the WebSocket handshake method
                                CallTargetNode callTargetNode = invoke.callTarget();
                                NodeInputList<ValueNode> handshakeArgs = callTargetNode.arguments();

                                for (ValueNode arg : handshakeArgs) {
                                    if (arg instanceof ConstantNode) {
                                        ConstantNode constantNode = (ConstantNode) arg;

                                        if (constantNode.getValue() instanceof DirectSubstrateObjectConstant) {
                                            DirectSubstrateObjectConstant dsoc = (DirectSubstrateObjectConstant) constantNode.getValue();
                                            String handshakeURI = dsoc.getObject().toString();
                                            System.out.println("WebSocket Handshake URI: " + handshakeURI);
                                        }
                                    }
                                }
                            }
                        }
                    }

                    for (Node node : decodedGraph.getNodes()) {
                        if (node instanceof Invoke) {
                            Invoke invoke = (Invoke) node;
                            AnalysisMethod targetMethod = ((AnalysisMethod) invoke.getTargetMethod());

                            // Look for `getPayload()` invocation
                            if (targetMethod.getQualifiedName().contains("AbstractWebSocketMessage.getPayload")) {
                                System.out.println("Detected WebSocket getPayload() call");

                                CallTargetNode callTargetNode = invoke.callTarget();
                                NodeInputList<ValueNode> arguments = callTargetNode.arguments();

                                // Step 2: Track the next invocation for `readValue()`
                                for (Node nextNode : decodedGraph.getNodes()) {
                                    if (nextNode instanceof Invoke) {
                                        Invoke nextInvoke = (Invoke) nextNode;
                                        AnalysisMethod nextTargetMethod = ((AnalysisMethod) nextInvoke.getTargetMethod());

                                        // Look for `ObjectMapper.readValue()` invocation
                                        if (nextTargetMethod.getQualifiedName().contains("ObjectMapper.readValue")) {
                                            System.out.println("Detected ObjectMapper.readValue() call");

                                            CallTargetNode nextCallTargetNode = nextInvoke.callTarget();
                                            NodeInputList<ValueNode> nextArguments = nextCallTargetNode.arguments();

                                            // Step 2: Extract deserialized object type (e.g., ChatMessage)
                                            for (ValueNode arg : nextArguments) {
                                                if (arg instanceof ConstantNode) {
                                                    ConstantNode constantNode = (ConstantNode) arg;
                                                    Object payloadObject = constantNode.getValue();

                                                    if (payloadObject instanceof DirectSubstrateObjectConstant) {
                                                        DirectSubstrateObjectConstant dsoc = (DirectSubstrateObjectConstant) payloadObject;
                                                        Object actualMessage = dsoc.getObject();

                                                        // Check if the object is a DynamicHub and extract the actual class type
                                                        if (actualMessage instanceof com.oracle.svm.core.hub.DynamicHub) {
                                                            System.out.println("Detected DynamicHub object");

                                                            // Step 3: Extract the hosted class (e.g., ChatMessage) from the DynamicHub
                                                            Field hostedClassField = actualMessage.getClass().getDeclaredField("hostedJavaClass");
                                                            hostedClassField.setAccessible(true);
                                                            Class<?> actualClass = (Class<?>) hostedClassField.get(actualMessage);

                                                            // Print message type (e.g., ChatMessage)
                                                            System.out.println("Actual message type: " + actualClass.getSimpleName());

                                                            // Step 4: Use reflection to list and print all fields (attributes) of the message type
                                                            Field[] fields = actualClass.getDeclaredFields();
                                                            System.out.println("Fields of " + actualClass.getSimpleName() + ":");

                                                            for (Field field : fields) {
                                                                // Make private fields accessible
                                                                field.setAccessible(true);

                                                                try {
                                                                    // Get field name (values won't be available at this stage since we're just reflecting on the class)
                                                                    System.out.println("Field: " + field.getName() + " (" + field.getType().getSimpleName() + ")");
                                                                } catch (Exception e) {
                                                                    e.printStackTrace();
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
                        }
                    }

                    for (Node node : decodedGraph.getNodes()) {

                        if (node instanceof Invoke) {
                            Invoke invoke = (Invoke) node;
                            AnalysisMethod targetMethod = ((AnalysisMethod) invoke.getTargetMethod());

                            if (targetMethod.getQualifiedName().startsWith(WEBSOCKET_CLIENT_PACKAGE)) {
                                System.out.println("===========================================");
                                System.out.println("Method qualified name: " + method.getQualifiedName());
                                System.out.println("Target method qualified name: " + targetMethod.getQualifiedName());
                                Parameter[] parameters = targetMethod.getParameters();

                                System.out.println("targetMethod.getWrapped().getName() = " + targetMethod.getWrapped().getName() + ", just the getWrapped() = " + targetMethod.getWrapped());

                                String PARENT_METHOD = cleanParentMethod(method.getQualifiedName());
                                CallTargetNode callTargetNode = invoke.callTarget();
                                System.out.println("callTargetNode = " + callTargetNode);
                                NodeInputList<ValueNode> arguments = callTargetNode.arguments();
                                System.out.println("arguments = " + arguments);
                                String URI = "";
                                String RETURN_TYPE = null;
                                Boolean callIsCollection = false;

                                for (ValueNode v : arguments) {
                                    if (v instanceof Invoke) {
                                        System.out.println("\t\tand IS an instance of Invoke");
                                        URI += extractURI(((Invoke) v).callTarget(), propMap);
                                    }
                                    //NOTE: need to find definitive way of knowing if node holds return value
                                    //return type seems to be in substratemethod prior to a invoke of restTemplate.whatevercall
                                    else if (v instanceof ConstantNode && !v.isNullConstant() && !v.isIllegalConstant()) {
                                        ConstantNode cn = (ConstantNode) v;
                                        System.out.println("CONSTANT NODE = " + cn);
                                        //EXTRACT RETURN TYPE
                                        if (cn.toString().contains("com.oracle.svm.core.hub.DynamicHub")) {
                                            Boolean returnTypeLikely = false;
                                            for (Node cnUsage : cn.usages()) {
                                                for (Node subUsage : cnUsage.usages()) {
                                                    if (subUsage instanceof Invoke && subUsage.toString().contains("StandardWebSocketClient")) {
                                                        returnTypeLikely = true;
                                                        break;
                                                    }
                                                }
                                                if (returnTypeLikely) {
                                                    break;
                                                }
                                            }
                                            if (returnTypeLikely) {
                                                DirectSubstrateObjectConstant dsoc = (DirectSubstrateObjectConstant) cn.getValue();
                                                RETURN_TYPE = dsoc.getObject().toString();
                                                callIsCollection = isCollection(RETURN_TYPE);
                                                RETURN_TYPE = cleanReturnType(RETURN_TYPE);
                                            }
                                        }
                                        //MIGHT be URI or portion of URI
                                        else {

                                            DirectSubstrateObjectConstant dsoc = (DirectSubstrateObjectConstant) cn.getValue();
                                            URI += dsoc.getObject().toString();
                                        }

                                    }
                                }

                                //TO-DO: In future try to get what type of HTTP Entity.
                                if (RETURN_TYPE == null || RETURN_TYPE.contains("String")) {
                                    RETURN_TYPE = WebsocketCallExtractionFull.WEBSOCKET_TEXT_MESSAGE;
                                }
                                WebsocketParameter param = getParamDetails(callTargetNode, URI);
                                System.out.println("Param = " + param);
                                websocketConnections.add(new WebsocketConnection(PARENT_METHOD, RETURN_TYPE, URI, callIsCollection, clazz.getCanonicalName(), msName, param));
                                System.out.println("PARENT METHOD = " + PARENT_METHOD);
                                System.out.println("RETURN TYPE = " + RETURN_TYPE);
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
        return websocketConnections;
    }

    private static WebsocketParameter getParamDetails(CallTargetNode node, String URI) {
        WebsocketParameter param = new WebsocketParameter(false, false);
        int count = 0;
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
    private static WebsocketParameter setIfBodyAndType(WebsocketParameter param, CallTargetNode node) {
//         System.out.println("Node = " + node);
//         System.out.println("Node TargetMethod = "  + node.targetMethod());
        // boolean doBodyCountCheck = false;
        // if (node.targetMethod().toString().contains(RestCallExtraction.HTTP_ENTITY_PACKAGE)){
        //     param.setIsBody(true);
        //     doBodyCountCheck = true;
        // }
        for (ValueNode arg : node.arguments()) {
//             System.out.println("arg = " + arg);
            // if (doBodyCountCheck && arg instanceof AllocatedObjectNode){
            //     //means allocated node is above it
            //     System.out.println("");
            //     System.out.println("arg is an instance of allocatedobjectnode = " + ((AllocatedObjectNode)arg));
            //     System.out.println("virtual object = " + ((AllocatedObjectNode)arg).getVirtualObject());

            // }
            // else 
            if (arg instanceof PiNode) {
//                 System.out.println("\t" + arg + " is a PiNode");
//                 System.out.println("\tpi node inputs: " + ((PiNode)arg).inputs());
                for (Node inputNode : ((PiNode) arg).inputs()) {
//                     System.out.println("\t\tpiNode input = " + inputNode);
                    if (inputNode instanceof Invoke) {
                        param = setIfBodyAndType(param, ((Invoke) inputNode).callTarget());
                        // param =  handleIfInvokeInRESTParam(param, ((ValueNode)inputNode));
                    }
                }
            } else if (arg instanceof Invoke) {
//                 System.out.println("calling handle!");
                param = handleIfInvokeInWebsocketParam(param, arg);
            } else {
//                 System.out.println("\targ is class = " + arg.getClass());
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

            if (((Invoke) predecessor.predecessor()).callTarget().targetMethod().toString().contains(WebsocketCallExtractionFull.WEBSOCKET_TEXT_MESSAGE)) {
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
                CommitAllocationNode caNode = (CommitAllocationNode) bNodePredecessor.predecessor();


                // for (Node caNodeInput : caNode.inputs()){
                //     System.out.println("caNode input = " + caNodeInput);
                //     if (caNodeInput.toString().matches(".*VirtualInstance\\([0-9]*\\) HttpEntity")){
                //         System.out.println("match found!");
                //         System.out.println("between parentheses " + extractVirtualInstance(caNodeInput.toString()));
                //         //extract that number
                //     }
                // }

                // int httpEntityValsCount = ((CommitAllocationNode)bNodePredecessor.predecessor()).getValues().size();
                // param.setParamCount(param.getParamCount() +  httpEntityValsCount - 1);
                // param.setIsBody(true);
            }
            // for (Node inNode : bNodePredecessor.inputs()){
            //     System.out.println("\t\t\t\tinNode inputs = " + inNode);
            // if (inNode instanceof Invoke){
            //     param = handleIfInvokeInRESTParam(param, ((ValueNode)inNode));
            // }
            // }
            // System.out.println("bNodePredecessor predecessor = " + ((CommitAllocationNode)bNodePredecessor.predecessor()).getValues());
            // for (ValueNode vn : ((CommitAllocationNode)bNodePredecessor.predecessor()).getValues()){
            //     System.out.println("vn constant node = " + (ConstantNode)vn + ", value " + ((ConstantNode)vn).getValue());
            //  }
            // System.out.println("HttpEntity params");
            // param.setParamCount(param.getParamCount() + ((CommitAllocationNode)bNodePredecessor.predecessor()).getValues() - 1); //-1 because one of those is the headers

            param = setIfBodyAndType(param, ((Invoke) predecessor.predecessor()).callTarget());
        } else {
            param = setIfBodyAndType(param, ((Invoke) node).callTarget());
        }
        return param;
    }

    private static String extractVirtualInstance(String input) {
        String regex = ".*VirtualInstance\\((.*?)\\)\\s.*"; // regex pattern to match "VirtualInstance(?)"
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(input);
        if (matcher.matches()) {
            return matcher.group(1); // returns whatever is between parentheses
        }
        return null; // if there's no match
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
        String uriPortion = "";
        for (Node arg : node.arguments()) {
            if (arg instanceof ConstantNode) {
                ConstantNode cn = (ConstantNode) arg;
                if (!(cn.getValue() instanceof PrimitiveConstant)) {
                    DirectSubstrateObjectConstant dsoc = (DirectSubstrateObjectConstant) cn.getValue();
                    uriPortion += dsoc.getObject().toString();
                }
            } else if (arg instanceof Invoke) {
                uriPortion += extractURI(((Invoke) arg).callTarget(), propMap);
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
