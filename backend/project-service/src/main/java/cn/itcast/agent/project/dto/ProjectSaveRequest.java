package cn.itcast.agent.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 收藏项目请求。
 *
 * <p>仓库的客观信息由前端从检索结果带过来，收藏后作为快照保存；
 * 除 owner/repo 外都可以不传。</p>
 */
public record ProjectSaveRequest(

        @NotBlank(message = "仓库命名空间不能为空")
        @Size(max = 100, message = "仓库命名空间长度不能超过 100 个字符")
        String repoOwner,

        @NotBlank(message = "仓库名不能为空")
        @Size(max = 100, message = "仓库名长度不能超过 100 个字符")
        String repoName,

        @Size(max = 500, message = "简介长度不能超过 500 个字符")
        String description,

        @Size(max = 50, message = "语言长度不能超过 50 个字符")
        String language,

        @PositiveOrZero(message = "Star 数不能为负数")
        Integer stars,

        @PositiveOrZero(message = "Fork 数不能为负数")
        Integer forks,

        @Size(max = 100, message = "默认分支长度不能超过 100 个字符")
        String defaultBranch,

        @Size(max = 255, message = "仓库地址长度不能超过 255 个字符")
        String htmlUrl,

        @Size(max = 500, message = "备注长度不能超过 500 个字符")
        String note
) {
}