package cn.itcast.agent.project.vo;

/**
 * 项目收藏对外模型。
 *
 * <p>时间统一格式化为字符串（yyyy-MM-dd HH:mm:ss），避免前后端对日期序列化格式产生隐式约定。</p>
 */
public record ProjectVO(
        Long id,
        String fullName,
        String repoOwner,
        String repoName,
        String description,
        String language,
        Integer stars,
        Integer forks,
        String defaultBranch,
        String htmlUrl,
        String note,
        Integer status,
        String createTime,
        String updateTime
) {
}