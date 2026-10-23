package cn.itcast.agent.search.support;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;

/**
 * 仓库文件内容的解码与二进制判定。
 *
 * <p>外部平台的 contents 接口统一返回 base64。直接按字符串回给模型有两个问题：
 * 二进制文件会变成乱码、超大文件会撑爆请求。因此这里先解码并做保守的二进制判断，
 * 真正的长度截断由 SearchService 统一完成。</p>
 */
public final class RepositoryContentSupport {

    /** 允许读取的原始文件上限；超过后不再解码，避免把超大文件读进内存 */
    public static final long MAX_FILE_BYTES = 512 * 1024L;

    /** 明确按二进制处理的扩展名，覆盖常见图片、压缩包、可执行文件与字体 */
    private static final Set<String> BINARY_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "gif", "webp", "ico", "bmp", "svgz",
            "pdf", "zip", "gz", "tgz", "bz2", "xz", "7z", "rar", "tar",
            "jar", "war", "ear", "class", "exe", "dll", "so", "dylib", "bin",
            "mp3", "mp4", "avi", "mov", "wav", "flac", "woff", "woff2", "ttf", "otf",
            "db", "sqlite", "sqlite3", "lockb"
    );

    private RepositoryContentSupport() {
    }

    /** 扩展名是否属于明确的二进制类型（内容判定仍是兜底） */
    public static boolean isBinaryPath(String path) {
        if (path == null) {
            return false;
        }
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot == path.length() - 1) {
            return false;
        }
        return BINARY_EXTENSIONS.contains(path.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    /** 解码 base64 内容；格式非法时返回 null，由调用方转为可读错误 */
    public static byte[] decodeBase64(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            return Base64.getMimeDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 保守判定字节内容是否为二进制。
     * 只看前 8KB：出现 NUL 直接判定；控制字符比例明显偏高也判定为二进制。
     */
    public static boolean looksBinary(byte[] data) {
        if (data == null || data.length == 0) {
            return false;
        }
        int sampleSize = Math.min(data.length, 8192);
        int suspicious = 0;
        for (int i = 0; i < sampleSize; i++) {
            int value = data[i] & 0xFF;
            if (value == 0) {
                return true;
            }
            // 保留换行、制表、回车等常见空白，其余控制字符计入可疑数
            if (value < 0x09 || (value > 0x0D && value < 0x20)) {
                suspicious++;
            }
        }
        return suspicious > 8 || suspicious * 100 > sampleSize;
    }

    /** 把已通过二进制判定的字节转成 UTF-8 文本 */
    public static String toText(byte[] data) {
        return new String(data, StandardCharsets.UTF_8);
    }
}
