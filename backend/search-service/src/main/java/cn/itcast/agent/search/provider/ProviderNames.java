package cn.itcast.agent.search.provider;

/**
 * 检索来源标识常量。
 * 该值会写入 {@link cn.itcast.agent.common.api.dto.GiteeRepository#source()}，
 * 前端据此展示来源，排查问题时也能一眼看出数据来自哪个平台。
 */
public final class ProviderNames {

    public static final String GITHUB = "github";
    public static final String GITEE = "gitee";

    private ProviderNames() {
    }
}