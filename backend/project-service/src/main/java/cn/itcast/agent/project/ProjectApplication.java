package cn.itcast.agent.project;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 项目服务启动类。
 * 对外提供项目收藏能力：ProjectController -> ProjectService -> ProjectMapper -> MySQL。
 *
 * <p>本服务不直接访问任何外部数据源：仓库的客观信息由前端从 search-service 的检索结果带过来，
 * 收藏后作为快照保存。需要刷新时再单独引入对 search-service 的调用。</p>
 */
@SpringBootApplication
public class ProjectApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProjectApplication.class, args);
    }
}