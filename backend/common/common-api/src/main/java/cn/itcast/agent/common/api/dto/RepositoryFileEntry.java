package cn.itcast.agent.common.api.dto;

/**
 * 仓库目录项，服务间共享模型。
 *
 * @param name    文件或目录名
 * @param path    相对仓库根目录的路径
 * @param type    file / dir / submodule 等
 * @param size    文件大小（字节）；目录为 null
 * @param htmlUrl 平台网页地址，便于用户直接打开
 */
public record RepositoryFileEntry(
        String name,
        String path,
        String type,
        Long size,
        String htmlUrl
) {
}
