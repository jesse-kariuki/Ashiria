package com.memoryintel.event;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Central event bus. Decouples producers (instrumented code, sampler) from
 * the consumer (analysis engine).
 *
 * Thread safety: all methods are safe to call from any thread simultaneously.
 */


public class EventPipeline {

    // Bounded Queue - prevents unbounded memory growth under load
    private final BlockingQueue<MemoryEvent> queue;

    // Listeners are notified by the consumer thread, not the producer
    // CopyOnWriteArrayList is thread safe for concurrent modifications

    private final CopyOnWriteArrayList<Consumer<MemoryEvent>> listeners = new CopyOnWriteArrayList<>();
    private final AtomicLong totalPublished = new AtomicLong();
    private final AtomicLong totalDropped = new AtomicLong();
    private volatile boolean shutdown = false;

    public EventPipeline(int maxQueueSize) {
        this.queue = new LinkedBlockingDeque<>(maxQueueSize);
    }

    /**
     * Publish an event. NEVER blocks — drops if queue is full.
     * Called from instrumented application threads (hot path).
     */
    public void publish(MemoryEvent event) {
        if (shutdown || event == null) return;
        if(queue.offer(event)) totalPublished.incrementAndGet();
        else totalDropped.incrementAndGet();

    }

    /**
     * Poll for the next event. Blocks up to timeoutMs.
     * Called only from the analysis engine thread.
     */

    public MemoryEvent poll(long timeoutMs) throws InterruptedException{
        return queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Drain all available events into a list (non-blocking batch read).
     * Removes at most max given elements and drains them into the given target list.
     * Returns the number of elements drained.
     * Called only from the analysis engine thread.
     */


    public int drainTo(List<MemoryEvent> target, int max) {
        return queue.drainTo(target, max);
    }

    /**
     * Register a listener to be called for every event.
     * Listeners are called by the analysis thread, not producer threads.
     */

    public void addListener(Consumer<MemoryEvent> listener) {
        if (listener != null) listeners.add(listener);
    }

    /**
     * Notify all listeners. Called by the analysis engine after processing.
     */

    public void notifyListeners(MemoryEvent event) {
        for(Consumer<MemoryEvent> l: listeners){
            try{l.accept(event);}
            catch(Exception e){}
        }
    }

    public void shutdown()              { shutdown = true; }
    public long getTotalPublished()     { return totalPublished.get(); }
    public long getTotalDropped()       { return totalDropped.get(); }
    public int  getQueueSize()          { return queue.size(); }


    /** True if >5% of events are being dropped — signals back-pressure */
    public boolean isBackPressured() {
        long total = totalPublished.get() + totalDropped.get();
        if (total == 0) return false;
        return (totalDropped.get() * 100.0 / total) > 5.0;
    }



}
