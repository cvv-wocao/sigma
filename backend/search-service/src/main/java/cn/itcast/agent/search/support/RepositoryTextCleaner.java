package cn.itcast.agent.search.support;

import cn.itcast.agent.common.api.dto.GiteeRepository;
import cn.itcast.agent.search.config.RepositorySearchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Objects;

/**
 * 仓库文本清洗：把外部平台返回的简介 / README 处理成「人能读、提示词装得下」的样子。
 *
 * <p>为什么需要它：GitHub 的检索接口是对仓库做关键词匹配的（简介、标题里的词都算），
 * 于是有一批靠堆词刷排名的 SEO 垃圾仓库，会把几万个字符的填充空白、乱码和广告词塞进
 * description，借此跑到搜索结果前列。这类数据原样返回前端就是「满屏乱码」——实测搜索
 * 「商城」时排名前二的仓库简介分别是 5.9 万和 5.5 万字符，其中绝大部分是重复空白与
 * 二进制乱码；送给模型又会把排序提示词直接撑爆。</p>
 *
 * <p>清洗只做三件事，不改变语义：</p>
 * <ol>
 *   <li>把连续空白（含全角空格 U+3000、制表符等）压成一个普通空格；</li>
 *   <li>剔除控制字符与零宽 / 双向控制等不可见格式字符；</li>
 *   <li>超过上限时截断并补省略号。</li>
 * </ol>
 *
 * <p>另外 {@link #isSpam} 用来识别「靠堆词刷榜」的仓库：正常人不会把简介写到上千字符，
 * 也不会在简介里塞几十个连续空白。识别出来的仓库在检索结果里直接剔除，不出现在页面上，
 * 也不进入推荐流水线的候选池。</p>
 */
@Component
public class RepositoryTextCleaner {

    private static final Logger log = LoggerFactory.getLogger(RepositoryTextCleaner.class);

    /** 连续多少个空白就认为是「填充式刷词」 */
    private static final int WHITESPACE_RUN_LIMIT = 40;
    /** README 最多保留多少个连续换行，避免大段空行占满提示词 */
    private static final int MAX_CONSECUTIVE_NEWLINES = 2;

    private final RepositorySearchProperties properties;

    public RepositoryTextCleaner(RepositorySearchProperties properties) {
        this.properties = properties;
    }

    /**
     * 清洗仓库简介。原文为空时返回 null；清洗后为空（整段都是空白 / 不可见字符）时也返回 null。
     */
    public String sanitizeDescription(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        int max = properties.maxDescriptionLength();
        StringBuilder sb = new StringBuilder(Math.min(raw.length(), max + 8));
        boolean pendingSpace = false;
        int written = 0;
        for (int i = 0; i < raw.length(); ) {
            int codePoint = raw.codePointAt(i);
            i += Character.charCount(codePoint);
            if (isInvisible(codePoint)) {
                continue;
            }
            if (isBlank(codePoint)) {
                pendingSpace = sb.length() > 0;
                continue;
            }
            if (pendingSpace) {
                if (written >= max) {
                    return sb.append('…').toString();
                }
                sb.append(' ');
                written++;
                pendingSpace = false;
            }
            if (written >= max) {
                return sb.append('…').toString();
            }
            sb.appendCodePoint(codePoint);
            written++;
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /**
     * 清洗 README 正文：保留换行（Markdown 结构靠它），但压缩行内空白、去掉不可见字符，
     * 并把连续空行收敛到 {@link #MAX_CONSECUTIVE_NEWLINES} 行以内。
     */
    public String sanitizeReadme(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        int max = properties.maxReadmeLength();
        StringBuilder sb = new StringBuilder(Math.min(raw.length(), max + 8));
        boolean pendingSpace = false;
        int pendingNewlines = 0;
        int written = 0;
        for (int i = 0; i < raw.length(); ) {
            int codePoint = raw.codePointAt(i);
            i += Character.charCount(codePoint);
            if (codePoint == '\r') {
                continue;
            }
            if (codePoint == '\n') {
                pendingSpace = false;
                pendingNewlines = Math.min(pendingNewlines + 1, MAX_CONSECUTIVE_NEWLINES);
                continue;
            }
            if (isInvisible(codePoint)) {
                continue;
            }
            if (isBlank(codePoint)) {
                pendingSpace = sb.length() > 0;
                continue;
            }
            if (pendingNewlines > 0 && sb.length() > 0) {
                sb.append("\n".repeat(pendingNewlines));
                written += pendingNewlines;
            } else if (pendingSpace) {
                sb.append(' ');
                written++;
            }
            pendingNewlines = 0;
            pendingSpace = false;
            if (written >= max) {
                return sb.append('…').toString();
            }
            sb.appendCodePoint(codePoint);
            written++;
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /** 按需清洗仓库简介；内容没变时返回原对象，避免无意义地复制 */
    public GiteeRepository clean(GiteeRepository repository) {
        if (repository == null) {
            return null;
        }
        String cleaned = sanitizeDescription(repository.description());
        if (Objects.equals(cleaned, repository.description())) {
            return repository;
        }
        return repository.withDescription(cleaned);
    }

    /**
     * 是否为「靠堆词刷榜」的垃圾仓库。
     *
     * <p>判据只有两条，都很保守，避免误伤正常仓库：</p>
     * <ul>
     *   <li>简介原始长度超过 search.spam-description-length（默认 1000 字符）；</li>
     *   <li>简介里出现连续 {@link #WHITESPACE_RUN_LIMIT} 个空白字符（填充占位）。</li>
     * </ul>
     */
    public boolean isSpam(GiteeRepository repository) {
        if (repository == null || !Boolean.TRUE.equals(properties.spamFilterEnabled())) {
            return false;
        }
        String raw = repository.description();
        if (!StringUtils.hasText(raw)) {
            return false;
        }
        int limit = properties.spamDescriptionLength();
        if (raw.length() > limit) {
            log.debug("判定为关键词堆砌仓库（简介 {} 字符，超过上限 {}）：{}",
                    raw.length(), limit, repository.fullName());
            return true;
        }
        int run = 0;
        for (int i = 0; i < raw.length(); i++) {
            if (isBlank(raw.charAt(i))) {
                run++;
                if (run >= WHITESPACE_RUN_LIMIT) {
                    log.debug("判定为关键词堆砌仓库（简介含连续 {} 个空白）：{}",
                            run, repository.fullName());
                    return true;
                }
            } else {
                run = 0;
            }
        }
        return false;
    }

    private boolean isBlank(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }

    /** 控制字符与零宽 / 双向控制等不可见格式字符：前端看不出来，但会占长度、干扰模型 */
    private boolean isInvisible(int codePoint) {
        if (Character.isISOControl(codePoint)) {
            return true;
        }
        if (codePoint == 0x200B || codePoint == 0x200C || codePoint == 0x200D
                || codePoint == 0x200E || codePoint == 0x200F) {
            return true;
        }
        if (codePoint >= 0x202A && codePoint <= 0x202E) {
            return true;
        }
        if (codePoint >= 0x2060 && codePoint <= 0x2064) {
            return true;
        }
        return codePoint == 0xFEFF;
    }
}