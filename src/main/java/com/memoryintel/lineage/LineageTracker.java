package com.memoryintel.lineage;


import com.memoryintel.event.ObjectAllocationEvent;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Records the lineage graph in Java-side nested ConcurrentHashMaps.
 *
 * Structure:
 *   allocatedClass → callSite → count
 *   threadName     → allocatedClass → count
 */

public class LineageTracker {


    // class → (callSite → count)
    private final ConcurrentHashMap<String,ConcurrentHashMap<String, AtomicLong>> lineageMap = new ConcurrentHashMap<>();

    // thread → (class → count)
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, AtomicLong>> threadMap = new ConcurrentHashMap<>();

    public void recordAllocation(ObjectAllocationEvent event) {
        String cls      = event.getClassName();
        String callSite = event.getAllocatingClass() + "#" + event.getAllocatingMethod();
        String thread   = event.getThreadName();

        lineageMap.computeIfAbsent(cls,    k -> new ConcurrentHashMap<>())
                .computeIfAbsent(callSite, k -> new AtomicLong())
                .incrementAndGet();

        threadMap.computeIfAbsent(thread, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(cls,    k -> new AtomicLong())
                .incrementAndGet();
    }

    /** Top call sites responsible for allocating a given class. */
    public Map<String, Long> getLineageFor(String className, int topN) {
        var callSites = lineageMap.get(className);
        if (callSites == null) return Map.of();
        Map<String, Long> result = new HashMap<>();
        callSites.entrySet().stream()
                .sorted(Comparator.comparingLong(
                        (Map.Entry<String, AtomicLong> e) -> e.getValue().get()).reversed())
                .limit(topN)
                .forEach(e -> result.put(e.getKey(), e.getValue().get()));
        return result;
    }

    /** Top classes allocated by a given thread. */
    public Map<String, Long> getThreadLineage(String thread, int topN) {
        var classes = threadMap.get(thread);
        if (classes == null) return Map.of();
        Map<String, Long> result = new HashMap<>();
        classes.entrySet().stream()
                .sorted(Comparator.comparingLong(
                        (Map.Entry<String, AtomicLong> e) -> e.getValue().get()).reversed())
                .limit(topN)
                .forEach(e -> result.put(e.getKey(), e.getValue().get()));
        return result;
    }




}
