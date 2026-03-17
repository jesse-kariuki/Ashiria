package com.memoryintel.demo;

public class DemoApp {
        public static void main(String[] args) throws Exception {
        System.out.println("[Demo] App starting");
        for (int i = 0; i < 5; i++) {
            Thread.sleep(1000);
            System.out.println("[Demo] Tick " + i);
        }
        System.out.println("[Demo] App done");
    }
}
