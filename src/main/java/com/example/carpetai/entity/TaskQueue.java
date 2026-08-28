package com.example.carpetai.entity;

import com.example.carpetai.config.ModConfig;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 多假人并发任务队列，限制同时执行的任务数，防止 API 过载。
 */
public class TaskQueue {

    private static final Map<String, PlayerContext> contexts = new ConcurrentHashMap<>();
    private static final ExecutorService executor = Executors.newFixedThreadPool(4);

    /** 当前正在执行的任务数（跨线程读写，必须用原子类型，否则丢失更新会永久漂移） */
    private static final AtomicInteger activeTasks = new AtomicInteger(0);

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

        // isBusy 的检查与置位必须原子，且任务计数先于 submit 递增，
        // 否则任务可能在计数递增前完成递减，或被拒绝后假人永远 busy。
        synchronized (ctx) {
            if (ctx.isBusy) return false;
            if (activeTasks.get() >= config.maxConcurrentTasks) return false;
            ctx.isBusy = true;
        }
        activeTasks.incrementAndGet();
        try {
            executor.submit(() -> {
                try {
                    task.run();
                } finally {
                    ctx.isBusy = false;
                    activeTasks.decrementAndGet();
                }
            });
        } catch (RejectedExecutionException e) {
            activeTasks.decrementAndGet();
            synchronized (ctx) {
                ctx.isBusy = false;
            }
            return false;
        }
        return true;
    }

    public static void shutdown() {
        executor.shutdown();
    }
}