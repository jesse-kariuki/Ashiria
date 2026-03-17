package com.memoryintel.agent;

import java.lang.instrument.Instrumentation;

/**
 * Agent entry point. The JVM calls premain() before the application's main().
 * agentmain() is called when attaching dynamically to a running JVM.
 */


public class MemoryIntelAgent {

    private static Instrumentation instrumentation;

    public static void premain(String agentArgs, Instrumentation inst){

        System.out.println("[MemoryIntel] starting...");
        instrumentation = inst;
        AgentConfig config = AgentConfig.parse(agentArgs);  
        System.out.println("[MemoryIntel] Agent active.");

    }

    public static void agentmain(String agentArgs, Instrumentation inst) {
        
        premain(agentArgs, inst);
    }

    public static Instrumentation getInstrumentation() {
        return instrumentation;
    }


}
