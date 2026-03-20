# MemoryIntel JVM Agent

MemoryIntel is a Java instrumentation agent that collects object allocation and heap sampling data at runtime for memory analysis and debugging.

## What it does (so far)

- Implements a Java `-javaagent` entrypoint in `com.memoryintel.agent.MemoryIntelAgent`.
- Instruments application classes at load time using ASM to inject allocation hooks around every `NEW`+`<init>` allocation sequence.
- Collects allocation events in `com.memoryintel.analysis.AllocationCollector` and publishes to an asynchronous, bounded `EventPipeline`.
- Runs a dedicated `MemoryAnalysisEngine` thread to process events and maintain:
  - per-class allocation counts,
  - allocation rates over a sliding window,
  - top allocation call sites,
  - heap sample history.
- Periodically samples JVM heap stats via `MemorySampler` using `MemoryMXBean` and publishes `HeapSampleEvent`.
- Prints a final report including top allocating classes and call sites on shutdown.
- Includes a simple `DemoApp` workload generating many allocations for agent testing.

## Key components

- `MemoryIntelAgent` — Premain/agentmain bootstrap and instrumentation lifecycle.
- `AgentConfig` — Parses agent args (e.g. `sampleInterval`, `maxQueue`, `topN`, `track`, `verbose`).
- `MemoryClassTransformer` — JVM `ClassFileTransformer` that decides whether to instrument classes.
- `ClassAnalyser` / `InstrumentingMethodVisitor` — ASM bytecode instrumentation for `NEW` allocations.
- `AllocationCollector` — Hot-path bridge called after each allocation (instrumented code). 
- `EventPipeline` — Bounded queue for event publish/poll with drop semantics under load.
- `MemoryAnalysisEngine` — Consumer thread analyzing events and tracking metrics.
- `MemorySampler` — Periodic heap sampling and top class summarization.

## Build

```bash
mvn clean package
```

This builds a fat jar (`jar-with-dependencies`) with manifest entries for `Premain-Class` and retransform capabilities.

## Run

Use as a Java agent with any target JVM application:

```bash
java -javaagent:target/memory-intel-agent-1.0.0-jar-with-dependencies.jar=sampleInterval=1000,maxQueue=50000,track=com.memoryintel.demo,verbose=true -jar target/...app.jar
```

To run the demo app directly:

```bash
java -javaagent:target/memory-intel-agent-1.0.0-jar-with-dependencies.jar=verbose=true -cp target/classes com.memoryintel.demo.DemoApp
```

## Agent args

- `sampleInterval=<ms>`: heap sample interval (default `1000`)
- `maxQueue=<N>`: event pipeline capacity (default `50000`)
- `topN=<N>`: top report size (default `20`)
- `track=<pkg1>;<pkg2>`: only instrument matching package prefixes (default all non-system)
- `verbose=<true|false>`: print instrumentation logs
- `rustEngine=<socket>`: optional path for integrating external Rust engine (currently not implemented in pipeline)

## Limitations and current state

- Allocation instrumentation only supports simple `NEW` followed by `<init>` patterns and may not capture every edge case.
- `MemorySampler.sample()` currently creates `HeapSampleEvent` but does not publish it (needs event publish integration).
- Instrumentation currently excludes JVM/system classes and its own agent packages.
- This is a prototype-level memory instrumentation engine with event queue backpressure awareness.

## Where to extend next

- Add event listeners and push updates to external sinks (e.g., HTTP, file, socket).
- Implement real-time telemetry output (console / UI / JFR).
- Add safety and performance guards for heavily allocated code paths.

---

For details, inspect source under `src/main/java/com/memoryintel/**`.
