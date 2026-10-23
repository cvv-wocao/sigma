package cn.itcast.agent.common.api.dto;

/**
 * 仓库单文件内容，服务间共享模型。
 *
 * @param fullName  仓库全名 owner/repo
 * @param path      文件相对路径
 * @param branch    查询分支；为空表示来源平台的默认分支
 * @param content   文本正文；二进制、目录或文件过大时为 null
 * @param size      文件大小（字节），来源未提供时为 null
 * @param truncated 内容是否因为长度上限被截断
 * @param binary    是否为二进制文件（不做文本解码）
 * @param directory 指定路径是否为目录
 * @param tooLarge  文件是否超过允许读取的大小
 */
public record RepositoryFileContent(
        String fullName,
        String path,
        String branch,
        String content,
        Long size,
        boolean truncated,
        boolean binary,
        boolean directory,
        boolean tooLarge
) {
}
