package com.memoryintel.agent;

import com.memoryintel.analysis.AllocationCollector;
import com.memoryintel.analysis.MemoryAnalysisEngine;
import com.memoryintel.api.HttpApiServer;
import com.memoryintel.event.EventPipeline;
import com.memoryintel.sampling.GCListener;
import com.memoryintel.sampling.MemorySampler;
import com.memoryintel.transformer.DynamicRetransformer;
import com.memoryintel.transformer.MemoryClassTransformer;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Agent entry point. The JVM calls premain() before the application's main().
 * agentmain() is called when attaching dynamically to a running JVM.
 */


public class MemoryIntelAgent {

    private static Instrumentation instrumentation;
    private static MemoryAnalysisEngine analysisEngine;
    private static EventPipeline eventPipeline;
    private static ScheduledExecutorService scheduler;
    private static HttpApiServer apiServer; 


    public static void premain(String agentArgs, Instrumentation inst){

        System.out.println("[MemoryIntel] starting...");
        instrumentation = inst;
        AgentConfig config = AgentConfig.parse(agentArgs);
        bootstrap(inst, config);
        System.out.println("[MemoryIntel] Agent active.");

    }

    public static void agentmain(String agentArgs, Instrumentation inst) {
        
        premain(agentArgs, inst);
    }


    private static void bootstrap(Instrumentation inst, AgentConfig config) {
    eventPipeline = new EventPipeline(config.getMaxQueueSize());
    analysisEngine = new MemoryAnalysisEngine(eventPipeline, config);

    MemoryClassTransformer transformer = new MemoryClassTransformer(config);
    inst.addTransformer(transformer, true);

    new GCListener(eventPipeline).register();
    DynamicRetransformer retransformer = new DynamicRetransformer(inst);

    analysisEngine.setRetransformer(retransformer);
    Thread analysisThread = new Thread(analysisEngine::run, "memory-intel-analysis");

    analysisThread.setDaemon(true);
    analysisThread.start();

    MemorySampler sampler = new MemorySampler(inst, eventPipeline, analysisEngine);
    scheduler = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "memory-intel-scheduler");
        t.setDaemon(true);
        return t;
    });
    scheduler.scheduleAtFixedRate(
        sampler::sample,
        config.getSampleIntervalMs(),
        config.getSampleIntervalMs(),
        TimeUnit.MILLISECONDS
    );

    if (config.getHttpPort() > 0) {
        try {
            apiServer = new HttpApiServer(analysisEngine, config.getHttpPort());
            apiServer.start();
            System.out.println("[MemoryIntel] HTTP API → http://localhost:" + config.getHttpPort());
        } catch (IOException e) {
            System.err.println("[MemoryIntel] HTTP server failed to start: " + e.getMessage());
        }
    }

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        System.out.println("[MemoryIntel] Shutting down...");
        scheduler.shutdown();
        analysisEngine.shutdown();
        eventPipeline.shutdown();
        if (apiServer != null) apiServer.shutdown();
        printFinalReport();
    }, "memory-intel-shutdown"));
}

    private static void printFinalReport() {
        System.out.println("\n=== MemoryIntel Final Report ===");
        System.out.printf("Events processed: %,d  |  Dropped: %,d%n",
                analysisEngine.getTotalEventsProcessed(),
                eventPipeline.getTotalDropped());
        System.out.println("\nTop allocating classes:");
        analysisEngine.getTopAllocatingClasses(10).forEach(e ->
                System.out.printf("  %-50s %,d%n", e.getKey(), e.getValue()));
        System.out.println("\nTop call sites:");
        analysisEngine.getTopCallSites(10).forEach(e ->
                System.out.printf("  %-60s %,d%n", e.getKey(), e.getValue()));
    }

    public static MemoryAnalysisEngine getAnalysisEngine() { return analysisEngine; }
    public static Instrumentation getInstrumentation() {
        return instrumentation;
    }




}
