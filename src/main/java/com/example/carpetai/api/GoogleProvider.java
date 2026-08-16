package com.example.carpetai.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;

/**
 * Google Gemini 提供商。
 * 协议：/v1beta/models/{model}:generateContent，使用 query param key={apiKey} 认证。
 */
public class GoogleProvider implements LLMProvider {

    private final String url;

    public GoogleProvider(String url) {
        this.url = url;
    }

    @Override
    public String name() { return "google"; }

    @Override
    public String apiUrl() { return url; }

    @Override
    public JsonObject buildRequestBody(String model, String systemPrompt, String userPrompt,
                                        List<Map<String, String>> history, int maxTokens, double temperature) {
        JsonObject body = new JsonObject();

        // Gemini 用 systemInstruction 和 contents
        JsonArray contents = new JsonArray();

        if (history != null) {
            for (Map<String, String> h : history) {
                JsonObject msg = new JsonObject();
                msg.addProperty("role", h.get("role").equals("assistant") ? "model" : "user");
                JsonArray parts = new JsonArray();
                JsonObject part = new JsonObject();
                part.addProperty("text", h.get("content"));
                parts.add(part);
                msg.add("parts", parts);
                contents.add(msg);
            }
        }

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        JsonArray parts = new JsonArray();
        JsonObject part = new JsonObject();
        part.addProperty("text", userPrompt);
        parts.add(part);
        userMsg.add("parts", parts);
        contents.add(userMsg);

        body.add("contents", contents);

        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            JsonObject sysInst = new JsonObject();
            JsonArray sysParts = new JsonArray();
            JsonObject sysPart = new JsonObject();
            sysPart.addProperty("text", systemPrompt);
            sysParts.add(sysPart);
            sysInst.add("parts", sysParts);
            body.add("systemInstruction", sysInst);
        }

        JsonObject genConfig = new JsonObject();
        genConfig.addProperty("maxOutputTokens", maxTokens);
        genConfig.addProperty("temperature", temperature);
        body.add("generationConfig", genConfig);

        return body;
    }

    @Override
    public String extractContent(JsonObject responseBody) {
        return responseBody.getAsJsonArray("candidates")
                .get(0).getAsJsonObject()
                .getAsJsonObject("content")
                .getAsJsonArray("parts")
                .get(0).getAsJsonObject()
                .get("text").getAsString();
    }

    @Override
    public Map<String, String> headers(String apiKey) {
        // Gemini 通常用 query param 传 key，这里也支持 header 方式
        return Map.of("Content-Type", "application/json");
    }
}