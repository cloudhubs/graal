package com.oracle.svm.hosted.prophet;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.reachability.ReachabilityAnalysisMethod;

import java.lang.annotation.Annotation;
import java.util.*;

import com.oracle.svm.hosted.prophet.model.Entity;
import com.oracle.svm.hosted.prophet.model.Field;
import com.oracle.svm.hosted.prophet.model.Name;
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

    public static Optional<Entity> extractClassEntityCalls(Class<?> clazz, AnalysisMetaAccess metaAccess, Inflation bb) {
        Entity ent = null;
        HashMap<String, Field> fieldMap = new HashMap<>();
        AnalysisType analysisType = metaAccess.lookupJavaType(clazz);
        try {
            for (AnalysisField field : analysisType.getInstanceFields(false)) {
                String fieldName = field.getName();
                try {
                    if (field.getWrapped().getAnnotations().length > 0) {

                        // add field
                        fieldMap.putIfAbsent(fieldName, new Field("", new Name(fieldName)));

                        // get annotations
                        for (Annotation ann : field.getWrapped().getAnnotations()) {

                            // check if it is an entity annotation
                            if (ann.toString().startsWith(ENTITY_PACKAGE)) {

                                // create entity if its null
                                if (ent == null) {
                                    ent = new Entity(new Name(clazz.getSimpleName()));
                                }

                                // fetch the Field
                                Field newField = fieldMap.get(fieldName);

                                // add annotation to field
                                Set<com.oracle.svm.hosted.prophet.model.Annotation> annotationsSet = newField.getAnnotations();
                                com.oracle.svm.hosted.prophet.model.Annotation tempAnnot = new com.oracle.svm.hosted.prophet.model.Annotation();
                                tempAnnot.setName(ann.toString());
                                annotationsSet.add(tempAnnot);
                                newField.setAnnotations(annotationsSet);

                                // replace the old field
                                fieldMap.put(fieldName, newField);

                            }
                        }
                    }
                } catch (Exception | LinkageError ex) {
                    ex.printStackTrace();
                }
            }

            System.out.println("FIELDS: " + fieldMap.values());

            if (ent != null) {
                if (!fieldMap.values().isEmpty())
                    ent.setFields(new HashSet<>(fieldMap.values()));
            }

            return Optional.ofNullable(ent);

        } catch (Exception | LinkageError ex) {
            ex.printStackTrace();
        }
        return Optional.ofNullable(ent);
    }
}