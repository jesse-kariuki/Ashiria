package com.memoryintel.transformer;


import java.lang.instrument.Instrumentation;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Retransforms classes at runtime when the analysis engine identifies
 * them as high-priority tracking targets (hot allocators).
 *
 * Retransformation re-runs the registered ClassFileTransformers against
 * an already-loaded class. Our MemoryClassTransformer is still registered,
 * so it will inject allocation tracking into the class's bytecode live.
 */

public class DynamicRetransformer {
    private final Instrumentation inst;
    private final Set<String> escalated = ConcurrentHashMap.newKeySet();

    public DynamicRetransformer(Instrumentation inst) {
        this.inst = inst;
    }

}
