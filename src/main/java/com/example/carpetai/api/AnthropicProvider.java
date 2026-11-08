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
        // 防御（与 GoogleProvider 同款）：content 缺失/为空、或全是 thinking 等
        // 无 text 字段的块时，给带 stopReason 的明确报错，而非 NPE/越界
        var blocks = responseBody.getAsJsonArray("content");
        if (blocks == null || blocks.size() == 0) {
            throw new IllegalStateException(
                "Anthropic returned no content (stopReason=" + stopReason(responseBody) + ")");
        }
        for (var block : blocks) {          // 跳过 thinking 等非文本块，取第一个 text 块
            JsonObject obj = block.getAsJsonObject();
            if (obj.has("text") && !obj.get("text").isJsonNull()) {
                return obj.get("text").getAsString();
            }
        }
        throw new IllegalStateException(
            "Anthropic returned no text block (stopReason=" + stopReason(responseBody) + ")");
    }

    private static String stopReason(JsonObject responseBody) {
        return responseBody.has("stop_reason") && !responseBody.get("stop_reason").isJsonNull()
            ? responseBody.get("stop_reason").getAsString() : "unknown";
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