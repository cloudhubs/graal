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

    public static Optional<Entity> extractClassEntityCalls(Class<?> clazz, AnalysisMetaAccess metaAccess, Inflation bb) {
        Entity ent = null;
        HashMap<String, Field> fieldMap = new HashMap<>();
        AnalysisType analysisType = metaAccess.lookupJavaType(clazz);
        List<String> collectionNames = Arrays.asList("Set", "List", "Queue", "Deque", "Map", "Array");
        List<String> primitiveNames = Arrays.asList("byte", "short", "int", "long", "float", "double", "char", "boolean");
        
        try {
            for (AnalysisField field : analysisType.getInstanceFields(false)) {
                String fieldName = field.getName();
                try {
                    // Spring
                    List<Annotation> classAnns = Arrays.asList(clazz.getAnnotations());
                    if (field.getWrapped().getAnnotations().length > 0 || isLombok(analysisType, clazz)) {
                        String typeName = field.getWrapped().getType().toString();
                        //Handles HotSpotType and HotSpotResolvedPrimitiveType
                        if(typeName.contains("/") && typeName.contains(";")){
                            typeName = typeName.substring(typeName.lastIndexOf("/") + 1, typeName.indexOf(";"));
                        }else if(typeName.contains(PRIMITIVE_VALUE)){
                            typeName = typeName.replace(PRIMITIVE_VALUE, "").replace(">", "");
                        }
                        // Sets if it is a collection or reference based on type
                        if(collectionNames.contains(typeName)){
                                
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

                            fieldMap.putIfAbsent(fieldName, new Field(new Name(fieldName), elementName, null, true, field.getType().getName().substring(1), true));
                        }else{
                            fieldMap.putIfAbsent(fieldName, new Field(new Name(fieldName), typeName, null, true, field.getType().getName().substring(1), false));
                        }
                        Set<com.oracle.svm.hosted.prophet.model.Annotation> annotationsSet = new HashSet<>();
                        if(isLombok(analysisType, clazz)){
                            Name temp = new Name(clazz.getSimpleName(), analysisType.getName().substring(1));
                            ent = new Entity(temp);
                        }

                        for (Annotation ann : field.getWrapped().getAnnotations()) {

                            if(ann.toString().startsWith(ENTITY_PACKAGE)){
                                //Create new entity if it does not exist
                                if (ent == null) {
                                    Name temp = new Name(clazz.getSimpleName(), analysisType.getName().substring(1));
                                    ent = new Entity(temp);
                                }
                                //Create a new annotation and set it's name
                                com.oracle.svm.hosted.prophet.model.Annotation tempAnnot = new com.oracle.svm.hosted.prophet.model.Annotation();

                                //String manipulation for annotation names
                                String annName = ann.toString();
                                if(annName.contains(ENTITY_PACKAGE)){
                                    annName = "@" + annName.replace(ENTITY_PACKAGE, "");
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
    public static boolean isLombok(AnalysisType analysisType, Class<?> clazz){
        
        AnalysisField[] fields = analysisType.getInstanceFields(false);
        AnalysisMethod[] methods = analysisType.getDeclaredMethods();
        boolean getFound, setFound;
        
        for(AnalysisField field : fields){

            getFound = false;
            setFound = false;

            for(AnalysisMethod method : methods){
                // Checks methods generated by Lombok for Entities vs method names present
                if(method.getName().toLowerCase().equals("get" + field.getName().toLowerCase())
                || method.getName().toLowerCase().equals("is" + field.getName().toLowerCase())
                || method.getName().toLowerCase().equals(field.getName().toLowerCase())){
                    getFound = true;
                }
                if(method.getName().toLowerCase().equals("set" + field.getName().toLowerCase())
                || method.getName().toLowerCase().equals("set" + field.getName().toLowerCase().replace("is", ""))){
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