package com.memoryintel.event;

public class GcNotificationEvent implements MemoryEvent{
    private final long timestamp;
    private final String gcName; //e.g G1 Young Generation
    private final String gcAction; //e.g "end of minor GC"
    private final String gcCause; //e.g "Allocation Failure"
    private final long durationMs; //duration of GC in milliseconds
    private final long heapUsedBefore;
    private final long heapUsedAfter;

    public GcNotificationEvent(long timestamp, String gcName, String gcAction,
                               String gcCause, long durationMs, long heapUsedBefore, long heapUsedAfter) {
        this.timestamp      = timestamp;
        this.gcName         = gcName;
        this.gcAction       = gcAction;
        this.gcCause        = gcCause;
        this.durationMs     = durationMs;
        this.heapUsedBefore = heapUsedBefore;
        this.heapUsedAfter  = heapUsedAfter;
    }

    @Override public EventType getEventType() { return EventType.GC_NOTIFICATION; }
    @Override public long getTimestamp() { return timestamp; }

    public String getGcName() {
        return gcName;
    }

    public String getGcAction() {
        return gcAction;
    }

    public String getGcCause() {
        return gcCause;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public long getHeapUsedBefore() {
        return heapUsedBefore;
    }

    public long getHeapUsedAfter() {
        return heapUsedAfter;
    }
}
