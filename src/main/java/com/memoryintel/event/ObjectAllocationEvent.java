package com.memoryintel.event;

/*
Event is fired once for every object allocation in instrumented code
*/

public class ObjectAllocationEvent implements MemoryEvent {

    private final String className; /* com.example.Session */
    private final String allocatingClass; /*com.example.SessionManager */
    private final String allocatingMethod; /*com.example.SessionManager.createSession() */
    private final long timestamp;
    private final long threadId;
    private final String threadName;
    private final StackTraceElement[] stackTrace;
    

    public ObjectAllocationEvent(String className, String allocatingClass, String allocatingMethod, long timestamp, long threadId, String threadName, StackTraceElement[] stackTrace) {
        this.className = className;
        this.allocatingClass = allocatingClass;
        this.allocatingMethod = allocatingMethod;
        this.timestamp = timestamp;
        this.threadId = threadId;
        this.threadName = threadName;
        this.stackTrace = stackTrace;
    }

    @Override
    public EventType getEventType() {
        return EventType.OBJECT_ALLOCATION;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    public String getClassName()        { return className; }
    public String getAllocatingClass()   { return allocatingClass; }
    public String getAllocatingMethod()  { return allocatingMethod; }
    public long   getThreadId()         { return threadId; }
    public String getThreadName()       { return threadName; }
    public StackTraceElement[] getStackTrace() { return stackTrace; }




}
