# MemoryIntel JVM Agent

MemoryIntel is a Java instrumentation agent that captures object allocations, class-level allocation rates, heap usage, GC metrics, and call site lineage in real time. It is designed for profiling memory allocation hotspots and early leak/fuzz detection inside a JVM process.

## What it does today

- `-javaagent` entrypoint in `com.memoryintel.agent.MemoryIntelAgent` for static and dynamic attach.
- Bytecode instrumentation via ASM in `com.memoryintel.analysis.ClassAnalyser` / `InstrumentingMethodVisitor` to inject allocation recording around `NEW` + `<init>` sequences.
- Allocation events are captured in `AllocationCollector` and pushed through a bounded `EventPipeline` backpressure-aware queue.
- `MemoryAnalysisEngine` consumes events and maintains:
  - per-class allocation counts,
  - call site counts,
  - allocation rate window (10s window).
- In-process heap history captured by `MemoryAnalysisEngine` and published to `/api/heap/history`.
- `GCListener` subscribes to JVM GC notifications and emits `GcNotificationEvent`s.
- Dynamic retranformer (`DynamicRetransformer`) can escalate hot classes and request retransformation.
- Optional embedded HTTP API server (`HttpApiServer`) with endpoints for status, allocation summaries, call sites, leaks, lineage, and heap history.
- `DemoApp` provides a synthetic workload to exercise agent paths.

## Key components

- `MemoryIntelAgent` — lifecycle, agent config, transformer registration, scheduler setup, and shutdown hooks.
- `AgentConfig` — parse agent args, includes `sampleInterval`, `maxQueue`, `topN`, `track`, `verbose`, `httpPort`, plus non-functional stub `rustEngine` path handling.
- `MemoryClassTransformer` — decide which classes to instrument and delegate ASM transformations.
- `AllocationCollector` — static bridge called by instrumented bytecode to enqueue object allocations.
- `EventPipeline` — concurrent queue with listener support and drop metrics.
- `MemoryAnalysisEngine` — event processing, heap history, allocation rate and leak detector, escalations.
- `MemorySampler` — periodic heap stats reader (currently building `HeapSampleEvent` for future use).
- `GCListener` — JMX-based GC notification observer.
- `HttpApiServer` — lightweight built-in API using JDK `HttpServer`.
- `LineageTracker` — tracks allocation lineage per class and per thread.
- `LeakDetector` — basic heuristics based on stale high-rate allocations.

## Build

```bash
mvn clean package
```

The Maven build produces a fat jar under `target/` (with dependencies, `Premain-Class`, and `Agent-Class` manifest entries).

## Run examples

1) Attach to an existing app:

```bash
java -javaagent:target/memory-intel-agent-1.0.0-jar-with-dependencies.jar=sampleInterval=1000,maxQueue=50000,track=com.memoryintel.demo,verbose=true,httpPort=7777 -jar target/my-app.jar
```

2) Run demo app with agent

```bash
java -javaagent:target/memory-intel-agent-1.0.0-jar-with-dependencies.jar=verbose=true,httpPort=7777 -cp target/classes com.memoryintel.demo.DemoApp
```

## Agent args

- `sampleInterval=<ms>`: heap sample interval for scheduler (default `1000`).
- `maxQueue=<N>`: event pipeline capacity (default `50000`).
- `topN=<N>`: top N entries for reports/API (default `20`).
- `track=<pkg1>;<pkg2>`: semicolon-separated package prefixes to instrument (default all non-system/applicable classes).
- `verbose=<true|false>`: instrumentation logs (default `false`).
- `httpPort=<port>`: if non-zero, starts HTTP API server on localhost.

## HTTP API endpoints (default 7777)

- `GET /api/status`
- `GET /api/allocations/top`
- `GET /api/allocations/rates`
- `GET /api/callsites/top`
- `GET /api/leaks`
- `GET /api/lineage?class=com.example.Foo`
- `GET /api/heap/history`

## Runtime metrics

- Events processed
- Dropped events (queue overflow)
- Heap usage history
- Allocation rates
- GC notifications
- Leak-suspect list

## Limitations

- Instrumentation targets only typical `NEW` + `<init>` patterns.
- `MemorySampler.sample()` currently does not publish events into the pipeline by implementation oversight (planned fix).
- Skips core JVM classes and agent/internal classes, plus `kotlin/scala` packages.
- Prototype status: no security sandbox, no production-grade sampling throttling.

## How to extend

- Add direct event publishing for `MemorySampler` `HeapSampleEvent`.
- Add configurable leak detection thresholds and memory growth alerts.
- Capture/stream GC event rates and durations to remote store.
- Add non-blocking, native-side or Rust-side analysis (`rustEngine`) bridge.
- Add persistent dashboard storage or JFR-event export.

---

### Quick curl checks

```bash
curl -s http://localhost:7777/api/status | python3 -m json.tool
curl -s http://localhost:7777/api/allocations/top | python3 -m json.tool
curl -s http://localhost:7777/api/leaks | python3 -m json.tool
curl -s "http://localhost:7777/api/lineage?class=java.util.ArrayList" | python3 -m json.tool
curl -s http://localhost:7777/api/heap/history | python3 -m json.tool
```