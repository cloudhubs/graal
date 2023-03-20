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
import java.util.Optional;

import com.oracle.svm.hosted.prophet.Logger;

public class ProphetPlugin {

    private final ImageClassLoader loader;
    private final AnalysisUniverse universe;
    private final AnalysisMetaAccess metaAccess;
    private final Inflation bb;
    private final String modulename;
    private final String basePackage;
    private final List<Class<?>> allClasses;
    private static final Logger logger = Logger.loggerFor(ProphetPlugin.class);
    private final Set<String> relationAnnotationNames = new HashSet<>(Arrays.asList("ManyToOne", "OneToMany", "OneToOne", "ManyToMany"));
    private Map<String, Object> propMap = null;

    private final List<String> unwantedBasePackages = Arrays.asList("org.graalvm", "com.oracle", "jdk.vm");

    public ProphetPlugin(ImageClassLoader loader, AnalysisUniverse aUniverse, AnalysisMetaAccess metaAccess, Inflation bb, String basePackage, String modulename) {
        this.loader = loader;
        universe = aUniverse;
        this.metaAccess = metaAccess;
        this.bb = bb;
        this.modulename = modulename;
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
        public static final HostedOptionKey<String> ProphetBasePackage = new HostedOptionKey<>("unknown");

        @Option(help = "Module name.")//
        public static final HostedOptionKey<String> ProphetModuleName = new HostedOptionKey<>("unknown");

        @Option(help = "Where to store the analysis output?")//
        public static final HostedOptionKey<String> ProphetOutputFile = new HostedOptionKey<>(null);
    }

    public static void run(ImageClassLoader loader, AnalysisUniverse aUniverse, AnalysisMetaAccess metaAccess, Inflation bb) {
        String basePackage = Options.ProphetBasePackage.getValue();
        String modulename = Options.ProphetModuleName.getValue();

        logger.info("Running Prophet plugin");
        logger.info("Analyzing all classes in the " + basePackage + " package.");
        logger.info("Creating module " + modulename);

        var plugin = new ProphetPlugin(loader, aUniverse, metaAccess, bb, basePackage, modulename);
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
        if (enumeration != null){
            try {
                this.propMap = new org.yaml.snakeyaml.Yaml().load(new FileReader(enumeration.getFile()));
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        }

        var classes = filterRelevantClasses();
        return processClasses(classes);
    }

    private Module processClasses(List<Class<?>> classes) {
        var entities = new HashSet<Entity>();
        List<RestCall> restCallList = new ArrayList<>();
        List<Endpoint> endpointList = new ArrayList<>();

        logger.info("Amount of classes = " + classes.size());
        for (Class<?> clazz : classes) {
            Optional<Entity> ent = EntityExtraction.extractClassEntityCalls(clazz, metaAccess, bb);
            ent.ifPresent(entities::add);
            List<RestCall> restCalls = RestCallExtraction.extractClassRestCalls(clazz, metaAccess, bb, this.propMap);
            restCallList.addAll(restCalls);
            //ENDPOINT EXTRACTION HERE
            List<Endpoint> endpoints = EndpointExtraction.extractEndpoints(clazz, metaAccess, bb);
            endpointList.addAll(endpoints);
        }
        return new Module(new Name(modulename), entities, restCallList, endpointList);
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
 
    private List<Class<?>> filterClasses() {
        var res = new ArrayList<Class<?>>();
        for (Class<?> applicationClass : allClasses) {
            if (applicationClass.getName().startsWith(basePackage))
                res.add(applicationClass);
        }
        return res;
    }
}
