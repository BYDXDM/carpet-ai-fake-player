package com.example.carpetai;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import carpet.patches.FakePlayer;
import carpet.patches.FakePlayerInfo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CarpetAIEvents {
    // 存储假人的上下文状态
    public static Map<String, PlayerContext> CONTEXTS = new ConcurrentHashMap<>();

    public static void register() {
        // 可以在这里注册 tick 事件，用于处理需要持续执行的任务
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // Process active AI tasks
        });
    }

    public static class PlayerContext {
        public String lastAction;
        public long lastActionTime;
        public int taskProgress;
        
        public PlayerContext() {
            this.taskProgress = 0;
        }
    }
}
