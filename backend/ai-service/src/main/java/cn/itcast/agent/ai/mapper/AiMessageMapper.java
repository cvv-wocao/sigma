package cn.itcast.agent.ai.mapper;

import cn.itcast.agent.ai.entity.AiMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会话消息表 Mapper。
 * 单表增删改查直接使用 BaseMapper 提供的方法；
 * 复杂 SQL 按约定写在 resources/mapper/AiMessageMapper.xml 中，并在此接口声明方法。
 */
@Mapper
public interface AiMessageMapper extends BaseMapper<AiMessage> {
}