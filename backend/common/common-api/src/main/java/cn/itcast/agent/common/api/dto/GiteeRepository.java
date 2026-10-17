package cn.itcast.agent.common.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 仓库摘要，服务间共享模型。
 *
 * <p>search-service 调用外部代码托管平台（Gitee / GitHub）后统一转换成本对象返回，
 * ai-service 通过 Feign 消费。放在 common-api 而不是某个服务的内部 dto 包，
 * 是为了让接口契约只有一份，避免调用方与提供方各写一套而漂移。</p>
 *
 * <p>命名约定：对外 JSON 字段统一使用驼峰（fullName / stars ...）。Gitee 原始返回的
 * snake_case 字段名通过 JsonAlias 声明为反序列化别名；GitHub 的响应结构差异较大，
 * 在 GitHubClient 中手工映射，因此不依赖别名。</p>
 *
 * @param name          仓库名
 * @param fullName      仓库全名 owner/repo，去重与前端路由都以它为准
 * @param description   仓库简介，可能为 null
 * @param language      主要语言，可能为 null（例如纯文档仓库）
 * @param stars         Star 数
 * @param forks         Fork 数
 * @param defaultBranch 默认分支
 * @param htmlUrl       仓库网页地址
 * @param license       开源协议名称，缺失为 null
 * @param topics        主题标签（GitHub 提供，Gitee 通常为空）
 * @param updatedAt     最近推送时间，ISO-8601 字符串，缺失为 null
 * @param source        数据来源平台：gitee / github，供前端展示与排查使用
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GiteeRepository(
        String name,
        @JsonAlias("full_name") String fullName,
        String description,
        String language,
        @JsonAlias("stargazers_count") Integer stars,
        @JsonAlias("forks_count") Integer forks,
        @JsonAlias("default_branch") String defaultBranch,
        @JsonAlias("html_url") String htmlUrl,
        String license,
        List<String> topics,
        @JsonAlias("updated_at") String updatedAt,
        String source
) {

    /**
     * 复制一份并标注数据来源。
     * Gitee 是直接反序列化得到本对象的，拿不到来源标记，由 provider 统一补上。
     */
    public GiteeRepository withSource(String source) {
        return new GiteeRepository(name, fullName, description, language, stars, forks,
                defaultBranch, htmlUrl, license, topics, updatedAt, source);
    }

    /**
     * 复制一份并替换简介。
     * 外部平台的简介可能是一大段填充空白或乱码（关键词堆砌仓库），
     * 由清洗组件统一处理后用本方法生成新对象，避免调用方到处手动 new。
     */
    public GiteeRepository withDescription(String newDescription) {
        return new GiteeRepository(name, fullName, newDescription, language, stars, forks,
                defaultBranch, htmlUrl, license, topics, updatedAt, source);
    }
}