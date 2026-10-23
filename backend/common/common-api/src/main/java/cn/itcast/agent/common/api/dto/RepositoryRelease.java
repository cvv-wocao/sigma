package cn.itcast.agent.common.api.dto;

import java.util.List;

/**
 * 仓库 Release，服务间共享模型。
 *
 * <p>用户问「安装包在哪里」时，真正需要的是这里列出的 assets，
 * 而不是仓库源码 ZIP。发布说明 body 会由 search-service 截断，避免撑大提示词。</p>
 *
 * @param tagName       Git 标签，例如 v0.74.5
 * @param name          Release 名称，可能为空
 * @param prerelease    是否为预发布版本
 * @param publishedAt   发布时间，ISO-8601 字符串
 * @param htmlUrl       Release 详情页地址
 * @param body          发布说明摘要，服务端已按上限截断
 * @param bodyTruncated 发布说明是否被截断
 * @param assetCount    附件总数（assets 列表可能只保留前若干项）
 * @param assets        附件列表
 */
public record RepositoryRelease(
        String tagName,
        String name,
        boolean prerelease,
        String publishedAt,
        String htmlUrl,
        String body,
        boolean bodyTruncated,
        int assetCount,
        List<RepositoryReleaseAsset> assets
) {
}
