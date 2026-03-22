package com.memoryintel.analysis;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Multi-heuristic memory leak detector.
 *
 * Called periodically by MemoryAnalysisEngine (on every rate recalculation).
 * Looks for classes whose allocation count grows monotonically and whose
 * rate is accelerating — hallmarks of an object accumulation leak.
 */
public class LeakDetector {

    public enum Confidence { LOW, MEDIUM, HIGH, CRITICAL }

    public static class LeakSuspect {
        public final String className;
        public final Confidence confidence;
        public final List<String> evidence;
        public final long totalAllocations;

        LeakSuspect(String cls, Confidence conf, List<String> ev, long total) {
            this.className = cls;
            this.confidence = conf;
            this.evidence = Collections.unmodifiableList(ev);
            this.totalAllocations = total;
        }
    }

    // Per-class historical snapshot: (timestamp, totalCount)
    private static class ClassHistory {
        final Deque<long[]> snapshots = new ArrayDeque<>(32); // [timestamp, count]
        final Deque<Double> rateHistory = new ArrayDeque<>(12);
    }

    private final ConcurrentHashMap<String, ClassHistory> histories = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LeakSuspect> suspects = new ConcurrentHashMap<>();

    /**
     * Called by MemoryAnalysisEngine after each rate window recalculation.
     * Updates history and re-evaluates leak heuristics for each class.
     *
     * @param className   dot-form class name
     * @param totalCount  total allocation count since agent start
     * @param ratePerSec  allocations/sec in the last window
     */
    public void tick(String className, long totalCount, double ratePerSec) {
        ClassHistory history = histories.computeIfAbsent(className, k -> new ClassHistory());

        history.snapshots.addLast(new long[]{System.currentTimeMillis(), totalCount});
        if (history.snapshots.size() > 30) history.snapshots.removeFirst();

        history.rateHistory.addLast(ratePerSec);
        if (history.rateHistory.size() > 12) history.rateHistory.removeFirst();

        evaluate(className, history, totalCount, ratePerSec);
    }

    private void evaluate(String name, ClassHistory h, long total, double rate) {
        if (h.snapshots.size() < 5) return; // Need enough history to judge

        int score = 0;
        List<String> evidence = new ArrayList<>();

        // Heuristic 1: Monotonic growth — count never decreases (GC not reclaiming)
        long[] prev = null;
        boolean monotonic = true;
        for (long[] snap : h.snapshots) {
            if (prev != null && snap[1] < prev[1]) { monotonic = false; break; }
            prev = snap;
        }
        if (monotonic && h.snapshots.size() >= 10) {
            evidence.add("Monotonic growth over " + h.snapshots.size() + " samples");
            score += 2;
        }

        // Heuristic 2: Rate acceleration — second half of history faster than first
        if (h.rateHistory.size() >= 6) {
            Double[] rates = h.rateHistory.toArray(new Double[0]);
            int mid = rates.length / 2;
            double firstHalf = 0, secondHalf = 0;
            for (int i = 0; i < mid; i++) firstHalf += rates[i];
            for (int i = mid; i < rates.length; i++) secondHalf += rates[i];
            firstHalf /= mid;
            secondHalf /= (rates.length - mid);
            if (secondHalf > firstHalf * 1.5) {
                evidence.add(String.format("Rate accelerating: %.0f/s → %.0f/s", firstHalf, secondHalf));
                score += 2;
            }
        }

        // Heuristic 3: High sustained volume
        if (total > 100_000 && rate > 50.0) {
            evidence.add(String.format("%,d total at %.0f/s", total, rate));
            score += 1;
        }

        // Heuristic 4: Long lifespan — first snapshot is old and count was already high
        if (!h.snapshots.isEmpty()) {
            long ageMs = System.currentTimeMillis() - h.snapshots.peekFirst()[0];
            if (ageMs > 120_000 && total > 50_000) { // 2+ minutes old and still large
                evidence.add(String.format("Sustained for %ds with %,d total", ageMs/1000, total));
                score += 1;
            }
        }

        if (score == 0) return;

        Confidence confidence = switch (score) {
            case 1 -> Confidence.LOW;
            case 2 -> Confidence.MEDIUM;
            case 3 -> Confidence.HIGH;
            default -> Confidence.CRITICAL;
        };

        suspects.put(name, new LeakSuspect(name, confidence, evidence, total));
    }

    /** Returns all current leak suspects, sorted by total allocations descending. */
    public List<LeakSuspect> getSuspects() {
        List<LeakSuspect> result = new ArrayList<>(suspects.values());
        result.sort(Comparator.comparingLong((LeakSuspect s) -> s.totalAllocations).reversed());
        return result;
    }

    public void clear() { suspects.clear(); histories.clear(); }
}
