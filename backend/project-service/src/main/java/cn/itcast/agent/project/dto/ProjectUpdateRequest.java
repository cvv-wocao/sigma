package cn.itcast.agent.project.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 更新收藏项目请求。
 *
 * <p>只更新非 null 字段，因此前端可以只提交备注，或只改状态。
 * 仓库身份（owner/repo）不可修改：改了就是另一个项目，应该重新收藏。</p>
 */
public record ProjectUpdateRequest(

        @Size(max = 500, message = "简介长度不能超过 500 个字符")
        String description,

        @Size(max = 500, message = "备注长度不能超过 500 个字符")
        String note,

        @Min(value = 0, message = "状态取值只能是 0 或 1")
        @Max(value = 1, message = "状态取值只能是 0 或 1")
        Integer status
) {
}