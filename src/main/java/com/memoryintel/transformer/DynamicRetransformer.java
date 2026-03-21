package com.memoryintel.transformer;


import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Retransforms classes at runtime when the analysis engine identifies
 * them as high-priority tracking targets (hot allocators).
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

    /**
     * Escalate tracking for a class by name (dot-form: com.example.Foo).
     * Finds the live Class object and retransforms its bytecode.
     */

    public boolean escalate(String className) {
        if (!escalated.add(className)) return false; // Already done

        if (!inst.isRetransformClassesSupported()) {
            System.out.println(
                    "[MemoryIntel] Retransform not supported, cannot escalate: "
                            + className);
            return false;
        }

        Class<?> target = Arrays.stream(inst.getAllLoadedClasses())
                .filter(c -> c.getName().equals(className))
                .findFirst().orElse(null);

        if (target == null) {
            System.out.println(
                    "[MemoryIntel] Class not found for escalation: " + className);
            return false;
        }

        try {
            inst.retransformClasses(target);
            System.out.println(
                    "[MemoryIntel] Escalated: " + className);
            return true;
        } catch (UnmodifiableClassException e) {
            System.out.println(
                    "[MemoryIntel] Class not modifiable: " + className);
            return false;
        }
    }

    public Set<String> getEscalated() {
        return Collections.unmodifiableSet(escalated);
    }


}
