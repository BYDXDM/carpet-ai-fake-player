package com.example.carpetai.entity;

import com.example.carpetai.CarpetAIFakePlayer;
import com.example.carpetai.config.ModConfig;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 多假人并发任务队列，限制同时执行的任务数，防止 API 过载。
 * 线程池跟随服务器生命周期：SERVER_STARTING 时（重）建，SERVER_STOPPING 时关闭并限时等待；
 * 单机（集成服务器）重启世界后可正常重建，不会出现 /ai 永远提示 busy。
 */
public class TaskQueue {

    private static final Map<String, PlayerContext> contexts = new ConcurrentHashMap<>();
    private static final AtomicInteger WORKER_SEQ = new AtomicInteger(0);

    /** 线程池会随服务器停止而关闭、随服务器启动而重建，跨线程读写需 volatile 保证可见 */
    private static volatile ExecutorService executor = newExecutor();

    /** 当前正在执行的任务数（跨线程读写，必须用原子类型，否则丢失更新会永久漂移） */
    private static final AtomicInteger activeTasks = new AtomicInteger(0);

    private static ExecutorService newExecutor() {
        return Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "CarpetAI-Worker-" + WORKER_SEQ.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    /** 确保线程池可用。SERVER_STARTING 时调用；submit 时发现已关闭也会惰性重建。 */
    public static synchronized void ensureRunning() {
        if (executor == null || executor.isShutdown()) {
            executor = newExecutor();
            // 上一任服务器可能带着未执行的主线程回调退出，busy 标志已无主，全部复位
            for (PlayerContext ctx : contexts.values()) {
                synchronized (ctx) {
                    ctx.isBusy = false;
                }
            }
            CarpetAIFakePlayer.LOGGER.info("[Carpet AI] Task queue thread pool (re)started.");
        }
    }

    private static ExecutorService acquireExecutor() {
        ExecutorService e = executor;
        if (e == null || e.isShutdown()) {
            ensureRunning();
            e = executor;
        }
        return e;
    }

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
     * 释放假人的 busy 标志（幂等）。由任务负责在"真正结束"后调用：
     * 成功路径在主线程回调的 finally 中，失败路径在 executeAI 的 finally 中。
     * 不在此处递减 activeTasks，任务计数由 submit 的工作线程 finally 统一收账。
     */
    public static void release(String playerName) {
        PlayerContext ctx = contexts.get(playerName);
        if (ctx != null) {
            synchronized (ctx) {
                ctx.isBusy = false;
            }
        }
    }

    /**
     * 提交一个异步任务。如果达到并发上限，任务会被拒绝。
     * busy 标志由任务自己负责释放（见 {@link #release}）；本类只保证 activeTasks 计数收支平衡。
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
            acquireExecutor().submit(() -> {
                try {
                    task.run();
                } catch (Throwable t) {
                    // executeAI 内部已兜底 Exception；这里防御 Error 级异常并释放 busy，防假人永久卡死
                    CarpetAIFakePlayer.LOGGER.error("Unhandled error in AI task for " + playerName, t);
                    release(playerName);
                } finally {
                    activeTasks.decrementAndGet();
                }
            });
        } catch (RejectedExecutionException e) {
            activeTasks.decrementAndGet();
            release(playerName);
            return false;
        }
        return true;
    }

    /** 关闭线程池（SERVER_STOPPING）。中断在途任务并限时等待收尾，下次服务器启动时重建。 */
    public static void shutdown() {
        ExecutorService e = executor;
        if (e == null) return;
        e.shutdownNow();
        try {
            if (!e.awaitTermination(5, TimeUnit.SECONDS)) {
                CarpetAIFakePlayer.LOGGER.warn("[Carpet AI] Task queue workers did not terminate within 5s.");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
