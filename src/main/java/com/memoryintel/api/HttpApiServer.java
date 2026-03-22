package com.memoryintel.api;

import com.memoryintel.analysis.LeakDetector;
import com.memoryintel.analysis.MemoryAnalysisEngine;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Embedded HTTP API server using the JDK's built-in com.sun.net.httpserver.
 * No external dependencies needed.
 *
 * Endpoints:
 *   GET /api/status
 *   GET /api/allocations/top
 *   GET /api/allocations/rates
 *   GET /api/callsites/top
 *   GET /api/leaks
 *   GET /api/lineage?class=com.example.Foo
 *   GET /api/heap/history
 */
public class HttpApiServer {

    private final MemoryAnalysisEngine engine;
    private final int port;
    private HttpServer server;
    private final long startTime = System.currentTimeMillis();

    public HttpApiServer(MemoryAnalysisEngine engine, int port) {
        this.engine = engine;
        this.port = port;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/api/status",           ex -> handle(ex, this::statusJson));
        server.createContext("/api/allocations/top",  ex -> handle(ex, this::topAllocationsJson));
        server.createContext("/api/allocations/rates",ex -> handle(ex, this::topRatesJson));
        server.createContext("/api/callsites/top",    ex -> handle(ex, this::topCallSitesJson));
        server.createContext("/api/leaks",            ex -> handle(ex, this::leaksJson));
        server.createContext("/api/lineage",          ex -> handle(ex, () -> lineageJson(ex)));
        server.createContext("/api/heap/history",     ex -> handle(ex, this::heapHistoryJson));

        server.setExecutor(Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "memory-intel-http");
            t.setDaemon(true);
            return t;
        }));
        server.start();
    }

    public void shutdown() {
        if (server != null) server.stop(1);
    }


    @FunctionalInterface
    interface JsonSupplier {
        String get() throws Exception;
    }

    private void handle(HttpExchange ex, JsonSupplier supplier) throws IOException {
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        try {
            String body = supplier.get();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
        } catch (Exception e) {
            byte[] err = ("{\"error\":\"" + e.getMessage() + "\"}").getBytes();
            ex.sendResponseHeaders(500, err.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(err); }
        }
    }


    private String statusJson() {
        long uptimeSecs = (System.currentTimeMillis() - startTime) / 1000;
        return String.format(
            "{\"uptime_secs\":%d,\"events_processed\":%d,\"queue_dropped\":%d}",
            uptimeSecs,
            engine.getTotalEventsProcessed(),
            engine.getPipeline().getTotalDropped()
        );
    }

    private String topAllocationsJson() {
        List<Map.Entry<String, Long>> top = engine.getTopAllocatingClasses(20);
        return entriesToJson("classes", top, "count");
    }

    private String topRatesJson() {
        List<Map.Entry<String, Double>> top = engine.getTopAllocationRates(20);
        StringBuilder sb = new StringBuilder("{\"classes\":[");
        for (int i = 0; i < top.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(String.format("{\"class_name\":%s,\"rate_per_sec\":%.2f}",
                q(top.get(i).getKey()), top.get(i).getValue()));
        }
        return sb.append("]}").toString();
    }

    private String topCallSitesJson() {
        List<Map.Entry<String, Long>> top = engine.getTopCallSites(20);
        return entriesToJson("sites", top, "count");
    }

    private String leaksJson() {
        List<LeakDetector.LeakSuspect> suspects = engine.getLeakDetector().getSuspects();
        StringBuilder sb = new StringBuilder("{\"suspects\":[");
        for (int i = 0; i < suspects.size(); i++) {
            if (i > 0) sb.append(',');
            LeakDetector.LeakSuspect s = suspects.get(i);
            sb.append(String.format(
                "{\"class_name\":%s,\"confidence\":%s,\"total_allocations\":%d,\"evidence\":[%s]}",
                q(s.className), q(s.confidence.name()), s.totalAllocations,
                String.join(",", s.evidence.stream().map(this::q).toList())
            ));
        }
        return sb.append("]}").toString();
    }

    private String lineageJson(HttpExchange ex) {
        String query = ex.getRequestURI().getQuery(); 
        String className = "";
        if (query != null) {
            for (String part : query.split("&")) {
                if (part.startsWith("class=")) className = part.substring(6);
            }
        }
        Map<String, Long> lineage = engine.getLineageFor(className, 20);
        return entriesToJsonMap("lineage", lineage);
    }

    private String heapHistoryJson() {
        List<MemoryAnalysisEngine.HeapPoint> history = engine.getHeapHistory();
        StringBuilder sb = new StringBuilder("{\"history\":[");
        for (int i = 0; i < history.size(); i++) {
            if (i > 0) sb.append(',');
            MemoryAnalysisEngine.HeapPoint p = history.get(i);
            sb.append(String.format(
                "{\"timestamp_ns\":%d,\"used_bytes\":%d,\"max_bytes\":%d}",
                p.timestamp, p.usedBytes, p.maxBytes));
        }
        return sb.append("]}").toString();
    }


    private String entriesToJson(String key, List<Map.Entry<String, Long>> entries, String valueKey) {
        StringBuilder sb = new StringBuilder("{\"" + key + "\":[");
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(String.format("{\"class_name\":%s,\"%s\":%d}",
                q(entries.get(i).getKey()), valueKey, entries.get(i).getValue()));
        }
        return sb.append("]}").toString();
    }

    private String entriesToJsonMap(String key, Map<String, Long> map) {
        StringBuilder sb = new StringBuilder("{\"" + key + "\":[");
        boolean first = true;
        for (Map.Entry<String, Long> e : map.entrySet()) {
            if (!first) sb.append(',');
            sb.append(String.format("{\"site\":%s,\"count\":%d}", q(e.getKey()), e.getValue()));
            first = false;
        }
        return sb.append("]}").toString();
    }

    private String q(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}