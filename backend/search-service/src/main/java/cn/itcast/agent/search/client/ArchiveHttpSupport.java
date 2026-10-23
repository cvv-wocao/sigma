package cn.itcast.agent.search.client;

import cn.itcast.agent.common.core.exception.BusinessException;
import cn.itcast.agent.common.core.result.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 仓库归档下载的公共 HTTP 支持。
 *
 * <p>检索接口用 RestClient 拿 JSON 足够，但归档下载必须拿到原始输入流，
 * 不能先反序列化成 byte[]，否则几百 MB 的仓库会直接压垮服务。这里使用
 * JDK HttpClient 的 {@code BodyHandlers.ofInputStream()}，边接收边交给响应输出流。</p>
 */
final class ArchiveHttpSupport {

    private static final Logger log = LoggerFactory.getLogger(ArchiveHttpSupport.class);

    private static final String USER_AGENT = "java-agent-platform/search-service";
    private static final int ERROR_BODY_LIMIT = 512;

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private ArchiveHttpSupport() {
    }

    /**
     * 打开远程归档流。只有上游返回 2xx 才返回结果；其余状态转换成可读的业务异常，
     * 避免把 HTML 错误页当成 ZIP 下载给用户。
     */
    static RepositoryArchiveStream open(String url, String fileName) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/zip, application/octet-stream")
                .GET()
                .build();

        HttpResponse<InputStream> response;
        try {
            response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.SERVER_ERROR, "下载任务被中断，请重试");
        } catch (IOException e) {
            log.warn("连接仓库归档地址失败：url={}, error={}", url, e.getMessage());
            throw new BusinessException(ErrorCode.SERVER_ERROR, "连接代码托管平台失败，请稍后重试");
        }

        int status = response.statusCode();
        String contentType = response.headers().firstValue("Content-Type").orElse("application/zip");
        if (status < 200 || status >= 300) {
            String detail = readErrorBody(response.body());
            log.warn("仓库归档下载失败：status={}, url={}, detail={}", status, url, detail);
            try {
                response.body().close();
            } catch (IOException ignored) {
                // 读取错误响应失败不影响给前端返回主错误
            }
            if (status == 404) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "仓库或分支不存在，无法下载");
            }
            if (status == 401 || status == 403) {
                throw new BusinessException(ErrorCode.SERVER_ERROR, "代码托管平台拒绝下载或触发限流，请稍后重试");
            }
            if (status == 429) {
                throw new BusinessException(ErrorCode.SERVER_ERROR, "代码托管平台下载频率超限，请稍后重试");
            }
            throw new BusinessException(ErrorCode.SERVER_ERROR, "下载仓库失败，上游状态码：" + status);
        }
        if (contentType.toLowerCase().startsWith("text/html")) {
            try {
                response.body().close();
            } catch (IOException ignored) {
                // 同上的清理逻辑
            }
            throw new BusinessException(ErrorCode.SERVER_ERROR, "上游返回了网页而不是 ZIP，仓库可能不存在或不是公开仓库");
        }

        long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
        return new RepositoryArchiveStream(response.body(), contentLength, contentType, fileName);
    }

    /** 对 URL 路径片段做编码，避免 owner/repo 中的特殊字符改变请求路径 */
    static String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** 分支名允许包含 /，逐段编码后再拼回去 */
    static String encodeRef(String ref) {
        return Arrays.stream(ref.split("/", -1))
                .map(ArchiveHttpSupport::encodePathSegment)
                .collect(Collectors.joining("/"));
    }

    /** 生成浏览器保存时的文件名，去掉路径分隔符和控制字符 */
    static String archiveFileName(String owner, String repo, String branch) {
        String raw = owner + "-" + repo + "-" + branch + ".zip";
        return raw.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String readErrorBody(InputStream inputStream) {
        try (inputStream) {
            byte[] bytes = inputStream.readNBytes(ERROR_BODY_LIMIT);
            return new String(bytes, StandardCharsets.UTF_8).replaceAll("\\s+", " ").trim();
        } catch (IOException e) {
            return "";
        }
    }
}
