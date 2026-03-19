package com.memoryintel.transformer;

import com.memoryintel.agent.AgentConfig;
import com.memoryintel.analysis.ClassAnalyser;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Registered with Instrumentation.addTransformer().
 * The JVM calls transform() for EVERY class as it is loaded.
 *
 * Return null  → use original bytecode (no change)
 * Return bytes → use these bytes instead (instrumented version)
 */


public class MemoryClassTransformer implements ClassFileTransformer {
    private final AgentConfig config;
    private final AtomicLong inspected = new AtomicLong();
    private final AtomicLong instrumented = new AtomicLong();
    private final AtomicLong errors = new AtomicLong();


    public MemoryClassTransformer(AgentConfig config) {
        this.config = config;
    }

    @Override
    public byte[] transform(ClassLoader loader,
                            String className,
                            Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain,
                            byte[] classfileBuffer

    ) {
        if (className == null) return null;
        inspected.incrementAndGet();
        if (!config.shouldInstrument(className)) return null;

        try {
            byte[] instrumentedByteCode = ClassAnalyser.instrument(classfileBuffer, className);

            if (instrumentedByteCode != null) {
                instrumented.incrementAndGet();
                if (config.isVerboseLogging()) System.out.println("[Memory intel] Instrumented Class: " + className);
                return instrumentedByteCode;
            }
                else {
                    if (config.isVerboseLogging()) System.out.println("[Memory intel] Skipped Class (no NEW found): " + className);
                    return null;
                }
        } catch (Exception e) {
            errors.incrementAndGet();
            if (config.isVerboseLogging())
                System.err.println("[Memory intel] Error transforming Class: " + className + " - " + e.getMessage());
            return null;
        }

    }

    public long getInspected()    { return inspected.get(); }
    public long getInstrumented() { return instrumented.get(); }
    public long getErrors()       { return errors.get(); }




}
