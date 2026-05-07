package com.giothedev.lumora.client.Debug;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.HashMap;
import java.util.Map;

public class LumoraProfiler {
    private static final Map<String, Long> startTimes = new HashMap<>();
    private static final Map<String, Long> frameTotals = new HashMap<>();
    private static final Map<String, Integer> frameCounts = new HashMap<>();
    private static final Map<String, Double> cachedMs = new HashMap<>();

    private static long lastUpdateTick = 0;
    public static boolean enabled = true;
    
    // ProfilerTimer: START
    public static void begin(String key) {
        if(!enabled) return;
        startTimes.put(key, System.nanoTime());
    }

    // ProfilerTimer: END
    public static void end(String key) {
        if(!enabled) return;

        Long start = startTimes.get(key);
        if(start == null) return;

        long time = System.nanoTime()-start;

        frameTotals.merge(key, time, Long::sum);
        frameCounts.merge(key, 1, Integer::sum);
    }

    // HUD for profiler
    public static void render(GuiGraphics graphics) {
        if(!enabled) return;

        Minecraft mc = Minecraft.getInstance();
        if(mc.player == null || mc.level == null) return;

        long gameTime = mc.level.getGameTime();

        // Update once per second (20 ticks)
        if(gameTime - lastUpdateTick >= 20){
            lastUpdateTick = gameTime;

            cachedMs.clear();

            for(String key : frameTotals.keySet()){
                long ns = frameTotals.getOrDefault(key, 0L);
                int count = frameCounts.getOrDefault(key, 1);
                double ms = ns / 1_000_000.0;
                double avg = ms / count;
                cachedMs.put(key, avg);
            }
        }

        int x = 10;
        int y = 10;
        graphics.drawString(mc.font, "Lumen Profiler (avg ms / sec)", x, y, 0xFFFFFF);
        y += 12;

        for(String key : cachedMs.keySet()){
            double avg = cachedMs.get(key);

            String text = key + ": " + String.format("%.3f ms", avg);
            graphics.drawString(mc.font, text, x, y, 0xAAAAAA);
            y += 10;
        }
    }
}