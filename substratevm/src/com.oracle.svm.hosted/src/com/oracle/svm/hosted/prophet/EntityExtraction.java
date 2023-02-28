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

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import com.oracle.svm.util.AnnotationWrapper;

public class EntityExtraction {

    private final static String ENTITY_PACKAGE = "javax.persistence";

    public static void extractClassEntityCalls(Class<?> clazz, AnalysisMetaAccess metaAccess, Inflation bb) {
//        System.out.println("IN ENTITY GRAAL");
        AnalysisType analysisType = metaAccess.lookupJavaType(clazz);
        try {
            for (AnalysisField field : analysisType.getInstanceFields(false)) {
                try {
                    System.out.println("~~~Field~~~");
                    System.out.println(field.toString());

                    Annotation[] annotations = field.getWrapped().getAnnotations();
                    for(Annotation ann : annotations){
                        System.out.println("+++Annotation+++");
                        System.out.println(ann.toString());
                        System.out.println("+++Annotation+++\n");
                    }
                    System.out.println("~~~Field~~~\n");
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