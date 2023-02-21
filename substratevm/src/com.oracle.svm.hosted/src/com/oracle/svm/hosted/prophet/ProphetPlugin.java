package com.oracle.svm.hosted.prophet;

import com.oracle.graal.pointsto.meta.AnalysisField;
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
import com.oracle.svm.hosted.prophet.model.Component;
import com.oracle.svm.hosted.prophet.model.Controller;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.virtual.AllocatedObjectNode;
import org.graalvm.compiler.options.Option;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.lang.reflect.Method;
import java.lang.annotation.Annotation;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.virtual.AllocatedObjectNode;
import org.graalvm.compiler.options.Option;
import java.lang.reflect.Method;
import java.lang.annotation.Annotation;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import javax.crypto.CipherInputStream;

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

    private Map<String, Object> propMap;
    private final Set<String> relationAnnotationNames = new HashSet<>(Arrays.asList("ManyToOne", "OneToMany", "OneToOne", "ManyToMany"));
    private static final Logger logger = Logger.loggerFor(ProphetPlugin.class);

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
    public static void run(ImageClassLoader loader, AnalysisUniverse aUniverse, AnalysisMetaAccess metaAccess, Inflation bb) {
        String basePackage = Options.ProphetBasePackage.getValue();
        String modulename = Options.ProphetModuleName.getValue();
        Boolean extractRestCalls = Options.ProphetRest.getValue();
        logger.info("Running Prophet plugin :)");
        logger.info("Analyzing all classes in the " + basePackage + " package.");
        logger.info("Creating module " + modulename);

        var plugin = new ProphetPlugin(loader, aUniverse, metaAccess, bb, basePackage, modulename, extractRestCalls);
        Module module = plugin.doRun();
        dumpModule(module);
    }

    private Module doRun() {
        URL enumeration = loader.getClassLoader().getResource("application.yml");
        try {
            this.propMap = new org.yaml.snakeyaml.Yaml().load(new FileReader(enumeration.getFile()));
            // System.out.println(this.propMap);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        var classes = filterRelevantClasses();
        return processClasses(classes);
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
    private Module processClasses(List<Class<?>> classes) {
        var entities = new HashSet<Entity>();

        //REST CALL EXTRACTION
        for (Class<?> clazz : classes) {
            if (extractRestCalls)
                processMethods(clazz);
        }
        // Entity Extraction
        for (Class<?> clazz : classes) {
            Annotation[] annotations = clazz.getAnnotations();
            for (Annotation ann : annotations) {
                if (ann.annotationType().getName().startsWith("javax.persistence.Entity")) {
                    Entity entity = processEntity(clazz, ann);
                    entities.add(entity);
                }
            }
        }
        return new Module(new Name(modulename), entities);
    }
    
    private void processMethods(Class<?> clazz) {
        AnalysisType analysisType = metaAccess.lookupJavaType(clazz);
        try {
            for (AnalysisMethod method : analysisType.getDeclaredMethods()) {
                if (!method.getQualifiedName().contains("EmsService.getExams")) {
                    continue;
                }
                try {

                    /*
                     * General idea:
                     * 1) find rest invoke
                     * 2) check the first argument - ip address
                     * 3) if a string - done
                     *  if not a string - then it should be an stringbuilder.toString invoke
                     *  4) find the receiver of the toString invocation
                     *  5) filter all its usages to find the .append calls
                     *  6) find the arguments to the append calls
                     *  7) try to extract the string constant for each
                     *       should be either
                     *         a) direct string/char
                     *         b) param passed into the method  (for configurable uris like /productis/{id}/smth)
                     *         c) loadfield
                     *              if a loadfield, try to extract its  @Value annottion and load the value from config file
                     */

                    StructuredGraph decodedGraph = ReachabilityAnalysisMethod.getDecodedGraph(bb, method);
                    for (Node node : decodedGraph.getNodes()) {
                        if (node instanceof Invoke) {
                            Invoke invoke = (Invoke) node;
                            AnalysisMethod targetMethod = ((AnalysisMethod) invoke.getTargetMethod());
                            if (targetMethod.getQualifiedName().startsWith("org.springframework.web.client.RestTemplate")) {
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
                                    // todo assert it is really a toString invocation
                                    AllocatedObjectNode toStringReceiver = (AllocatedObjectNode) callTarget.arguments().get(0);
                                    System.out.println("ToString receiver: " + toStringReceiver);
                                    StringBuilder stringBuilder = new StringBuilder();
                                    for (Node usage : toStringReceiver.usages()) {
                                        System.out.println("\t usage : " + usage);
                                        if (usage instanceof CallTargetNode) {
                                            CallTargetNode usageAsCallTarget = (CallTargetNode) usage;
                                            AnalysisMethod m = ((AnalysisMethod) usageAsCallTarget.targetMethod());
                                            if (m.getQualifiedName().startsWith("java.lang.AbstractStringBuilder.append")) {
                                                System.out.println("\t\t is a calltarget to " + m);
                                                ValueNode fstArg = usageAsCallTarget.arguments().get(1);
                                                System.out.println("\t\t" + fstArg);
                                                if (fstArg instanceof LoadFieldNode) {
                                                    System.out.println("\t\t\tload field " + fstArg);
                                                    LoadFieldNode loadfieldNode = (LoadFieldNode) fstArg;
                                                    AnalysisField field = (AnalysisField) loadfieldNode.field();
                                                    for (Annotation annotation : field.getAnnotations()) {
                                                        if (annotation.annotationType().getName().contains("Value")) {
                                                            System.out.println("\t\t\tLoad field with value annotation");
                                                            Method valueMethod = annotation.annotationType().getMethod("value");
                                                            String propTemplate = ((String) valueMethod.invoke(annotation));
                                                            System.out.println("\t\t\textracted value: " + propTemplate);
                                                            String res = tryResolve(propTemplate);
                                                            System.out.println("\t\t\t\t resolved: " + res);
                                                            if (res != null) {
                                                                stringBuilder.append(res);
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    System.out.println("Concatenated url: " + stringBuilder.toString());
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

    private String tryResolve(String expr) {
        String mergedKey = expr.substring(2, expr.length() - 1);
        String[] path = mergedKey.split("\\.");
        var curr = this.propMap;
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
                curr = ((Map<String, Object>) value);
            }
        }
        return null;
    }




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
    private Module processControllerClasses(List<Class<?>> classes){
        System.out.println("beginnning processing Controllers");
        Set<Controller> controllers = processControllers(classes);
        System.out.println("processedControllers");
        controllers.forEach(System.out::println);
        return new Module(new Name(modulename), controllers);
    }
    private Module processServiceClasses(List<Class<?>> classes){
        System.out.println("beginnning processing Services");
        Set<Service> services = processService(classes);
        System.out.println("processed Services");
        services.forEach(System.out::println);
        return new Module(new Name(modulename), services);
    }
    private Service processService(Class<?> clazz) {
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

        Set<Method> methods = new HashSet<>();
        for (java.lang.reflect.Method meth : clazz.getDeclaredMethods()) {
            methods.add(meth);
        }

        s.setServiceFields(fields);
        s.setServiceMethods(methods);

        return s;
    }
    private Set<Controller> processControllers(List<Class<?>> classes){
        Set<Controller> controllers = new HashSet<Controller>();
        for(Class<?> clazz : classes){
            Controller c = new Controller();
            boolean serv = false;
            Annotation[] annotations = clazz.getAnnotations();
            for (Annotation ann : annotations){
                if(ann.toString().toLowerCase().contains("controller")){
                    c.setClass(clazz);
                    serv = true;
                }
            }
            if(serv){
                Method[] methods = clazz.getMethods();
                for (Method m : methods){
                    c.addMethod(m);
                }
                java.lang.reflect.Field[] fields = clazz.getDeclaredFields();
                for (java.lang.reflect.Field f : fields){
                    c.addField(f);
                }
            }
            if (c.getControllerClass() != null){
                controllers.add(c);
            }
        }
        return controllers;
    }
}



