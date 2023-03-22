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

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaField;

public class EntityExtraction {

    private final static String ENTITY_PACKAGE = "@javax.persistence.";
    private final static String PRIMITIVE_VALUE = "HotSpotResolvedPrimitiveType<";
    private final static String LOMBOK_ANNOTATION = "@Data";

    public static Optional<Entity> extractClassEntityCalls(Class<?> clazz, AnalysisMetaAccess metaAccess, Inflation bb) {
        Entity ent = null;
        HashMap<String, Field> fieldMap = new HashMap<>();
        AnalysisType analysisType = metaAccess.lookupJavaType(clazz);

        try {
            for (AnalysisField field : analysisType.getInstanceFields(false)) {

                String fieldName = field.getName();

                try {
                    // Spring
                    if (field.getWrapped().getAnnotations().length > 0 || isLombok(analysisType)) {
                        
                        String typeName = field.getWrapped().getType().toString();
                        //Handles HotSpotType and HotSpotResolvedPrimitiveType
                        if(typeName.contains("/") && typeName.contains(";")){
                            typeName = typeName.substring(typeName.lastIndexOf("/") + 1, typeName.indexOf(";"));
                        }else if(typeName.contains(PRIMITIVE_VALUE)){
                            typeName = typeName.replace(PRIMITIVE_VALUE, "");
                            typeName = typeName.replace(">", "");
                        }

                        fieldMap.putIfAbsent(fieldName, new Field(typeName, new Name(fieldName)));
                        Set<com.oracle.svm.hosted.prophet.model.Annotation> annotationsSet = new HashSet<>();
                        if(isLombok(analysisType)){
                            ent = new Entity(new Name(clazz.getSimpleName()));
                        }

                        for (Annotation ann : field.getWrapped().getAnnotations()) {

                            if(ann.toString().startsWith(ENTITY_PACKAGE)){
                                //Create new entity if it does not exist
                                if (ent == null) {
                                    ent = new Entity(new Name(clazz.getSimpleName()));
                                }
                                //Create a new annotation and set it's name
                                com.oracle.svm.hosted.prophet.model.Annotation tempAnnot = new com.oracle.svm.hosted.prophet.model.Annotation();
                                String annName = ann.toString();

                                //String manipulation for annotation names
                                if(annName.contains(ENTITY_PACKAGE)){
                                    annName = annName.replace(ENTITY_PACKAGE, "");
                                    annName = "@" + annName;
                                    annName = annName.substring(0, annName.indexOf("("));
                                }
                                tempAnnot.setName(annName);

                                //Add it to the set
                                annotationsSet.add(tempAnnot);
                            }
                        }
                        //Add the annotation set to the field and put it in the map
                        Field updatedField = fieldMap.get(fieldName);
                        updatedField.setAnnotations(annotationsSet);
                        fieldMap.put(fieldName, updatedField);
                    }
                } catch (Exception | LinkageError ex) {
                    ex.printStackTrace();
                }
            }
            if (ent != null) {
                //if (!fieldMap.values().isEmpty())
                    ent.setFields(new HashSet<>(fieldMap.values()));
            }

            return Optional.ofNullable(ent);

        } catch (Exception | LinkageError ex) {
            ex.printStackTrace();
        }
        return Optional.ofNullable(ent);
    }

    //Because Lombok annotations are processed at compile-time, they are not retained in the compiled
    //class' file annotation table
    public static boolean isLombok(AnalysisType analysisType){
        
        AnalysisField[] fields = analysisType.getInstanceFields(false);
        AnalysisMethod[] methods = analysisType.getDeclaredMethods();
        boolean getFound, setFound;

        for(AnalysisField field : fields){

            getFound = false;
            setFound = false;

            for(AnalysisMethod method : methods){

                if(method.getName().toLowerCase().equals("get" + field.getName().toLowerCase())){
                    getFound = true;
                }
                if(method.getName().toLowerCase().equals("set" + field.getName().toLowerCase())){
                    setFound = true;
                }

            }

            if(!(getFound && setFound)){
                return false;
            }
        }
        return true;
    }
}