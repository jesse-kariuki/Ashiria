package com.memoryintel.analysis;

import com.memoryintel.agent.AgentConfig;
import com.memoryintel.event.EventPipeline;
import com.memoryintel.event.HeapSampleEvent;
import com.memoryintel.event.MemoryEvent;
import com.memoryintel.event.ObjectAllocationEvent;
import com.memoryintel.lineage.LineageTracker;
import com.memoryintel.transformer.DynamicRetransformer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Runs on its own daemon thread, consuming events from the pipeline
 * and building in-memory analytics.
 */

public class MemoryAnalysisEngine implements Runnable{
    private static final int BATCH_SIZE = 256;
    private static final int WINDOW_SECONDS = 10;
    private final EventPipeline pipeline;
    private final AgentConfig config;

    /*
    *  -- Allocation tracking -----------------------------------------------
      ConcurrentHashMap: safe for reads from the console reporter thread
      while the analysis thread writes.
      * AtomicLong: safe increment from
      multiple threads without synchronized blocks.
    * */

    private final ConcurrentHashMap<String, AtomicLong> allocationCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> windowCounts = new ConcurrentHashMap<>(); // in a particular time frame/ window, how many allocations took place
    private final ConcurrentHashMap<String, AtomicLong> callSiteCounts = new ConcurrentHashMap<>();// where allocations are happening

    private long lastWindowResetTime = System.currentTimeMillis();

    /*
    * volatile: written by analysis thread, read by reporter thread
    * Making it volatile + replacing the whole map atomically avoids any
    * need for locks on the reader side.
    * */

    private volatile Map<String, Double> allocationRates = Collections.emptyMap();
    private final Deque<HeapPoint> heapHistory = new ArrayDeque<>(BATCH_SIZE);

    private volatile boolean running = true;
    private final AtomicLong eventsProcessed = new AtomicLong();
    private final long startTime = System.currentTimeMillis();

    private final Set<String> escalatedClasses = ConcurrentHashMap.newKeySet();
    private volatile DynamicRetransformer retransformer;
    public void setRetransformer(DynamicRetransformer r) { this.retransformer = r; }


    private final LineageTracker lineageTracker = new LineageTracker();


    public Map<String,Long> getLineageFor(String className, int topN) {
        return lineageTracker.getLineageFor(className, topN);
    }




    public MemoryAnalysisEngine(EventPipeline pipeline, AgentConfig config) {
        this.pipeline = pipeline;
        this.config = config;
    }

    @Override
    public void run() {
        List<MemoryEvent> batch = new ArrayList<>(BATCH_SIZE);

        while(running) {
            try{
                MemoryEvent first = pipeline.poll(50);
                if(first != null) {
                    batch.add(first);
                    pipeline.drainTo(batch, BATCH_SIZE-1);
                    processBatch(batch);
                    batch.clear();
                }
                maybeRecalculateRates();
            }
            catch(InterruptedException e) {Thread.currentThread().interrupt();break;}
            catch(Exception e) {System.err.println("[Memory intel] Analysis error: " + e.getMessage());}




        }


    }



    private void processBatch(List<MemoryEvent> batch) {
        for(MemoryEvent event : batch) {
            eventsProcessed.incrementAndGet();
            switch (event.getEventType()){
                case OBJECT_ALLOCATION:
                    processAllocation((ObjectAllocationEvent)event); break;
                case HEAP_SAMPLE:
                    processHeapSample((HeapSampleEvent)event); break;
                default: break;
            }
            pipeline.notifyListeners(event);
        }

    }
    private void processAllocation(ObjectAllocationEvent event) {
        allocationCounts.computeIfAbsent(event.getClassName(), k -> new AtomicLong()).incrementAndGet();
        windowCounts.computeIfAbsent(event.getClassName(), k -> new AtomicLong()).incrementAndGet();

        String site = event.getAllocatingClass() + "." + event.getAllocatingMethod();
        callSiteCounts.computeIfAbsent(site, k -> new AtomicLong()).incrementAndGet();
        lineageTracker.recordAllocation(event);

    }
    private void processHeapSample(HeapSampleEvent e) {
        synchronized (heapHistory){
            heapHistory.addLast(new HeapPoint(e.getTimestamp(), e.getHeapUsedBytes(), e.getHeapMaxBytes()));
            while(heapHistory.size() >300) heapHistory.removeFirst();
        }
        if(e.getHeapUsagePercent()>80) System.out.printf("[Memory intel] ⚠️ High heap usage: %.1f%%n", e.getHeapUsagePercent());

        checkEscalation();
    }
    private void checkEscalation() {
        allocationRates.entrySet().stream()
                .filter(e -> e.getValue() > 500.0)
                .map(Map.Entry::getKey)
                .filter(cls -> !escalatedClasses.contains(cls))
                .forEach(cls -> {
                    escalatedClasses.add(cls);
                    System.out.printf(
                            "[MemoryIntel] Escalating: %s (%.0f/sec)%n", cls,
                            allocationRates.get(cls));
                    if (retransformer != null) retransformer.escalate(cls);
                });
    }
    private void maybeRecalculateRates() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastWindowResetTime;
        if(elapsed < WINDOW_SECONDS * 1000L ) return;

        double secs = elapsed / 1000.0;
        Map<String, Double> rates = new HashMap<>();
        windowCounts.forEach((cls,count) ->{
            double rate = count.get() / secs;
            if(rate > 0.1) rates.put(cls, rate);
        });
        allocationRates = Collections.unmodifiableMap(rates);
        windowCounts.clear();
        lastWindowResetTime = now;



    }

    // Query API for actual analysis of top consumers
    public  List<Map.Entry<String, Long>> getTopAllocatingClasses(int n){
        return allocationCounts
                .entrySet()
                .stream()
                .map(e -> Map.entry(e.getKey(), e.getValue().get()))
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(n)
                .collect(Collectors.toList());

    }
    public List<Map.Entry<String,Double>> getTopAllocationRates(int n) {
        return allocationRates.entrySet().stream()
                .sorted(Map.Entry.<String,Double>comparingByValue().reversed())
                .limit(n).collect(Collectors.toList());
    }

    public List<Map.Entry<String,Long>> getTopCallSites(int n) {
        return callSiteCounts.entrySet().stream()
                .map(e -> Map.entry(e.getKey(), e.getValue().get()))
                .sorted(Map.Entry.<String,Long>comparingByValue().reversed())
                .limit(n).collect(Collectors.toList());
    }

    public List<HeapPoint> getHeapHistory() {
        synchronized (heapHistory) { return new ArrayList<>(heapHistory); }
    }

    public long getTotalEventsProcessed() { return eventsProcessed.get(); }
    public void shutdown() { running = false; }




    public static class HeapPoint {
        public final long timestamp, usedBytes, maxBytes;
        HeapPoint(long t, long u, long m) { timestamp=t; usedBytes=u; maxBytes=m; }
    }





}
