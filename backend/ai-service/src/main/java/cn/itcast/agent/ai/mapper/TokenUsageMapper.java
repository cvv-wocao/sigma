package cn.itcast.agent.ai.mapper;

import cn.itcast.agent.ai.entity.TokenUsage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/** Token 使用流水 Mapper。 */
@Mapper
public interface TokenUsageMapper extends BaseMapper<TokenUsage> {

    @Select("""
            SELECT COUNT(*) AS requestCount,
                   COALESCE(SUM(prompt_tokens), 0) AS promptTokens,
                   COALESCE(SUM(completion_tokens), 0) AS completionTokens,
                   COALESCE(SUM(total_tokens), 0) AS totalTokens,
                   COALESCE(SUM(input_cost_micros), 0) AS inputCostMicros,
                   COALESCE(SUM(output_cost_micros), 0) AS outputCostMicros,
                   COALESCE(SUM(input_cost_micros + output_cost_micros), 0) AS usageCostMicros,
                   COALESCE(SUM(CASE WHEN platform_token = 1 THEN input_cost_micros + output_cost_micros ELSE 0 END), 0) AS platformUsageCostMicros,
                   COALESCE(SUM(CASE WHEN platform_token = 0 THEN input_cost_micros + output_cost_micros ELSE 0 END), 0) AS selfKeyUsageCostMicros,
                   COALESCE(SUM(cost_micros), 0) AS costMicros,
                   COALESCE(SUM(CASE WHEN cost_micros > 0 THEN 1 ELSE 0 END), 0) AS chargedRequestCount,
                   COALESCE(SUM(CASE WHEN platform_token = 1 THEN 1 ELSE 0 END), 0) AS platformRequestCount,
                   COALESCE(SUM(CASE WHEN platform_token = 0 THEN 1 ELSE 0 END), 0) AS selfKeyRequestCount,
                   COALESCE(AVG(duration_ms), 0) AS avgDurationMs,
                   COALESCE(MAX(duration_ms), 0) AS maxDurationMs,
                   MIN(create_time) AS firstTime,
                   MAX(create_time) AS lastTime
            FROM ai_token_usage
            WHERE user_id = #{userId}
              AND create_time >= #{since}
            """)
    TokenUsageSummaryRow summarize(@Param("userId") Long userId, @Param("since") LocalDateTime since);
}
