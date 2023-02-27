package com.oracle.svm.hosted.prophet;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.reachability.ReachabilityAnalysisMethod;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.options.Option;
import com.oracle.svm.hosted.analysis.Inflation;

public class EntityExtraction {

    private final static String ENTITY_PACKAGE = "javax.persistence";

    public static void extractClassEntityCalls(Class<?> clazz, AnalysisMetaAccess metaAccess, Inflation bb) {
        System.out.println("IN ENTITY GRAAL");
        AnalysisType analysisType = metaAccess.lookupJavaType(clazz);
        try {
            for (AnalysisField field : analysisType.getInstanceFields(false)) {
                try {
                    System.out.println(field.getAnnotationRoot());
//                    for (Node node : field.getAnnotations()) {
//                        if (node instanceof Invoke) {
//                            Invoke invoke = (Invoke) node;
//                            AnalysisMethod targetMethod = ((AnalysisMethod) invoke.getTargetMethod());
//                            if (targetMethod.getQualifiedName().startsWith(ENTITY_PACKAGE)) {
//                                System.out.println("ENTITY NAME: " + targetMethod.getQualifiedName());
//                            }
//                        }
//                    }
                }
                catch (Exception | LinkageError ex) {
                    ex.printStackTrace();
                }
            }
        }
        catch (Exception | LinkageError ex) {
            ex.printStackTrace();
        }
    }
}
