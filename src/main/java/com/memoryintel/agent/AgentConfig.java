package com.memoryintel.agent;

import java.util.*;

/**
 * Holds all agent configuration, parsed from the -javaagent args string.
 *
 * Parameters:
 *   sampleInterval=N   Heap sampling interval in ms (default: 1000)
 *   maxQueue=N         Max events in pipeline queue (default: 50000)
 *   topN=N             Entries shown in reports (default: 20)
 *   track=pkg;pkg      Semicolon-separated package prefixes to instrument
 *   verbose=true       Log every class instrumentation event
 *   rustEngine=path    Unix socket path for Rust engine bridge
 */


public class AgentConfig {

    private static final long DEFAULT_SAMPLE_INTERVAL_MS = 1000L;
    private static final int DEFAULT_MAX_QUEUE = 50_000;
    private static final int DEFAULT_TOP_N = 20;

    private final long sampleIntervalMs;
    private final int maxQueueSize;
    private final int topN;
    private final Set<String> trackedPackages;
    private final boolean verboseLogging;
    private final String rustSocketPath;

    private AgentConfig(long sampleIntervalMs, int maxQueueSize, int topN, Set<String> trackedPackages,
            boolean verboseLogging, String rustSocketPath) {
        this.sampleIntervalMs = sampleIntervalMs;
        this.maxQueueSize = maxQueueSize;
        this.topN = topN;
        this.trackedPackages = trackedPackages;
        this.verboseLogging = verboseLogging;
        this.rustSocketPath = rustSocketPath;
    }

    public static AgentConfig parse(String args) {
        Map<String, String> params = new HashMap<>();
        if (args != null && !args.isBlank()) {
            for (String token : args.split(",")) {
                String[] kv = token.split("=", 2);
                if (kv.length == 2)
                    params.put(kv[0].trim(), kv[1].trim());
                else
                    params.put(kv[0].trim(), "true");

            }
        }

        long interval = parseLong(params.get("sampleInterval"), DEFAULT_SAMPLE_INTERVAL_MS);
        int maxQueue = parseInt(params.get("maxQueue"), DEFAULT_MAX_QUEUE);
        int topN = parseInt(params.get("topN"), DEFAULT_TOP_N);
        boolean verbose = Boolean.parseBoolean(params.getOrDefault("verbose", "false"));
        String rustSocket = params.get("rustEngine");
        Set<String> trackedPackages = new HashSet<>();
        String trackParam = params.get("track");

        if (trackParam != null)
            Arrays.stream(trackParam.split(";")).map(String::trim).forEach(trackedPackages::add);

        return new AgentConfig(interval, maxQueue, topN, trackedPackages, verbose, rustSocket);

    }

    public boolean shouldInstrument(String internalClassName) {
        if (internalClassName == null)
            return false;
        String cn = internalClassName.replace('/', '.');

        if (cn.startsWith("com.memoryintel.analysis")|| cn.startsWith("com.memoryintel.agent") || cn.startsWith("com.memoryintel.event")|| cn.startsWith("com.memoryintel.sampling"))
            return false;
        if (cn.startsWith("java.") || cn.startsWith("sun"))
            return false;
        if (cn.startsWith("com.sun"))
            return false;
        if (cn.startsWith("org.objectweb.asm"))
            return false;
        if (cn.startsWith("kotlin.") || cn.startsWith("scala."))
            return false;

        if (!trackedPackages.isEmpty())
            return trackedPackages.stream().anyMatch(cn::startsWith);

        return true;

    }

    public long getSampleIntervalMs() {
        return sampleIntervalMs;
    }

    public int getMaxQueueSize() {
        return maxQueueSize;
    }

    public int getTopN() {
        return topN;
    }

    public boolean isVerboseLogging() {
        return verboseLogging;
    }

    public String getRustSocketPath() {
        return rustSocketPath;
    }

    private static long parseLong(String value, long defaultValue) {
        if (value == null)
            return defaultValue;

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static int parseInt(String value, int defaultValue) {
        if (value == null)
            return defaultValue;

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

}
