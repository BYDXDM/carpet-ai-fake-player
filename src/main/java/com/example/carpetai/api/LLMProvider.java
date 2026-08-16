package com.example.carpetai.api;

import java.util.List;
import java.util.Map;
import com.google.gson.JsonObject;

/**
 * LLM 提供商抽象接口。
 * 每种实现负责构造对应 API 的 HTTP 请求体并解析响应，返回纯文本内容。
 */
public interface LLMProvider {

    /** 提供商标识名，如 "openai", "anthropic", "google", "ollama", "custom" */
    String name();

    /** 此提供商的 API 完整 URL */
    String apiUrl();

    /** 构造请求体 JSON */
    JsonObject buildRequestBody(String model, String systemPrompt, String userPrompt,
                                 List<Map<String, String>> history, int maxTokens, double temperature);

    /** 从原始响应体中提取纯文本内容 */
    String extractContent(JsonObject responseBody);

    /** 默认完整的请求头，可覆盖 */
    default Map<String, String> headers(String apiKey) {
        return Map.of(
            "Content-Type", "application/json",
            "Authorization", "Bearer " + apiKey
        );
    }
}