package com.example.carpetai.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;

/**
 * Anthropic Claude 提供商。
 * 协议：/v1/messages，使用 x-api-key 认证。
 */
public class AnthropicProvider implements LLMProvider {

    private final String url;

    public AnthropicProvider(String url) {
        this.url = url;
    }

    @Override
    public String name() { return "anthropic"; }

    @Override
    public String apiUrl() { return url; }

    @Override
    public JsonObject buildRequestBody(String model, String systemPrompt, String userPrompt,
                                        List<Map<String, String>> history, int maxTokens, double temperature) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_tokens", maxTokens);
        body.addProperty("temperature", temperature);

        // Anthropic 用 system 字段（不是 messages 里的 system role）
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            body.addProperty("system", systemPrompt);
        }

        JsonArray messages = new JsonArray();

        if (history != null) {
            for (Map<String, String> h : history) {
                JsonObject msg = new JsonObject();
                msg.addProperty("role", h.get("role"));
                JsonArray content = new JsonArray();
                JsonObject text = new JsonObject();
                text.addProperty("type", "text");
                text.addProperty("text", h.get("content"));
                content.add(text);
                msg.add("content", content);
                messages.add(msg);
            }
        }

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        JsonArray content = new JsonArray();
        JsonObject text = new JsonObject();
        text.addProperty("type", "text");
        text.addProperty("text", userPrompt);
        content.add(text);
        userMsg.add("content", content);
        messages.add(userMsg);

        body.add("messages", messages);
        return body;
    }

    @Override
    public String extractContent(JsonObject responseBody) {
        return responseBody.getAsJsonArray("content")
                .get(0).getAsJsonObject()
                .get("text").getAsString();
    }

    @Override
    public Map<String, String> headers(String apiKey) {
        return Map.of(
            "Content-Type", "application/json",
            "x-api-key", apiKey,
            "anthropic-version", "2023-06-01"
        );
    }
}