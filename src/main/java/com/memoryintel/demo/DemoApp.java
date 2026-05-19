package com.memoryintel.demo;

import java.util.*;
import java.util.concurrent.*;

public class DemoApp {
    private static final List<Object> StaticRegistry = new ArrayList<>();
    private static final Random random = new Random();

    public static void main(String[] args) throws InterruptedException {
        System.out.println("[Agent-Stress] Starting heavy multi-class allocation...");

        int tick = 0;
        while (true) {
            for (int i = 0; i < 500; i++) {
                new LinkedList<>(List.of("data"));
                new HashSet<>(Arrays.asList(1, 2, 3, 4, 5));
                new PriorityQueue<>(Collections.reverseOrder());
            }

            for (int i = 0; i < 300; i++) {
                Runnable r = () -> {};
                Callable<Double> c = () -> Math.random();
                Optional.of("Temporary_Wrapper").map(String::toLowerCase);
            }



            for (int i = 0; i < 100; i++) {
                StringBuilder sb = new StringBuilder("Stress");
                sb.append(tick).append(UUID.randomUUID());
                byte[] smallBuffer = new byte[1024]; // 1KB per loop
            }

            performInternalLogic(tick);

            if (tick % 5 == 0) {
                StaticRegistry.add(new HeavyObject(tick));
            }

            if (tick % 20 == 0) {
                System.out.printf("[Status] Tick: %d | Registry Size: %d | Heap: %dMB%n",
                        tick, StaticRegistry.size(), (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024);
            }

            Thread.sleep(50);
            tick++;
        }
    }

    private static void performInternalLogic(int t) {
        new BitSet(t % 100);
        new StringTokenizer("a,b,c,d,e,f", ",");
    }

    static class HeavyObject {
        private final long timestamp;
        private final double[] weight = new double[256];
        public HeavyObject(int t) {
            this.timestamp = System.currentTimeMillis();
            Arrays.fill(weight, t);
        }
    }
}