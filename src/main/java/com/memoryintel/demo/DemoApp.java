package com.memoryintel.demo;

import java.util.*;

public class DemoApp {
    private static final Map<Integer, String> cache = new HashMap<>();

    public static void main(String[] args) throws Exception {
        System.out.println("[Demo] Starting allocation workload");
        int tick = 0;
        while (true) {
            // Create many short-lived objects per tick
            for (int i = 0; i < 200; i++) {
                new ArrayList<>(Arrays.asList("a", "b", "c"));
            }
            // Accumulate objects in the cache (simulates a leak)
            cache.put(tick, "cached-value-" + tick);

            if (tick % 10 == 0)
                System.out.printf("[Demo] tick=%d, cache size=%d%n",
                        tick, cache.size());
            Thread.sleep(100);
            tick++;
        }
    }
}
