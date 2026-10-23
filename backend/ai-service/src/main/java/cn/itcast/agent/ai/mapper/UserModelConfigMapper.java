package cn.itcast.agent.ai.mapper;

import cn.itcast.agent.ai.entity.UserModelConfig;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** 用户模型配置 Mapper，按 user_id 唯一。 */
@Mapper
public interface UserModelConfigMapper extends BaseMapper<UserModelConfig> {
}