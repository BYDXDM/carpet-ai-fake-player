package com.example.carpetai;

import com.example.carpetai.command.ModCommands;
import com.example.carpetai.config.ModConfig;
import com.example.carpetai.entity.TaskQueue;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CarpetAIFakePlayer implements ModInitializer {
    public static final String MOD_ID = "carpet-ai-fake-player";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[Carpet AI] Initializing...");

        // 预加载配置
        ModConfig.load();

        // 注册命令
        ModCommands.register();

        // 注册服务器生命周期：线程池随服务器启停重建/关闭，
        // 修复单机（集成服务器）重启世界后线程池已 shutdown 导致 /ai 永远提示 busy 的问题
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            LOGGER.info("[Carpet AI] Starting task queue...");
            TaskQueue.ensureRunning();
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            LOGGER.info("[Carpet AI] Shutting down task queue...");
            TaskQueue.shutdown();
        });

        LOGGER.info("[Carpet AI] Initialized successfully!");
    }
}
