package com.example.carpetai.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容提供商（也适用于 Ollama、Groq、自定义 OpenAI 兼容端点）。
 * 协议：/v1/chat/completions
 */
public class OpenAIProvider implements LLMProvider {

    private final String url;

    public OpenAIProvider(String url) {
        this.url = url;
    }

    @Override
    public String name() { return "openai"; }

    @Override
    public String apiUrl() { return url; }

    @Override
    public JsonObject buildRequestBody(String model, String systemPrompt, String userPrompt,
                                        List<Map<String, String>> history, int maxTokens, double temperature) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_tokens", maxTokens);
        body.addProperty("temperature", temperature);

        JsonArray messages = new JsonArray();

        JsonObject sys = new JsonObject();
        sys.addProperty("role", "system");
        sys.addProperty("content", systemPrompt);
        messages.add(sys);

        if (history != null) {
            for (Map<String, String> h : history) {
                JsonObject msg = new JsonObject();
                msg.addProperty("role", h.get("role"));
                msg.addProperty("content", h.get("content"));
                messages.add(msg);
            }
        }

        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", userPrompt);
        messages.add(user);

        body.add("messages", messages);
        return body;
    }

    @Override
    public String extractContent(JsonObject responseBody) {
        return responseBody.getAsJsonArray("choices")
                .get(0).getAsJsonObject()
                .getAsJsonObject("message")
                .get("content").getAsString();
    }
}