package cn.itcast.agent.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 用户服务启动类。
 * 当前阶段只提供启动入口，业务实现分布在 controller / service / mapper / security 等包中（待实现）。
 */
@SpringBootApplication
public class UserApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}