package com.example.carpetai;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CarpetAIFakePlayer implements ModInitializer {
    public static final String MOD_ID = "carpet-ai-fake-player";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[Carpet AI] Initializing...");
        // 注册命令和事件监听
        CarpetAICommands.register();
        CarpetAIEvents.register();
        LOGGER.info("[Carpet AI] Initialized successfully!");
    }
}
