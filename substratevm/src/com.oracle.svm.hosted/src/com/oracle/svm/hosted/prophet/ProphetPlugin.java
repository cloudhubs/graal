package com.oracle.svm.hosted.prophet;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.oracle.svm.hosted.prophet.model.WebsocketConnection;
import com.oracle.svm.hosted.prophet.model.WebsocketEndpoint;
import com.oracle.svm.hosted.prophet.model.WebsocketMessageType;
import org.graalvm.compiler.options.Option;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.analysis.Inflation;
import com.oracle.svm.hosted.prophet.model.Endpoint;
import com.oracle.svm.hosted.prophet.model.Entity;
import com.oracle.svm.hosted.prophet.model.Module;
import com.oracle.svm.hosted.prophet.model.Name;
import com.oracle.svm.hosted.prophet.model.RestCall;

public class ProphetPlugin {

    private final ImageClassLoader loader;
    private final AnalysisUniverse universe;
    private final AnalysisMetaAccess metaAccess;
    private final Inflation bb;
    private final String msName;
    private final String basePackage;
    private final List<Class<?>> allClasses;
    private static final Logger logger = Logger.loggerFor(ProphetPlugin.class);
    private final Set<String> relationAnnotationNames = new HashSet<>(Arrays.asList("ManyToOne", "OneToMany", "OneToOne", "ManyToMany"));
    private Map<String, Object> propMap = null;

    private final List<String> unwantedBasePackages = Arrays.asList("org.graalvm", "com.oracle", "jdk.vm");

    public ProphetPlugin(ImageClassLoader loader, AnalysisUniverse aUniverse, AnalysisMetaAccess metaAccess, Inflation bb, String basePackage, String msName) {
        this.loader = loader;
        universe = aUniverse;
        this.metaAccess = metaAccess;
        this.bb = bb;
        this.msName = msName;
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

        @Option(help = "Base package to analyse.")//
        public static final HostedOptionKey<String> ProphetBasePackage = new HostedOptionKey<>("unknown");

        @Option(help = "Microservice name.")//
        public static final HostedOptionKey<String> ProphetMicroserviceName = new HostedOptionKey<>("unknown");

        @Option(help = "Where to store the entity analysis")//
        public static final HostedOptionKey<String> ProphetEntityOutputFile = new HostedOptionKey<>(null);

        @Option(help = "Where to store the restcall output")//
        public static final HostedOptionKey<String> ProphetRestCallOutputFile = new HostedOptionKey<>(null);

        @Option(help = "Where to store the websocketConnections output")//
        public static final HostedOptionKey<String> ProphetWebsocketConnectionsOutputFile = new HostedOptionKey<>(null);

        @Option(help = "Where to store the endpoint output")//
        public static final HostedOptionKey<String> ProphetEndpointOutputFile = new HostedOptionKey<>(null);

    }

    public static void run(ImageClassLoader loader, AnalysisUniverse aUniverse, AnalysisMetaAccess metaAccess, Inflation bb) {
        String basePackage = Options.ProphetBasePackage.getValue();
        String msName = Options.ProphetMicroserviceName.getValue();

        if (msName == null) {
            throw new RuntimeException("ProphetMicroserviceName option was not provided");
        } else if (basePackage == null) {
            throw new RuntimeException("ProphetMicroserviceName option was not provided");
        }

        logger.info("Running Prophet plugin");
        logger.info("Analyzing all classes in the " + basePackage + " package.");
        logger.info("Creating module " + msName);

        var plugin = new ProphetPlugin(loader, aUniverse, metaAccess, bb, basePackage, msName);
        Module module = plugin.doRun();
        RestDump restDump = new RestDump();
        restDump.writeOutRestCalls(module.getRestCalls(), Options.ProphetRestCallOutputFile.getValue());
        restDump.writeOutWebsocketConnections(module.getWebsocketConnections(), Options.ProphetWebsocketConnectionsOutputFile.getValue());
        restDump.writeOutEndpoints(module.getEndpoints(), Options.ProphetEndpointOutputFile.getValue());

        dumpModule(module);
        logger.info("Final summary: " + module.shortSummary());
    }

    private static void dumpModule(Module module) {
        String outputFile = Options.ProphetEntityOutputFile.getValue();
        String serialized = JsonDump.dump(module);
        if (outputFile != null) {
            logger.info("Writing the entity json into the entity output file: " + outputFile);
            try (var writer = new FileWriter(outputFile)) {
                writer.write(serialized);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            // logger.info("Writing the entity json to standard output:");
            // System.out.println(serialized);
            throw new RuntimeException("ProphetEntityOutputFile option was not provided");

        }
    }

    private Module doRun() {
        URL enumeration = loader.getClassLoader().getResource("application.yml");
        if (enumeration != null) {
            try (BufferedReader reader = new BufferedReader(new FileReader(enumeration.getFile()))) {
                StringBuilder yamlContent = new StringBuilder();
                String line;

                // Read until the first '---' separator or end of file
                while ((line = reader.readLine()) != null) {
                    if (line.trim().equals("---")) {
                        break;
                    }
                    yamlContent.append(line).append("\n");
                }

                // Parse the YAML content before the separator
                this.propMap = new org.yaml.snakeyaml.Yaml().load(yamlContent.toString());

            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                this.propMap = new HashMap<>();
                e.printStackTrace();
            }
        }

        var classes = filterRelevantClasses();
        return processClasses(classes);
    }

    private Set<WebsocketConnection> linkWebsocketEndpointsAndMessageTypes(Set<WebsocketEndpoint> websocketEndpointsList, Set<WebsocketMessageType> websocketMessageTypesList) {
        Set<WebsocketConnection> websocketConnections = new HashSet<WebsocketConnection>();

        for (WebsocketEndpoint endpoint : websocketEndpointsList) {
            if (websocketMessageTypesList.isEmpty()) {
                WebsocketConnection connection = new WebsocketConnection(
                        endpoint.getParentMethod(),
                        endpoint.getReturnType(),
                        endpoint.getUri(),
                        endpoint.isCollection(),
                        endpoint.getConnectionInClassName(),
                        endpoint.getMsName(),
                        endpoint.getParam()
                );
                websocketConnections.add(connection);
            } else {
                boolean handlerMatched = false;
                for (WebsocketMessageType messageType : websocketMessageTypesList) {
                    String endpointHandler = endpoint.getWsHandler();
                    String messageTypeHandler = messageType.getWsHandler();
                    if (endpointHandler.equals(messageTypeHandler)) {
                        WebsocketConnection connection = new WebsocketConnection(
                                endpoint.getParentMethod() != null ? endpoint.getParentMethod() : messageType.getParentMethod(),
                                messageType.getWsDataType(),
                                endpoint.getUri(),
                                endpoint.isCollection(),
                                endpoint.getConnectionInClassName() != null ? endpoint.getConnectionInClassName() : messageType.getConnectionInClassName(),
                                endpoint.getMsName() != null ? endpoint.getMsName() : messageType.getMsName(),
                                endpoint.getParam() != null ? endpoint.getParam() : messageType.getParam()
                        );
                        websocketConnections.add(connection);
                        handlerMatched = true;
                        break;
                    }
                }
                if (!handlerMatched) {
                    WebsocketConnection connection = new WebsocketConnection(
                            endpoint.getParentMethod(),
                            endpoint.getReturnType(),
                            endpoint.getUri(),
                            endpoint.isCollection(),
                            endpoint.getConnectionInClassName(),
                            endpoint.getMsName(),
                            endpoint.getParam()
                    );
                    websocketConnections.add(connection);
                }
            }
        }
        return websocketConnections;
    }

    private Module processClasses(List<Class<?>> classes) {
        var entities = new HashSet<Entity>();
        Set<RestCall> restCallList = new HashSet<RestCall>();
        Set<WebsocketConnection> websocketConnectionsList = new HashSet<WebsocketConnection>();
        Set<WebsocketEndpoint> websocketEndpointsList = new HashSet<WebsocketEndpoint>();
        Set<WebsocketMessageType> websocketMessageTypesList = new HashSet<WebsocketMessageType>();
        Set<Endpoint> endpointList = new HashSet<Endpoint>();

        logger.info("Amount of classes = " + classes.size());
        for (Class<?> clazz : classes) {
            // add if class is entity
            Optional<Entity> ent = EntityExtraction.extractClassEntityCalls(clazz, metaAccess, bb);
            ent.ifPresent(entities::add);
            Set<RestCall> restCalls = RestCallExtraction.extractClassRestCalls(clazz, metaAccess, bb, this.propMap, Options.ProphetMicroserviceName.getValue());
            Set<WebsocketConnection> websocketConnection = WebsocketCallExtraction.extractClassWebsocketConnection(clazz, metaAccess, bb, this.propMap, Options.ProphetMicroserviceName.getValue());
            Set<WebsocketEndpoint> websocketEndpoints = WebsocketCallExtraction.extractClassWebsocketEndpoints(clazz, metaAccess, bb, this.propMap, Options.ProphetMicroserviceName.getValue());
            Set<WebsocketMessageType> websocketMessageTypes = WebsocketCallExtraction.extractClassWebsocketMessageTypes(clazz, metaAccess, bb, this.propMap, Options.ProphetMicroserviceName.getValue());
            restCallList.addAll(restCalls);
            websocketEndpointsList.addAll(websocketEndpoints);
            websocketMessageTypesList.addAll(websocketMessageTypes);
            websocketConnectionsList.addAll(websocketConnection);
            // ENDPOINT EXTRACTION HERE
            Set<Endpoint> endpoints = EndpointExtraction.extractEndpoints(clazz, metaAccess, bb, Options.ProphetMicroserviceName.getValue());
            endpointList.addAll(endpoints);
        }

        websocketConnectionsList.addAll(linkWebsocketEndpointsAndMessageTypes(websocketEndpointsList, websocketMessageTypesList));

        return new Module(new Name(msName), entities, restCallList, websocketConnectionsList, endpointList);
    }

    private List<Class<?>> filterRelevantClasses() {
        var res = new ArrayList<Class<?>>();
        for (Class<?> applicationClass : allClasses) {
            if (applicationClass.getName().startsWith(basePackage) && !applicationClass.isInterface())
                res.add(applicationClass);
        }
        return res;
    }
}
