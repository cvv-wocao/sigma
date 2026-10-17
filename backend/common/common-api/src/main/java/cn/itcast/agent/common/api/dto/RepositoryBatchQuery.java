package cn.itcast.agent.common.api.dto;

import java.util.List;

/**
 * 多关键词批量检索入参，供「AI 推荐」流水线一次性召回候选池使用。
 *
 * <p>与单关键词检索的区别：调用方一次给出多个关键词，由 search-service 逐个检索后
 * 按 fullName 去重合并，并施加语言 / Star 数 / 最近推送等过滤条件，避免调用方
 * 对同一接口发起 N 次请求。</p>
 *
 * <p>过滤条件里 language / minStars / pushedAfter 会尽量「下推」给外部平台的检索接口
 * （GitHub 支持 language: / stars: / pushed: 限定符），而不是拿回来再本地筛：
 * 平台默认按 Star 降序返回，不下推就只能看到每个关键词最热的十几条，
 * 冷门但贴合需求的仓库会被挡在门外。</p>
 *
 * @param keywords    关键词列表，逐个检索后合并去重；空白项会被忽略
 * @param perKeyword  每个关键词取多少条（建议 10-20）
 * @param language    语言过滤，null 表示不限；匹配时忽略大小写
 * @param minStars    Star 数下限，null 表示不限
 * @param pushedAfter 只保留该日期之后有推送的仓库，格式 yyyy-MM-dd，null 表示不限
 * @param maxResults  合并去重后最多返回多少条，null 表示使用服务端默认上限
 */
public record RepositoryBatchQuery(
        List<String> keywords,
        Integer perKeyword,
        String language,
        Integer minStars,
        String pushedAfter,
        Integer maxResults
) {
}