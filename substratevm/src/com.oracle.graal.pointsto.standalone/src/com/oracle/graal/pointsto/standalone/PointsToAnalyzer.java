/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Alibaba Group Holding Limited. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.oracle.graal.pointsto.standalone;

import com.oracle.graal.pointsto.AnalysisObjectScanningObserver;
import com.oracle.graal.pointsto.AnalysisPolicy;
import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.flow.context.bytecode.BytecodeSensitiveAnalysisPolicy;
import com.oracle.graal.pointsto.heap.HeapSnapshotVerifier;
import com.oracle.graal.pointsto.heap.ImageHeap;
import com.oracle.graal.pointsto.infrastructure.SubstitutionProcessor;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccessExtensionProvider;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.pointsto.meta.PointsToAnalysisFactory;
import com.oracle.graal.pointsto.phases.NoClassInitializationPlugin;
import com.oracle.graal.pointsto.standalone.heap.StandaloneImageHeapScanner;
import com.oracle.graal.pointsto.standalone.meta.StandaloneConstantFieldProvider;
import com.oracle.graal.pointsto.standalone.meta.StandaloneConstantReflectionProvider;
import com.oracle.graal.pointsto.standalone.util.Timer;
import com.oracle.graal.pointsto.typestate.DefaultAnalysisPolicy;
import com.oracle.graal.pointsto.util.GraalAccess;
import com.oracle.graal.pointsto.util.PointsToOptionParser;
import com.oracle.graal.pointsto.util.TimerCollection;
import com.oracle.svm.util.ModuleSupport;
import com.oracle.svm.util.ReflectionUtil;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.printer.GraalDebugHandlersFactory;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.compiler.word.WordTypes;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

public final class PointsToAnalyzer {

    static {
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "jdk.internal.vm.ci");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "jdk.internal.vm.compiler");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "jdk.internal.vm.compiler.management");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "java.base", "jdk.internal.loader");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "java.base", "jdk.internal.misc");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "java.base", "sun.text.spi");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "java.base", "jdk.internal.org.objectweb.asm");
        if (JavaVersionUtil.JAVA_SPEC >= 16) {
            ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "java.base", "sun.reflect.annotation");
            ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "java.base", "sun.security.jca");
            ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "jdk.jdeps", "com.sun.tools.classfile");
        }
    }

    private final StandalonePointsToAnalysis bigbang;
    private final DebugContext debugContext;
    private final String analysisTargetMainClass;

    @SuppressWarnings("try")
    private PointsToAnalyzer(String mainEntryClass, OptionValues options) {
        Providers originalProviders = GraalAccess.getOriginalProviders();
        SnippetReflectionProvider snippetReflection = originalProviders.getSnippetReflection();
        MetaAccessProvider originalMetaAccess = originalProviders.getMetaAccess();
        debugContext = new DebugContext.Builder(options, new GraalDebugHandlersFactory(snippetReflection)).build();
        ForkJoinPool executor = PointsToAnalysis.createExecutor(debugContext, Math.min(Runtime.getRuntime().availableProcessors(), 32));
        ClassLoader cl = ClassLoader.getSystemClassLoader();
        StandaloneHost standaloneHost = new StandaloneHost(options, cl);
        int wordSize = getWordSize();
        AnalysisPolicy analysisPolicy = PointstoOptions.AllocationSiteSensitiveHeap.getValue(options) ? new BytecodeSensitiveAnalysisPolicy(options)
                        : new DefaultAnalysisPolicy(options);

        JavaKind wordKind = JavaKind.fromWordSize(wordSize);
        AnalysisUniverse aUniverse = new AnalysisUniverse(standaloneHost, wordKind,
                        analysisPolicy, SubstitutionProcessor.IDENTITY, originalMetaAccess, snippetReflection, snippetReflection, new PointsToAnalysisFactory());
        AnalysisMetaAccess aMetaAccess = new AnalysisMetaAccess(aUniverse, originalMetaAccess);
        aMetaAccess.lookupJavaType(String.class).registerAsReachable();
        StandaloneConstantReflectionProvider aConstantReflection = new StandaloneConstantReflectionProvider(aUniverse, HotSpotJVMCIRuntime.runtime());
        StandaloneConstantFieldProvider aConstantFieldProvider = new StandaloneConstantFieldProvider(aMetaAccess);
        AnalysisMetaAccessExtensionProvider aMetaAccessExtensionProvider = new AnalysisMetaAccessExtensionProvider();
        HostedProviders aProviders = new HostedProviders(aMetaAccess, null, aConstantReflection, aConstantFieldProvider,
                        originalProviders.getForeignCalls(), originalProviders.getLowerer(), originalProviders.getReplacements(),
                        originalProviders.getStampProvider(), snippetReflection, new WordTypes(aMetaAccess, wordKind),
                        originalProviders.getPlatformConfigurationProvider(), aMetaAccessExtensionProvider, originalProviders.getLoopsDataProvider());
        analysisTargetMainClass = mainEntryClass;
        bigbang = new StandalonePointsToAnalysis(options, aUniverse, aProviders, standaloneHost, executor, () -> {
            /* do nothing */
        }, new TimerCollection());
        standaloneHost.setImageName(analysisTargetMainClass);
        aUniverse.setBigBang(bigbang);
        ImageHeap heap = new ImageHeap();
        StandaloneImageHeapScanner heapScanner = new StandaloneImageHeapScanner(bigbang, heap, aMetaAccess,
                        snippetReflection, aConstantReflection, new AnalysisObjectScanningObserver(bigbang), cl);
        aUniverse.setHeapScanner(heapScanner);
        HeapSnapshotVerifier heapVerifier = new HeapSnapshotVerifier(bigbang, heap, heapScanner);
        aUniverse.setHeapVerifier(heapVerifier);
        /* Register already created types as assignable. */
        aUniverse.getTypes().forEach(t -> {
            t.registerAsAssignable(bigbang);
            if (t.isReachable()) {
                bigbang.onTypeInitialized(t);
            }
        });
        /*
         * System classes and fields are necessary to tell the static analysis that certain things
         * really "exist". The most common reason for that is that there are no instances and
         * allocations of these classes seen during the static analysis. The heap chunks are one
         * good example.
         */
        try (Indent ignored = debugContext.logAndIndent("add initial classes/fields/methods")) {
            bigbang.addRootClass(Object.class, false, false).registerAsInHeap();
            bigbang.addRootClass(String.class, false, false).registerAsInHeap();
            bigbang.addRootClass(String[].class, false, false).registerAsInHeap();
            bigbang.addRootField(String.class, "value").registerAsInHeap();
            bigbang.addRootClass(long[].class, false, false).registerAsInHeap();
            bigbang.addRootClass(byte[].class, false, false).registerAsInHeap();
            bigbang.addRootClass(byte[][].class, false, false).registerAsInHeap();
            bigbang.addRootClass(Object[].class, false, false).registerAsInHeap();

            bigbang.addRootMethod(ReflectionUtil.lookupMethod(Object.class, "getClass"), true);

            for (JavaKind kind : JavaKind.values()) {
                if (kind.isPrimitive() && kind != JavaKind.Void) {
                    bigbang.addRootClass(kind.toJavaClass(), false, true);
                }
            }
            bigbang.getMetaAccess().lookupJavaType(JavaKind.Void.toJavaClass()).registerAsReachable();

            GraphBuilderConfiguration.Plugins plugins = new GraphBuilderConfiguration.Plugins(new InvocationPlugins());
            NoClassInitializationPlugin classInitializationPlugin = new NoClassInitializationPlugin();
            plugins.setClassInitializationPlugin(classInitializationPlugin);
            aProviders.setGraphBuilderPlugins(plugins);
        }
    }

    private static int getWordSize() {
        int wordSize;
        String archModel = System.getProperty("sun.arch.data.model");
        switch (archModel) {
            case "64":
                wordSize = AMD64Kind.QWORD.getSizeInBytes();
                break;
            case "32":
                wordSize = AMD64Kind.DWORD.getSizeInBytes();
                break;
            default:
                throw new RuntimeException("Property sun.arch.data.model should only be 64 or 32, but is " + archModel);

        }
        return wordSize;
    }

    /**
     * Create a PointsToAnalyzer instance with given arguments. The arguments should specify one
     * analysis entry class, and additional analysis options in Substrate VM's hosted option style.
     *
     * @param args entry class name and additional analysis options
     * @return PointsToAnalyzer instance
     */
    public static PointsToAnalyzer createAnalyzer(String[] args) {
        String mainEntryClass = null;
        List<String> optionArgs = new ArrayList<>();
        for (String arg : args) {
            if (arg.startsWith("-")) {
                optionArgs.add(arg);
            } else {
                mainEntryClass = arg;
            }
        }
        OptionValues options = PointsToOptionParser.getInstance().parse(optionArgs.toArray(new String[0]));
        return new PointsToAnalyzer(mainEntryClass, options);
    }

    @SuppressWarnings("try")
    public int run() {
        registerEntryMethod();
        int exitCode = 0;
        try (Timer t = new Timer("analysis", "standalone pointsto analysis")) {
            bigbang.runAnalysis(debugContext, (analysisUniverse) -> true);
        } catch (Throwable e) {
            reportException(e);
            exitCode = 1;
        }
        bigbang.getUnsupportedFeatures().report(bigbang);
        return exitCode;
    }

    /**
     * Clean up all analysis data. This method is called by user, not by the analysis framework,
     * because user may still use the analysis results after the pointsto analysis.
     */
    public void cleanUp() {
        bigbang.cleanupAfterAnalysis();
    }

    public AnalysisUniverse getResultUniverse() {
        return bigbang.getUniverse();
    }

    private void registerEntryMethod() {
        if (analysisTargetMainClass == null) {
            throw new RuntimeException("No analysis entry main class is specified.");
        } else {
            try {
                Class<?> analysisMainClass = Class.forName(analysisTargetMainClass);
                Method main = analysisMainClass.getDeclaredMethod("main", String[].class);
                // main method is static, whatever the invokeSpecial is it is ignored.
                bigbang.addRootMethod(main, true);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Can't find the specified analysis main class " + analysisTargetMainClass, e);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("Can't find the main method in the analysis main class " + analysisTargetMainClass, e);
            }
        }
    }

    /**
     * Need --add-exports=java.base/jdk.internal.module=ALL-UNNAMED in command line.
     *
     * @param args options to run the analyzing
     */
    public static void main(String[] args) {
        PointsToAnalyzer analyzer = createAnalyzer(args);
        analyzer.run();
    }

    protected static void reportException(Throwable e) {
        System.err.print("Exception:");
        e.printStackTrace();
    }
}