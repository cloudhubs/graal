package com.oracle.svm.hosted.prophet;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.reachability.ReachabilityAnalysisMethod;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.analysis.Inflation;
import com.oracle.svm.hosted.prophet.model.Entity;
import com.oracle.svm.hosted.prophet.model.Field;
import com.oracle.svm.hosted.prophet.model.Module;
import com.oracle.svm.hosted.prophet.model.Name;
import com.oracle.svm.hosted.prophet.model.Service;
import com.oracle.svm.hosted.prophet.model.Controller;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.options.Option;

import java.lang.reflect.Method;
//import java.lang.reflect.Field;

import java.lang.annotation.Annotation;

import java.io.FileWriter;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// todo move to a separate module for a faster compilation ?
public class ProphetPlugin {

    private final ImageClassLoader loader;
    private final AnalysisUniverse universe;
    private final AnalysisMetaAccess metaAccess;
    private final Inflation bb;
    private final String modulename;
    private final Boolean extractRestCalls;
    private final String basePackage;
    private final List<Class<?>> allClasses;

    private final List<String> unwantedBasePackages = Arrays.asList("org.graalvm", "com.oracle", "jdk.vm");

    public ProphetPlugin(ImageClassLoader loader, AnalysisUniverse aUniverse, AnalysisMetaAccess metaAccess, Inflation bb, String basePackage, String modulename, Boolean extractRestCalls) {
        this.loader = loader;
        universe = aUniverse;
        this.metaAccess = metaAccess;
        this.bb = bb;
        this.modulename = modulename;
        this.extractRestCalls = extractRestCalls;
        this.allClasses = new ArrayList<>();
        for (Class<?> clazz : loader.getApplicationClasses()) {
            boolean comesFromWantedPackage = unwantedBasePackages.stream().noneMatch(it -> clazz.getName().startsWith(it));
            if (comesFromWantedPackage) {
                this.allClasses.add(clazz);
            }
        }
        this.basePackage = basePackage;
    }

    public static class Options {
        @Option(help = "Use NI as a prophet plugin.")//
        public static final HostedOptionKey<Boolean> ProphetPlugin = new HostedOptionKey<>(false);

        @Option(help = "Try to extract rest calls.")//
        public static final HostedOptionKey<Boolean> ProphetRest = new HostedOptionKey<>(false);

        @Option(help = "Base package to analyse.")//
        public static final HostedOptionKey<String> ProphetBasePackage = new HostedOptionKey<>("edu.baylor.ecs.cms");

        @Option(help = "Module name.")//
        public static final HostedOptionKey<String> ProphetModuleName = new HostedOptionKey<>("cms");

        @Option(help = "Where to store the analysis output?")//
        public static final HostedOptionKey<String> ProphetOutputFile = new HostedOptionKey<>(null);
    }

    private static final Logger logger = Logger.loggerFor(ProphetPlugin.class);

    public static void run(ImageClassLoader loader, AnalysisUniverse aUniverse, AnalysisMetaAccess metaAccess, Inflation bb) {
        String basePackage = Options.ProphetBasePackage.getValue();
        String modulename = Options.ProphetModuleName.getValue();
        Boolean extractRestCalls = Options.ProphetRest.getValue();
        logger.info("Running my new amazing Prophet plugin :)");
        logger.info("Analyzing all classes in the " + basePackage + " package.");
        logger.info("Creating module " + modulename);

        var plugin = new ProphetPlugin(loader, aUniverse, metaAccess, bb, basePackage, modulename, extractRestCalls);
        Module module = plugin.doRun();
        dumpModule(module);
    }

    private static void dumpModule(Module module) {
        String outputFile = Options.ProphetOutputFile.getValue();
        String serialized = JsonDump.dump(module);
        if (outputFile != null) {
            logger.info("Writing the json into the output file: " + outputFile);
            try (var writer = new FileWriter(outputFile)) {
                writer.write(serialized);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            logger.info("Writing the json to standard output:");
            System.out.println(serialized);
        }
    }

    private Module doRun() {
        var classes = filterRelevantClasses();
        return processClasses(classes);
    }

    private Module processClasses(List<Class<?>> classes) {
        Set<Controller> controllers = processControllers(classes);
        var entities = new HashSet<Entity>();
        var services = new HashSet<Service>();

        for (Class<?> clazz : classes) {
            if (extractRestCalls)
                processMethods(clazz);
        }

        // Service class parsing
        for (Class<?> clazz : classes) {
            Annotation[] annotations = clazz.getAnnotations();
            for (Annotation ann : annotations) {

                if (ann.annotationType().getName().contains("springframework") && ann.annotationType().getName().contains("Service")) {
                    Service ser = processService(clazz);
//                    System.out.println("SERVICE: " + ser);
                    services.add(ser);
                }

                if (ann.annotationType().getName().startsWith("javax.persistence.Entity")) {
                    Entity entity = processEntity(clazz, ann);
//                    System.out.println("ENTITIES: " + entity);
                    entities.add(entity);
                }
            }
        }
        return new Module(new Name(modulename), entities);
    }

    private Service processService(Class<?> clazz) {
//        System.out.println("===== Fields: " + clazz.getName() + " =====\n");

        Name serviceName = new Name(clazz.getSimpleName());
        serviceName.setFullName(clazz.getName());

        Service s = new Service(serviceName);

        Set<Field> fields = new HashSet<>();
        for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {

            // create new field
            Field field = new Field();
            field.setName(new Name(f.getName()));
            field.setType(f.getType().getSimpleName());

            // adding field annotations to field
            Set<com.oracle.svm.hosted.prophet.model.Annotation> annots = new HashSet<>();
            for (Annotation annot : f.getDeclaredAnnotations()) {
                com.oracle.svm.hosted.prophet.model.Annotation newAnnot = new com.oracle.svm.hosted.prophet.model.Annotation();
                newAnnot.setStringValue(annot.annotationType().getSimpleName());
                newAnnot.setName("@" + annot.annotationType().getSimpleName());
                annots.add(newAnnot);
            }
            field.setAnnotations(annots);
            fields.add(field);
        }
//        System.out.println("\n===== END Fields =====\n");

//        System.out.println("===== Methods: " + clazz.getName() + " =====\n");
        Set<Method> methods = new HashSet<>();
        for (java.lang.reflect.Method meth : clazz.getDeclaredMethods()) {
            methods.add(meth);
        }
//        System.out.println("\n===== END Methods =====\n");

        s.setServiceFields(fields);
        s.setServiceMethods(methods);

        return s;
    }
    
    private Set<Controller> processControllers(List<Class<?>> classes){
        Set<Controller> controllers = new HashSet<Controller>();
        for(Class<?> clazz : classes){
            Controller c = new Controller();
            c.setClass(clazz);
            boolean serv = false;
            Annotation[] annotations = clazz.getAnnotations();
            for (Annotation ann : annotations){
                if(ann.toString().toLowerCase().contains("controller")){
                    //System.out.println("*Class*: " + clazz.getName() + " | " + ann.toString());
                    serv = true;
                }
            }
            if(serv){
                Method[] methods = clazz.getMethods();
                for (Method m : methods){
                    c.addMethod(m);
                    /*annotations = m.getDeclaredAnnotations();
                    for (Annotation ann : annotations){
                        System.out.println("*Method*: " + m.getName() + " | " + ann.toString());
                    }*/
                }
                java.lang.reflect.Field[] fields = clazz.getDeclaredFields();
                for (java.lang.reflect.Field f : fields){
                    c.addField(f);
                    /*annotations = f.getDeclaredAnnotations();
                    for (Annotation ann : annotations){
                        System.out.println("*Field*: " + f.getName() + " | " + ann.toString());
                    }*/
                }
            }
            controllers.add(c);
        }
        return controllers;
    }

    private void processMethods(Class<?> clazz) {
//        System.out.println("HELLO WERE IN DAVIDS METHOD");
        AnalysisType analysisType = metaAccess.lookupJavaType(clazz);
        try {
            for (AnalysisMethod method : analysisType.getDeclaredMethods()) {
                try {
                    StructuredGraph decodedGraph = ReachabilityAnalysisMethod.getDecodedGraph(bb, method);
                    for (Node node : decodedGraph.getNodes()) {
//                        System.out.println("NODE: " + node.toString());
                        if (node instanceof Invoke) {
                            Invoke invoke = (Invoke) node;
                            AnalysisMethod targetMethod = ((AnalysisMethod) invoke.getTargetMethod());
                            if (targetMethod.getQualifiedName().startsWith("org.springframework.web.client.RestTemplate")) {
//                                System.out.println("NODE: " + node.toString() + " " + targetMethod.getQualifiedName());
                                System.out.println(method.getQualifiedName());
                                System.out.println(targetMethod.getQualifiedName());
                                CallTargetNode callTargetNode = invoke.callTarget();
                                NodeInputList<ValueNode> arguments = callTargetNode.arguments();
                                ValueNode zero = arguments.get(0);
                                ValueNode one = arguments.get(1);
                                if (one instanceof InvokeWithExceptionNode) {
                                    // todo figure out when this does not work
                                    System.out.println("\tFirst arg is invoke:");
                                    CallTargetNode callTarget = ((InvokeWithExceptionNode) one).callTarget();
                                    System.out.println(callTarget.targetMethod());
                                    System.out.println("\targs:");
                                    for (ValueNode argument : callTarget.arguments()) {
                                        System.out.println("\t" + argument);
                                    }
                                }
                                System.out.println(zero + " " + one);
                                System.out.println("===");
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
    }

    private void dumpAllClasses() {
        logger.debug("---All app classes---");
        allClasses.forEach(System.out::println);
        logger.debug("---------------------");
    }

    private Set<Entity> filterEntityClasses(List<Class<?>> classes) {
        var entities = new HashSet<Entity>();
        for (Class<?> clazz : classes) {
            Annotation[] annotations = clazz.getAnnotations();
            for (Annotation ann : annotations) {
                if (ann.annotationType().getName().startsWith("javax.persistence.Entity")) {
                    Entity entity = processEntity(clazz, ann);
                    entities.add(entity);
                }
            }
        }
        return entities;
    }

    private List<Class<?>> filterRelevantClasses() {
        var res = new ArrayList<Class<?>>();
        for (Class<?> applicationClass : allClasses) {
            if (applicationClass.getName().startsWith(basePackage))
                res.add(applicationClass);
        }
        return res;
    }

    private final Set<String> relationAnnotationNames = new HashSet<>(Arrays.asList("ManyToOne", "OneToMany", "OneToOne", "ManyToMany"));

    private Entity processEntity(Class<?> clazz, Annotation ann) {
        var fields = new HashSet<Field>();
        for (java.lang.reflect.Field declaredField : clazz.getDeclaredFields()) {
            Field field = new Field();
            field.setName(new Name(declaredField.getName()));
            if (isCollection(declaredField.getType())) {
                Type nested = ((ParameterizedType) declaredField.getGenericType()).getActualTypeArguments()[0];
                field.setType(((Class<?>) nested).getSimpleName());
                field.setCollection(true);
            } else {
                field.setType(declaredField.getType().getSimpleName());
                field.setCollection(false);
            }

            var annotations = new HashSet<com.oracle.svm.hosted.prophet.model.Annotation>();
            for (Annotation declaredAnnotation : declaredField.getAnnotations()) {
                var annotation = new com.oracle.svm.hosted.prophet.model.Annotation();
                annotation.setStringValue(declaredAnnotation.annotationType().getSimpleName());
                annotation.setName("@" + declaredAnnotation.annotationType().getSimpleName());
                annotations.add(annotation);

                if (relationAnnotationNames.stream().anyMatch(it -> annotation.getName().contains(it))) {
                    field.setReference(true);
                    field.setEntityRefName(field.getType());
                }
            }
            field.setAnnotations(annotations);
            fields.add(field);
        }
        Entity entity = new Entity(new Name(clazz.getSimpleName()));
        entity.setFields(fields);

        return entity;
    }

    private List<Class<?>> filterClasses() {
        var res = new ArrayList<Class<?>>();
        for (Class<?> applicationClass : allClasses) {
            if (applicationClass.getName().startsWith(basePackage))
                res.add(applicationClass);
        }
        return res;
    }

    public static boolean isCollection(Class<?> type) {
        if (type.getName().contains("Set")) {
            return true;
        } else if (type.getName().contains("Collection")) {
            return true;
        } else
            return type.getName().contains("List");
    }
}
