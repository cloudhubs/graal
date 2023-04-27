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
import jdk.vm.ci.meta.ResolvedJavaType;

import java.lang.reflect.Type;
import java.lang.reflect.ParameterizedType;

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
                        // Sets if it is a collection or reference based on type
                        if(typeName.equals("Set") || typeName.equals("List") || typeName.equals("Queue")
                            || typeName.equals("Deque") || typeName.equals("Map") || typeName.equals("Array")){
                            
                                
                                java.lang.reflect.Field field2 = clazz.getDeclaredField(field.getWrapped().getName());
                                Type genericType = field2.getGenericType();
                                ParameterizedType parameterizedType = (ParameterizedType) genericType;
                                Type[] typeArguments = parameterizedType.getActualTypeArguments();
                                String elementName = typeName + "<";
                                for(Type elementType : typeArguments){
                                    int lastIndex = elementType.getTypeName().lastIndexOf('.');
                                    if(lastIndex != -1){
                                        elementName = elementName + elementType.getTypeName().substring(lastIndex + 1) + ",";
                                    }else{
                                        throw new IllegalArgumentException("ParameterizedType does not contain any periods in EntityExtraction.java");
                                    }
                                }
                                elementName = elementName.substring(0, elementName.length() - 1);
                                if(typeArguments.length != 0){
                                    elementName += ">";
                                }


                            fieldMap.putIfAbsent(fieldName, new Field(new Name(fieldName), elementName, null, true, elementName, true));
                        }else if(typeName.equals("byte") || typeName.equals("short") || typeName.equals("int") 
                            || typeName.equals("long") || typeName.equals("float") || typeName.equals("double")
                            || typeName.equals("char") || typeName.equals("boolean")){
                            fieldMap.putIfAbsent(fieldName, new Field(typeName, new Name(fieldName)));
                        }else{
                            fieldMap.putIfAbsent(fieldName, new Field(new Name(fieldName), typeName, null, true, typeName, false));
                        }
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
                if(method.getName().toLowerCase().equals("get" + field.getName().toLowerCase())
                || method.getName().toLowerCase().equals("is" + field.getName().toLowerCase())){
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