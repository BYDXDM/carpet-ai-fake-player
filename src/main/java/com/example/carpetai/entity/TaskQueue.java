package com.example.carpetai.entity;

import com.example.carpetai.config.ModConfig;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 多假人并发任务队列，限制同时执行的任务数，防止 API 过载。
 */
public class TaskQueue {

    private static final Map<String, PlayerContext> contexts = new ConcurrentHashMap<>();
    private static final ExecutorService executor = Executors.newFixedThreadPool(4);

    /** 当前正在执行的任务数 */
    private static int activeTasks = 0;

    public static PlayerContext getOrCreate(String playerName) {
        return contexts.computeIfAbsent(playerName, PlayerContext::new);
    }

    public static PlayerContext get(String playerName) {
        return contexts.get(playerName);
    }

    public static Map<String, PlayerContext> all() {
        return contexts;
    }

    public static void remove(String playerName) {
        PlayerContext ctx = contexts.remove(playerName);
        if (ctx != null) ctx.isBusy = false;
    }

    /**
     * 提交一个异步任务。如果达到并发上限，任务会被拒绝。
     */
    public static boolean submit(String playerName, Runnable task) {
        ModConfig config = ModConfig.load();
        PlayerContext ctx = getOrCreate(playerName);

        if (ctx.isBusy) return false;
        if (activeTasks >= config.maxConcurrentTasks) return false;

        ctx.isBusy = true;
        executor.submit(() -> {
            try {
                task.run();
            } finally {
                ctx.isBusy = false;
                activeTasks--;
            }
        });
        activeTasks++;
        return true;
    }

    public static void shutdown() {
        executor.shutdown();
    }
}