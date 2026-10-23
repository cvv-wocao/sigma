package cn.itcast.agent.common.api.dto;

/**
 * Release 附件（安装包、压缩包、校验文件等），服务间共享模型。
 *
 * @param name          附件文件名，例如 fzf-0.74.5-windows_amd64.zip
 * @param contentType   文件类型，来源平台未提供时为 null
 * @param size          文件大小（字节），来源平台未提供时为 null
 * @param downloadCount 下载次数，来源平台未提供时为 null
 * @param downloadUrl   浏览器可直接下载的地址
 */
public record RepositoryReleaseAsset(
        String name,
        String contentType,
        Long size,
        Long downloadCount,
        String downloadUrl
) {
}
