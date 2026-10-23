package cn.itcast.agent.ai.mapper;

import cn.itcast.agent.ai.entity.RechargeRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** 管理员充值记录 Mapper。 */
@Mapper
public interface RechargeRecordMapper extends BaseMapper<RechargeRecord> {
}