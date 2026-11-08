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
        // custom：任意 OpenAI 兼容端点（OpenAI 协议复用）。无默认端点，
        // 必须在配置里设置 apiUrl，为空时 complete() 统一给出明确报错。
        register(new OpenAIProvider("") {
            @Override public String name() { return "custom"; }
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
        // 自定义端点对所有 provider（含 google）生效；google 在其上拼接模型路径，
        // key 一律通过请求头传递（见各 provider 的 headers()），不进 URL
        String base = (config.apiUrl != null && !config.apiUrl.isEmpty()) ? config.apiUrl : provider.apiUrl();
        if (base == null || base.isBlank()) {
            // custom 等无默认端点的 provider 必须先配置 apiUrl，给出可操作的明确报错
            throw new IOException("LLM API URL is empty: provider '" + config.llmProvider
                + "' requires 'apiUrl' in config/carpet-ai-fake-player.json"
                + " (an OpenAI-compatible /chat/completions endpoint).");
        }
        if (provider instanceof GoogleProvider) {
            while (base.endsWith("/")) {
                base = base.substring(0, base.length() - 1);
            }
            // Gemini uses model-specific URL；认证走 x-goog-api-key 请求头
            url = base + "/" + config.model + ":generateContent";
        } else {
            url = base;
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