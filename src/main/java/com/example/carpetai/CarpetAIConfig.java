package com.example.carpetai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class CarpetAIConfig {
    private static final Path CONFIG_PATH = Path.of("config/carpet-ai-fake-player.json");
    private static CarpetAIConfig INSTANCE;

    public String llmProvider = "ollama"; // ollama, openai, anthropic, google, groq, custom
    public String apiUrl = "http://localhost:11434/v1/chat/completions";
    public String apiKey = "";
    public String model = "qwen2.5:7b";
    public int maxTokens = 2048;
    public double temperature = 0.7;
    public int contextLength = 10; // 记住最近的对话轮数

    public static CarpetAIConfig load() {
        if (INSTANCE != null) return INSTANCE;
        INSTANCE = new CarpetAIConfig();
        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                INSTANCE.llmProvider = obj.get("llmProvider").getAsString();
                INSTANCE.apiUrl = obj.get("apiUrl").getAsString();
                INSTANCE.apiKey = obj.has("apiKey") ? obj.get("apiKey").getAsString() : "";
                INSTANCE.model = obj.get("model").getAsString();
                INSTANCE.maxTokens = obj.get("maxTokens").getAsInt();
                INSTANCE.temperature = obj.get("temperature").getAsDouble();
                INSTANCE.contextLength = obj.has("contextLength") ? obj.get("contextLength").getAsInt() : 10;
            } catch (Exception e) {
                CarpetAIFakePlayer.LOGGER.error("Failed to load config", e);
            }
        } else {
            save();
        }
        return INSTANCE;
    }

    public static void save() {
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
            Files.writeString(CONFIG_PATH, obj.toString());
        } catch (IOException e) {
            CarpetAIFakePlayer.LOGGER.error("Failed to save config", e);
        }
    }
}
