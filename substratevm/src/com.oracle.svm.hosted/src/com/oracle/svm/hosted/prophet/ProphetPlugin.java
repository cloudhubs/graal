package com.oracle.svm.hosted.prophet;
import com.oracle.svm.hosted.prophet.EndpointExtraction;
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
import com.oracle.svm.hosted.prophet.RestCallExtraction;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.options.Option;
import jdk.vm.ci.meta.ResolvedJavaMethod.Parameter;

import java.net.URL;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;

import com.oracle.svm.hosted.prophet.Logger;

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
    private static final Logger logger = Logger.loggerFor(ProphetPlugin.class);
    private final Set<String> relationAnnotationNames = new HashSet<>(Arrays.asList("ManyToOne", "OneToMany", "OneToOne", "ManyToMany"));
    private Map<String, Object> propMap;

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
        URL enumeration = loader.getClassLoader().getResource("application.yml");
        try {
            this.propMap = new org.yaml.snakeyaml.Yaml().load(new FileReader(enumeration.getFile()));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        var classes = filterRelevantClasses();
        return processClasses(classes);
    }

    private Module processClasses(List<Class<?>> classes) {
        var entities = new HashSet<Entity>();
        logger.info("Amount of classes = " + classes.size());
        for (Class<?> clazz : classes) {
            if (extractRestCalls){
                EndpointExtraction.extractEndpoints(clazz, metaAccess, bb);
                RestCallExtraction.extractClassRestCalls(clazz, metaAccess, bb, this.propMap);
            }
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

    // private void dumpAllClasses() {
    //     logger.debug("---All app classes---");
    //     allClasses.forEach(System.out::println);
    //     logger.debug("---------------------");
    // }

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
            if (applicationClass.getName().startsWith("edu.baylor.ecs.cms")){
                System.out.println("app class name = " + applicationClass.getName());
            }
            if (applicationClass.getName().startsWith(basePackage) && !applicationClass.isInterface())
                res.add(applicationClass);
        }
        return res;
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
}
