package com.example.carpetai.api;

import com.example.carpetai.config.ModConfig;
import com.example.carpetai.CarpetAIFakePlayer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 统一的 LLM 客户端，通过 provider 注册表支持多后端。
 */
public class LLMClient {

    private static final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .build();

    private static final Map<String, LLMProvider> providers = new ConcurrentHashMap<>();

    static {
        // 注册已知提供商
        register(new OpenAIProvider("https://api.openai.com/v1/chat/completions"));
        register(new AnthropicProvider("https://api.anthropic.com/v1/messages"));
        register(new GoogleProvider("https://generativelanguage.googleapis.com/v1beta/models"));
        register(new OpenAIProvider("http://localhost:11434/v1/chat/completions") {
            @Override public String name() { return "ollama"; }
        });
        register(new OpenAIProvider("https://api.groq.com/openai/v1/chat/completions") {
            @Override public String name() { return "groq"; }
        });
    }

    public static void register(LLMProvider provider) {
        providers.put(provider.name(), provider);
    }

    public static LLMProvider getProvider(String name) {
        return providers.get(name);
    }

    /**
     * 根据配置调用对应 LLM，返回纯文本响应。
     */
    public static String complete(String systemPrompt, String userPrompt) throws IOException {
        return complete(systemPrompt, userPrompt, null);
    }

    public static String complete(String systemPrompt, String userPrompt,
                                   List<Map<String, String>> history) throws IOException {
        ModConfig config = ModConfig.load();
        LLMProvider provider = providers.get(config.llmProvider);
        if (provider == null) {
            throw new IOException("Unknown LLM provider: " + config.llmProvider);
        }

        String url;
        if (config.llmProvider.equals("google")) {
            // Gemini uses model-specific URL
            url = provider.apiUrl() + "/" + config.model + ":generateContent?key=" + config.apiKey;
        } else if (config.apiUrl != null && !config.apiUrl.isEmpty()) {
            // custom endpoint overrides default
            url = config.apiUrl;
        } else {
            url = provider.apiUrl();
        }

        JsonObject body = provider.buildRequestBody(
            config.model, systemPrompt, userPrompt, history,
            config.maxTokens, config.temperature
        );

        Map<String, String> headers = provider.headers(config.apiKey);

        Request.Builder reqBuilder = new Request.Builder().url(url);
        for (Map.Entry<String, String> h : headers.entrySet()) {
            reqBuilder.addHeader(h.getKey(), h.getValue());
        }

        RequestBody requestBody = RequestBody.create(
            body.toString(), MediaType.parse("application/json")
        );
        reqBuilder.post(requestBody);

        try (Response response = httpClient.newCall(reqBuilder.build()).execute()) {
            if (!response.isSuccessful()) {
                String errBody = response.body() != null ? response.body().string() : "";
                CarpetAIFakePlayer.LOGGER.error("LLM API error {}: {}", response.code(), errBody);
                throw new IOException("LLM API returned " + response.code() + ": " + errBody);
            }
            String respStr = response.body().string();
            JsonObject respJson = JsonParser.parseString(respStr).getAsJsonObject();
            return provider.extractContent(respJson);
        }
    }
}