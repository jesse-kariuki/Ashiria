package com.memoryintel.event;

/*

This interface represents a memory event such as allocation or garbage collection. It can be extended to include specific details about the event, such as the type of event, the size of memory involved, and the timestamp of the event.

*/

public interface MemoryEvent {

    EventType getEventType();
    long getTimestamp();

    enum EventType {
        OBJECT_ALLOCATION,
        HEAP_SAMPLE,
        GC_NOTIFICATION,
        CLASS_LOAD,
        TRACKING_ESCALATION
    }

}
