package com.example.carpetai.config;

import com.example.carpetai.CarpetAIFakePlayer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 模组配置，持久化到 config/carpet-ai-fake-player.json。
 */
public class ModConfig {

    private static final Path CONFIG_PATH = Path.of("config/carpet-ai-fake-player.json");
    private static ModConfig INSTANCE;

    // ---- LLM 配置 ----
    public String llmProvider = "openai";       // openai, anthropic, google, groq, ollama, custom
    public String apiUrl = "";                   // 自定义端点（为空则用 provider 默认）
    public String apiKey = "";
    public String model = "gpt-4o-mini";
    public int maxTokens = 2048;
    public double temperature = 0.7;

    // ---- 记忆与上下文 ----
    public int contextLength = 10;               // 最大对话轮数
    public int maxTokenBudget = 0;               // 每假人 token 配额上限，0=不限制

    // ---- 任务队列 ----
    public int maxConcurrentTasks = 3;           // 同时执行的假人任务数上限
    public int taskTimeoutSeconds = 300;         // 单任务超时（秒）

    // ---- Action 限制 ----
    public int actionCooldownMs = 2000;          // 两次动作间最低间隔（毫秒）
    public double maxMoveDistance = 100.0;        // 单次移动最大距离

    public static ModConfig load() {
        if (INSTANCE != null) return INSTANCE;
        INSTANCE = new ModConfig();
        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

                INSTANCE.llmProvider = getString(obj, "llmProvider", INSTANCE.llmProvider);
                INSTANCE.apiUrl = getString(obj, "apiUrl", INSTANCE.apiUrl);
                INSTANCE.apiKey = getString(obj, "apiKey", INSTANCE.apiKey);
                INSTANCE.model = getString(obj, "model", INSTANCE.model);
                INSTANCE.maxTokens = getInt(obj, "maxTokens", INSTANCE.maxTokens);
                INSTANCE.temperature = getDouble(obj, "temperature", INSTANCE.temperature);
                INSTANCE.contextLength = getInt(obj, "contextLength", INSTANCE.contextLength);
                INSTANCE.maxTokenBudget = getInt(obj, "maxTokenBudget", INSTANCE.maxTokenBudget);
                INSTANCE.maxConcurrentTasks = getInt(obj, "maxConcurrentTasks", INSTANCE.maxConcurrentTasks);
                INSTANCE.taskTimeoutSeconds = getInt(obj, "taskTimeoutSeconds", INSTANCE.taskTimeoutSeconds);
                INSTANCE.actionCooldownMs = getInt(obj, "actionCooldownMs", INSTANCE.actionCooldownMs);
                INSTANCE.maxMoveDistance = getDouble(obj, "maxMoveDistance", INSTANCE.maxMoveDistance);
            } catch (Exception e) {
                CarpetAIFakePlayer.LOGGER.error("Failed to load config, using defaults", e);
            }
        } else {
            save();
        }
        return INSTANCE;
    }

    public static void save() {
        if (INSTANCE == null) return;
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            JsonObject obj = new JsonObject();
            obj.addProperty("llmProvider", INSTANCE.llmProvider);
            obj.addProperty("apiUrl", INSTANCE.apiUrl);
            obj.addProperty("apiKey", INSTANCE.apiKey);
            obj.addProperty("model", INSTANCE.model);
            obj.addProperty("maxTokens", INSTANCE.maxTokens);
            obj.addProperty("temperature", INSTANCE.temperature);
            obj.addProperty("contextLength", INSTANCE.contextLength);
            obj.addProperty("maxTokenBudget", INSTANCE.maxTokenBudget);
            obj.addProperty("maxConcurrentTasks", INSTANCE.maxConcurrentTasks);
            obj.addProperty("taskTimeoutSeconds", INSTANCE.taskTimeoutSeconds);
            obj.addProperty("actionCooldownMs", INSTANCE.actionCooldownMs);
            obj.addProperty("maxMoveDistance", INSTANCE.maxMoveDistance);
            Files.writeString(CONFIG_PATH, obj.toString());
        } catch (IOException e) {
            CarpetAIFakePlayer.LOGGER.error("Failed to save config", e);
        }
    }

    private static String getString(JsonObject obj, String key, String def) {
        return obj.has(key) ? obj.get(key).getAsString() : def;
    }
    private static int getInt(JsonObject obj, String key, int def) {
        return obj.has(key) ? obj.get(key).getAsInt() : def;
    }
    private static double getDouble(JsonObject obj, String key, double def) {
        return obj.has(key) ? obj.get(key).getAsDouble() : def;
    }
}