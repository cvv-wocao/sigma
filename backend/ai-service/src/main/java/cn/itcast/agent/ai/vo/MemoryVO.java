package cn.itcast.agent.ai.vo;

import java.util.List;

/**
 * 长期记忆条目响应。
 *
 * <p>这是「给用户看」的视图：用户可以据此确认模型记住了什么，也可以一键清空。
 * 向量本身不返回，对前端没有意义且体积很大。</p>
 *
 * @param id         记忆 ID
 * @param content    记忆正文
 * @param category   记忆类别（PROFILE / PREFERENCE / GOAL / CONSTRAINT / FACT）
 * @param importance 重要度 1-5
 * @param keywords   召回用关键词
 * @param hitCount   被召回注入提示词的次数
 * @param createTime 创建时间
 * @param updateTime 最后更新时间
 */
public record MemoryVO(Long id, String content, String category, Integer importance, List<String> keywords,
                       Integer hitCount, String createTime, String updateTime) {
}
