package cn.itcast.agent.common.api.dto;

import java.util.List;

/**
 * 仓库目录列表，服务间共享模型。
 *
 * @param fullName  仓库全名 owner/repo
 * @param path      当前目录，根目录为空字符串
 * @param branch    查询分支；为空表示来源平台的默认分支
 * @param entries   目录项（服务端按上限截断）
 * @param truncated 是否因为条目过多被截断
 * @param total     截断前的条目数
 */
public record RepositoryFileListing(
        String fullName,
        String path,
        String branch,
        List<RepositoryFileEntry> entries,
        boolean truncated,
        int total
) {
}
