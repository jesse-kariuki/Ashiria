package com.memoryintel.analysis;


import com.memoryintel.event.EventPipeline;
import com.memoryintel.event.ObjectAllocationEvent;

/**
 * Static bridge injected into every instrumented class.
 * A hook that gets called immediately after every object allocation (after the constructor finishes).
 * IMPORTANT: This class is called millions of times per second from
 * application threads. Every nanosecond here matters.
 */

public class AllocationCollector {

    // volatile: ensures all threads see the latest value after bootstrap sets it

    private static volatile EventPipeline pipeline;
    private static volatile boolean captureStackTraces = false;
    private static final int MAX_STACK_DEPTH = 16;

    private AllocationCollector(){}

    /**
     * Called by injected bytecode immediately after every object construction.
     *
     * @param instance         the newly allocated object (fully initialized)
     * @param allocatingClass  which class ran the NEW instruction
     * @param allocatingMethod which method ran the NEW instruction
     */

    public static void onAllocation(
            Object instance,
            String allocatingClass,
            String allocatingMethod
    ){
        if(pipeline == null || instance==null) return;

        try{
            Thread current = Thread.currentThread();
            StackTraceElement[] stackTrace = captureStackTraces ? trimStack(current.getStackTrace(), MAX_STACK_DEPTH): null;
            ObjectAllocationEvent event = new ObjectAllocationEvent(
                    instance.getClass().getName(),
                    allocatingClass,
                    allocatingMethod,
                    System.nanoTime(),
                    current.getId(),
                    current.getName(),
                    stackTrace
            );
            pipeline.publish(event);
        }
        catch (Throwable t){

        }
    }

    private static StackTraceElement[] trimStack(StackTraceElement[] full, int maxDepth){
        int skip = 2; // skip getStackTrace and onAllocation frames
        int take = Math.min(Math.max(0, full.length - skip), maxDepth);
        StackTraceElement[] out = new StackTraceElement[take];
        System.arraycopy(full, skip, out, 0, take);
        return out;
    }

    public static void setPipeline(EventPipeline ep) { pipeline = ep; }
    public static void setCaptureStackTraces(boolean v) { captureStackTraces = v; }
    public static boolean isReady() { return pipeline != null; }




}
