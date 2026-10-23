package cn.itcast.agent.search.client;

import java.io.IOException;
import java.io.InputStream;

/**
 * 外部代码托管平台返回的仓库归档流。
 *
 * <p>归档可能很大，因此这里只持有输入流和少量元数据，不把 ZIP 读进内存。
 * 调用方负责在响应写完后关闭本对象，释放底层 HTTP 连接。</p>
 *
 * @param inputStream   ZIP 内容流
 * @param contentLength 上游声明的字节数，未知时为负数
 * @param contentType   上游声明的媒体类型
 * @param fileName      下载到浏览器时使用的文件名
 */
public record RepositoryArchiveStream(
        InputStream inputStream,
        long contentLength,
        String contentType,
        String fileName
) implements AutoCloseable {

    @Override
    public void close() throws IOException {
        inputStream.close();
    }
}
