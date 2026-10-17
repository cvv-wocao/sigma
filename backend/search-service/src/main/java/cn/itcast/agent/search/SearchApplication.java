package cn.itcast.agent.search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 检索服务启动类。
 * 对外提供 Gitee 搜索能力：SearchController -> SearchService -> GiteeClient -> Gitee OpenAPI v5。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableFeignClients(basePackages = "cn.itcast.agent")
public class SearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(SearchApplication.class, args);
    }
}