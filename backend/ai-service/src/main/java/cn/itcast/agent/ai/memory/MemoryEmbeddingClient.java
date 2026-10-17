package cn.itcast.agent.ai.memory;

import cn.itcast.agent.ai.config.MemoryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 可选的向量化客户端：把一段文本转成向量，供长期记忆做语义召回。
 *
 * <p>刻意不引入向量库 SDK：只要对方提供「OpenAI 兼容」的 embeddings 接口
 * （SiliconFlow、阿里云百炼兼容模式、OpenAI、本地 vLLM / Ollama 兼容网关都算），
 * 这里就能用。用 JDK 自带的 HttpClient 直接发请求，少一层依赖与版本兼容问题。</p>
 *
 * <p>约定：任何失败都只记日志并返回空，由调用方退回「关键词 + 重要度 + 时效」打分。
 * 向量化是锦上添花的能力，不应该让一次网络抖动影响正常对话。</p>
 */
@Component
public class MemoryEmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(MemoryEmbeddingClient.class);

    /** 向量维度上限：挡掉返回超长数组的异常响应，避免把数据库撑爆 */
    private static final int MAX_DIMENSION = 8192;

    private final MemoryProperties properties;
    private final JsonMapper jsonMapper;

    /** 未配置时保持为 null，enabled() 据此判断 */
    private final HttpClient httpClient;
    private final URI endpoint;

    public MemoryEmbeddingClient(MemoryProperties properties, JsonMapper jsonMapper) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        MemoryProperties.Embedding embedding = properties.embedding();
        if (embedding.usable()) {
            Duration timeout = embedding.timeout();
            this.httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
            this.endpoint = URI.create(trimTrailingSlash(embedding.baseUrl()) + "/embeddings");
            log.info("长期记忆已启用向量召回：model={}, endpoint={}", embedding.model(), this.endpoint);
        } else {
            this.httpClient = null;
            this.endpoint = null;
        }
    }

    /** 是否可用：未配置或配置不全时为 false，调用方据此选择召回策略 */
    public boolean enabled() {
        return httpClient != null;
    }

    /**
     * 把文本转成向量。
     *
     * @return 向量；未启用、文本为空或调用失败时返回空
     */
    public Optional<float[]> embed(String text) {
        if (httpClient == null || !StringUtils.hasText(text)) {
            return Optional.empty();
        }
        try {
            String body = jsonMapper.writeValueAsString(Map.of(
                    "model", properties.embedding().model(),
                    "input", text));
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(properties.embedding().timeout())
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + properties.embedding().apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                log.warn("向量化接口返回非 2xx，本轮退回关键词召回：status={}", response.statusCode());
                return Optional.empty();
            }
            return parse(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            log.warn("向量化调用失败，本轮退回关键词召回：{}", e.getMessage());
            return Optional.empty();
        }
    }

    /** 解析 OpenAI 兼容响应 {"data":[{"embedding":[...]}]}，只取第一条 */
    private Optional<float[]> parse(String body) {
        try {
            Map<?, ?> root = jsonMapper.readValue(body, Map.class);
            Object data = root.get("data");
            if (!(data instanceof List<?> items) || items.isEmpty()) {
                return Optional.empty();
            }
            if (!(items.get(0) instanceof Map<?, ?> item)) {
                return Optional.empty();
            }
            if (!(item.get("embedding") instanceof List<?> values) || values.isEmpty()
                    || values.size() > MAX_DIMENSION) {
                return Optional.empty();
            }
            float[] vector = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                if (!(values.get(i) instanceof Number number)) {
                    return Optional.empty();
                }
                vector[i] = number.floatValue();
            }
            return Optional.of(vector);
        } catch (Exception e) {
            log.warn("向量化响应解析失败，本轮退回关键词召回：{}", e.getMessage());
            return Optional.empty();
        }
    }

    private static String trimTrailingSlash(String value) {
        String trimmed = value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
