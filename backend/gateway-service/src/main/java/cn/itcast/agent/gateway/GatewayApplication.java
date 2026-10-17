package cn.itcast.agent.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 网关服务启动类。
 * 当前阶段只提供启动入口，路由规则见 resources/application.yml，
 * 全局过滤器与异常处理分别放在 filter / handler 包中（待实现）。
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}