package com.memoryintel.sampling;

import com.memoryintel.event.EventPipeline;
import com.memoryintel.event.GcNotificationEvent;
import com.sun.management.GarbageCollectionNotificationInfo;
import com.sun.management.GcInfo;

import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.Map;

/**
 * Subscribes to JVM GC notifications via JMX.
 *
 * How it works: each GC algorithm (G1, ZGC, etc.) exposes itself as a
 * GarbageCollectorMXBean. These beans implement NotificationEmitter,
 * meaning they push notifications whenever a GC completes.
 * Register as a listener against all of them.
 */

public class GCListener implements NotificationListener {
    private final EventPipeline pipeline;

    public GCListener(EventPipeline pipeline) {
        this.pipeline = pipeline;
    }


    /** Call once at agent startup to register against all GC beans. */
    public void register(){
        for(GarbageCollectorMXBean gcBean: ManagementFactory.getGarbageCollectorMXBeans()){
            if(gcBean instanceof NotificationEmitter emitter){
                emitter.addNotificationListener(this, null, gcBean);
                System.out.println("[MemoryIntel] GC listener: " + gcBean.getName());

            }
        }
    }

    /**
     * Called immediately after every GC completes.
     * Runs on a JVM internal notification thread — keep it fast.
     */
    @Override
    public void handleNotification(Notification notification, Object handback) {
        if(!GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(notification.getType())) return;

        try{
            GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from((CompositeData) notification.getUserData());
            GcInfo gcInfo = info.getGcInfo();
            long before = sumHeap(gcInfo.getMemoryUsageBeforeGc());
            long after  = sumHeap(gcInfo.getMemoryUsageAfterGc());

            pipeline.publish(new GcNotificationEvent(
                    System.nanoTime(),
                    info.getGcName(),
                    info.getGcAction(),
                    info.getGcCause(),
                    gcInfo.getDuration(),
                    before, after
            ));


        } catch (Exception e) {}
    }



    private static long sumHeap(Map<String, MemoryUsage> pools) {
        return pools.values().stream().mapToLong(MemoryUsage::getUsed).sum();
    }


}
