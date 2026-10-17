package cn.itcast.agent.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * AI 智能体服务启动类。
 * 对外提供 /ai/chat（普通对话）、/ai/agent（带工具调用与会话记忆的 Agent 对话）
 * 与 /ai/recommend（需求分析 -> 检索 -> 排序的推荐流水线）。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableFeignClients(basePackages = "cn.itcast.agent")
public class AiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiApplication.class, args);
    }
}