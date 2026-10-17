package cn.itcast.agent.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 长期记忆配置，对应 application.yml 中的 ai.memory.* 。
 *
 * <p>与短期记忆（ai.chat.memory.*）的分工：短期记忆是 Redis 里的滑动窗口，管「本次会话说了什么」；
 * 长期记忆是 MySQL 里的用户事实库，管「这个用户是谁」，跨会话保留、按相关度注入。</p>
 *
 * <p>取值同样在紧凑构造器里兜底与夹紧：召回条数与候选池决定提示词体积，抽取条数与单用户上限
 * 决定写入频率，配错时这里会自动收敛到一个安全区间。</p>
 *
 * @param enabled         长期记忆总开关；关闭后既不抽取也不注入，短期记忆不受影响
 * @param recallTopK      每轮最多注入几条记忆（上限 20）
 * @param candidateLimit  打分前按「重要度 + 更新时间」取多少条作为候选池（上限 500）
 * @param minScore        低于该分数的记忆不注入（取值 0-1），避免把无关内容塞进提示词
 * @param extractMaxItems 单轮问答最多抽取几条新记忆（上限 10）
 * @param maxPerUser      单用户记忆总量上限，超出后按分数淘汰最差的（上限 2000）
 * @param embedding       可选：向量召回配置，不配则退回「关键词 + 重要度 + 时效」打分
 */
@ConfigurationProperties(prefix = "ai.memory")
public record MemoryProperties(Boolean enabled, Integer recallTopK, Integer candidateLimit, Double minScore,
                               Integer extractMaxItems, Integer maxPerUser, Embedding embedding) {

    private static final int RECALL_TOP_K_LIMIT = 20;
    private static final int CANDIDATE_LIMIT_MAX = 500;
    private static final int EXTRACT_ITEMS_LIMIT = 10;
    private static final int MAX_PER_USER_LIMIT = 2000;

    public MemoryProperties {
        if (enabled == null) {
            enabled = Boolean.TRUE;
        }
        recallTopK = clamp(recallTopK, 6, 1, RECALL_TOP_K_LIMIT);
        candidateLimit = clamp(candidateLimit, 100, recallTopK, CANDIDATE_LIMIT_MAX);
        extractMaxItems = clamp(extractMaxItems, 3, 1, EXTRACT_ITEMS_LIMIT);
        maxPerUser = clamp(maxPerUser, 200, 1, MAX_PER_USER_LIMIT);
        if (minScore == null || minScore < 0 || minScore > 1) {
            minScore = 0.2;
        }
        if (embedding == null) {
            embedding = new Embedding(null, null, null, null, null);
        }
    }

    /** 是否开启长期记忆 */
    public boolean active() {
        return Boolean.TRUE.equals(enabled);
    }

    private static int clamp(Integer value, int defaultValue, int min, int max) {
        if (value == null || value < min) {
            return defaultValue;
        }
        return Math.min(value, max);
    }

    /**
     * 向量召回配置：任意「OpenAI 兼容」的 embeddings 接口（SiliconFlow、阿里云百炼兼容模式、
     * OpenAI、本地 vLLM / Ollama 的兼容网关等）都能用，只要它支持 POST {base-url}/embeddings 。
     *
     * @param enabled 是否启用向量召回
     * @param baseUrl 接口根地址，例如 https://api.siliconflow.cn/v1
     * @param apiKey  访问密钥，以 Bearer 方式放在 Authorization 头里
     * @param model   向量模型名，例如 BAAI/bge-m3、text-embedding-v4
     * @param timeout 单次向量化调用的超时时间
     */
    public record Embedding(Boolean enabled, String baseUrl, String apiKey, String model, Duration timeout) {

        public Embedding {
            if (enabled == null) {
                enabled = Boolean.FALSE;
            }
            if (timeout == null) {
                timeout = Duration.ofSeconds(10);
            }
        }

        /**
         * 四项都配齐才算可用：只把开关打开而没填地址、密钥、模型时按未启用处理，
         * 避免启动后每轮都打一次注定失败的请求，也避免把失败伪装成「召回为空」。
         */
        public boolean usable() {
            return Boolean.TRUE.equals(enabled)
                    && hasText(baseUrl)
                    && hasText(apiKey)
                    && hasText(model);
        }

        private static boolean hasText(String value) {
            return value != null && !value.isBlank();
        }
    }
}
