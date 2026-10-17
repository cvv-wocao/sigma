package cn.itcast.agent.search.provider;

/**
 * 一次关键词检索的完整条件。
 *
 * <p>为什么要有它：平台检索接口除了关键词，还能接受语言 / Star / 推送时间等限定符
 * （GitHub 的 language: / stars: / pushed:）。把这些条件放进查询对象，
 * 来源实现就能在「发起请求时」把它们拼进查询串，而不是拿回来再本地过滤——
 * 平台默认按 Star 降序返回，本地过滤时早就看不到冷门仓库了。</p>
 *
 * <p>不支持的来源可以直接忽略这些字段（见 {@link RepositorySearchProvider#search(KeywordQuery)}）。</p>
 *
 * @param keyword     关键词
 * @param page        页码，从 1 开始
 * @param perPage     每页条数
 * @param language    语言限定，null 表示不限
 * @param minStars    Star 数下限，null 表示不限
 * @param pushedAfter 只保留该日期之后有推送的仓库，格式 yyyy-MM-dd，null 表示不限
 */
public record KeywordQuery(String keyword, int page, int perPage,
                           String language, Integer minStars, String pushedAfter) {

    /** 不带任何限定条件的检索条件 */
    public static KeywordQuery of(String keyword, int page, int perPage) {
        return new KeywordQuery(keyword, page, perPage, null, null, null);
    }
}