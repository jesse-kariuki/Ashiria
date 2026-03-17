package com.memoryintel.event;
import java.util.Map;

/*
 * Event is fired once for every heap sample in instrumented code
 *

 */

public class HeapSampleEvent implements MemoryEvent{

    private final long timestamp;
    private final long heapUsedBytes;
    private final long heapMaxBytes;
    private final long heapCommittedBytes;
    private final int loadedClassCount;
    private final Map<String, Long> topClassesByInstanceCount; // class name -> instance count

    public HeapSampleEvent(long timestamp, long heapUsedBytes, long heapMaxBytes,
                           long heapCommittedBytes, int loadedClassCount,
                           Map<String, Long> topClassesByInstanceCount) {
        this.timestamp                = timestamp;
        this.heapUsedBytes            = heapUsedBytes;
        this.heapMaxBytes             = heapMaxBytes;
        this.heapCommittedBytes       = heapCommittedBytes;
        this.loadedClassCount         = loadedClassCount;
        this.topClassesByInstanceCount = topClassesByInstanceCount;
    }


    @Override
    public EventType getEventType() {
        return EventType.HEAP_SAMPLE;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }


    public long getHeapUsedBytes()        { return heapUsedBytes; }
    public long getHeapMaxBytes()         { return heapMaxBytes; }
    public long getHeapCommittedBytes()   { return heapCommittedBytes; }
    public int  getLoadedClassCount()     { return loadedClassCount; }
    public Map<String,Long> getTopClassesByInstanceCount() {
        return topClassesByInstanceCount;
    }

    public double getHeapUsagePercent(){
        if(heapMaxBytes <= 0) return 0;
        return (heapUsedBytes * 100.0) / (double)heapMaxBytes;
    }
}
