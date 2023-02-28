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
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.options.Option;
import jdk.vm.ci.meta.ResolvedJavaMethod.Parameter;

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
        var entities = new HashSet<Entity>();
        for (Class<?> clazz : classes) {
            if (extractRestCalls)
                processMethods(clazz);
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

    //annotations for controller to get endpoints
    private final Set<String> controllerAnnotationNames = new HashSet<>(Arrays.asList("GetMapping", "PutMapping", "DeleteMapping", "PostMapping"));
    private void processMethods(Class<?> clazz) {
        AnalysisType analysisType = metaAccess.lookupJavaType(clazz);
        try {
            for (AnalysisMethod method : analysisType.getDeclaredMethods()) {
                try {

                    // System.out.println("============");

                    // //Code to get the returnType attribute:
                    


                    // //Code to get the parentMethod attribute:
                    // Parameter[] params = method.getParameters();
                    // System.out.println("Annotations for Method: " + method.getQualifiedName());
                    // String parentMethod = null;
                    // //following the rad-source format for the parentMethod JSON need to parse before the first parenthesis
                    // parentMethod = method.getQualifiedName().substring(0,method.getQualifiedName().indexOf("("));
                    // System.out.println("PARENTSS:: " + parentMethod);



                    // //Code to get the argument attribute:
                    // // Example: "arguments": "[@PathVariable Integer id]",
                    // Annotation[][] annotations1 = method.getParameterAnnotations();
                    // ArrayList<String> parameterAnnotationsList = new ArrayList<>();
                    // for (int i = 0; i < annotations1.length; i++) {
                    //     Annotation[] annotations2 = annotations1[i];
                    //     for (int j = 0; j < annotations2.length; j++) {
                    //         Annotation annotation = annotations2[j];
                    //         //System.out.println("THIS MIGHT BE THE PARAMETER ANNOTATION BRUH: " + annotation.annotationType().getSimpleName());
                    //         parameterAnnotationsList.add("@" + annotation.annotationType().getSimpleName());
                    //     }
                    // }

                    // if(parameterAnnotationsList.size() > 0){
                    //     int j = 0;
                    //     for(Parameter p: params){
                    //         String parameterType = p.getParameterizedType().toString();
                    //         parameterAnnotationsList.set(j, parameterAnnotationsList.get(j) + " " + 
                    //         parameterType.substring(parameterType.lastIndexOf(".")+1,parameterType.length()) + " " +
                    //         p.getName());

                    //         j++;
                    //         //System.out.println("NAME: " + p.getName() + " ---- PARAMETERIZED TYPE: " + p.getParameterizedType());
                    //     }

                    //     for(String value : parameterAnnotationsList){
                    //         System.out.println("argument: " + value);
                    //     }
                    // }

                    // System.out.println("============");

                    

                    // //TODO: need to get the root endpoint
                    // // What I will need to extract; String httpMethod, String parentMethod, String arguments, String returnType
                    Annotation[] annotations = method.getAnnotations();
                    for (Annotation annotation : annotations) {
                        
                        if (controllerAnnotationNames.contains(annotation.annotationType().getSimpleName())) {
                            String httpMethod = null, path = null;
                            if (annotation.annotationType().getName().startsWith("org.springframework.web.bind.annotation.PutMapping")) {
                                path = ((String[]) annotation.annotationType().getMethod("value").invoke(annotation))[0];
                                httpMethod = "PUT";
                            } else if (annotation.annotationType().getName().startsWith("org.springframework.web.bind.annotation.GetMapping")) {
                                path = ((String[]) annotation.annotationType().getMethod("value").invoke(annotation))[0];
                                httpMethod = "GET";
                            } else if (annotation.annotationType().getName().startsWith("org.springframework.web.bind.annotation.PostMapping")) {
                                path = ((String[]) annotation.annotationType().getMethod("value").invoke(annotation))[0];
                                httpMethod = "POST";
                            } else if (annotation.annotationType().getName().startsWith("org.springframework.web.bind.annotation.DeleteMapping")) {
                                path = ((String[]) annotation.annotationType().getMethod("value").invoke(annotation))[0];
                                httpMethod = "DELETE";
                            }


                            Class<?> returnType = annotation.returnType();
                            System.out.println("HTTP Method: " + httpMethod + ", Path: " + path + ", ReturnType: " + returnType);
                            
                            //Special case for request mapping 
                        }else if (annotation.annotationType().getSimpleName().equals("RequestMapping")){
                         
                            String[] pathArr = (String[]) annotation.annotationType().getMethod("path").invoke(annotation);
                            String path = pathArr.length > 0 ? pathArr[0] : null;

                            //cant use a string[] because of ClassCastException 
                            Object[] methods = (Object[]) annotation.annotationType().getMethod("method").invoke(annotation);
                            String httpMethod = null;
                            if (methods.length > 0 && methods != null) {
                                httpMethod = methods[0].toString();
                            }

                            Class<?> returnType = annotation.returnType();
                            System.out.println("HTTP Method: " + httpMethod + ", Path: " + path + ", ReturnType: "+ returnType);
                        }
                    }

                    StructuredGraph decodedGraph = ReachabilityAnalysisMethod.getDecodedGraph(bb, method);
                    for (Node node : decodedGraph.getNodes()) {
                        if (node instanceof Invoke) {
                            Invoke invoke = (Invoke) node;
                            AnalysisMethod targetMethod = ((AnalysisMethod) invoke.getTargetMethod());
                            if (targetMethod.getQualifiedName().startsWith("org.springframework.web.client.RestTemplate")) {
                                System.out.println("Method Qualified Name = " + method.getQualifiedName());
                                System.out.println("Target Method Qualified Name = " + targetMethod.getQualifiedName());
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
                                        System.out.println("\targument = " + argument);
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
