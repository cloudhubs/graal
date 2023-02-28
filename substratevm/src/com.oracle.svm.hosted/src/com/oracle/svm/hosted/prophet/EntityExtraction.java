package com.oracle.svm.hosted.prophet;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.reachability.ReachabilityAnalysisMethod;

import java.lang.annotation.Annotation;
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
import com.oracle.svm.util.AnnotationWrapper;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import com.oracle.svm.util.AnnotationWrapper;

public class EntityExtraction {

    private final static String ENTITY_PACKAGE = "@javax.persistence";

    public static void extractClassEntityCalls(Class<?> clazz, AnalysisMetaAccess metaAccess, Inflation bb) {
        AnalysisType analysisType = metaAccess.lookupJavaType(clazz);
        try {
            for (AnalysisField field : analysisType.getInstanceFields(false)) {
                String fieldName = field.getName();
                try {
                    if (field.getWrapped().getAnnotations().length > 0) {
                        for (Annotation ann : field.getWrapped().getAnnotations()) {
                            if (ann.toString().startsWith(ENTITY_PACKAGE)) {
                                System.out.println(String.format("CLASS: %s, FIELD: %s, ANNOTATIONS: %s", clazz.getSimpleName(), fieldName, ann.toString()));
                            }
                        }
                    }
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