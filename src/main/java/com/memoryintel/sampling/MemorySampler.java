package com.memoryintel.sampling;

import com.memoryintel.analysis.MemoryAnalysisEngine;
import com.memoryintel.event.EventPipeline;
import com.memoryintel.event.HeapSampleEvent;

import java.lang.instrument.Instrumentation;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads JVM heap statistics on a schedule and publishes HeapSampleEvents.
 * Called by the scheduler in MemoryIntelAgent every sampleInterval ms.
 */

public class MemorySampler {
    private final EventPipeline pipeline;
    private final MemoryAnalysisEngine engine;
    private final MemoryMXBean memBean;
    private final ClassLoadingMXBean classBean;

    public MemorySampler(Instrumentation inst, EventPipeline pipeline,
                         MemoryAnalysisEngine engine) {
        this.pipeline  = pipeline;
        this.engine    = engine;
        this.memBean   = ManagementFactory.getMemoryMXBean();
        this.classBean = ManagementFactory.getClassLoadingMXBean();
    }

    /** Called on every scheduler tick. */
    public void sample(){
        try{
            MemoryUsage heap = memBean.getHeapMemoryUsage();
            Map<String, Long> topClasses = new HashMap<>();
            engine.getTopAllocatingClasses(10).forEach(e ->topClasses.put(e.getKey(), e.getValue()));
            HeapSampleEvent event = new HeapSampleEvent(
                    System.nanoTime(),
                    heap.getUsed(),
                    heap.getMax(),
                    heap.getCommitted(),
                    classBean.getLoadedClassCount(),
                    topClasses
            );

        }catch (Exception e){}
    }



}
