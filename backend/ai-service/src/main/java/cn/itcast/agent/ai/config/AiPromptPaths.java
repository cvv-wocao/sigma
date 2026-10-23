package cn.itcast.agent.ai.config;

/** 提示词文件路径。平台默认模型与用户自定义模型共用同一套提示词。 */
public final class AiPromptPaths {

    public static final String CHAT = "prompts/chat-system.txt";
    public static final String AGENT = "prompts/agent-system.txt";
    public static final String RECOMMEND_ANALYSIS = "prompts/recommend-analysis.txt";
    public static final String RECOMMEND_RANK = "prompts/recommend-rank.txt";
    public static final String MEMORY_EXTRACT = "prompts/memory-extract.txt";

    private AiPromptPaths() {
    }
}