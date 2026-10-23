package cn.itcast.agent.ai.mapper;

import cn.itcast.agent.ai.entity.WechatOrder;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/** 微信支付订单 Mapper。 */
@Mapper
public interface WechatOrderMapper extends BaseMapper<WechatOrder> {

    @Select("SELECT * FROM ai_wechat_order WHERE out_trade_no = #{outTradeNo} LIMIT 1")
    WechatOrder selectByOutTradeNo(@Param("outTradeNo") String outTradeNo);

    @Update("UPDATE ai_wechat_order SET code_url = #{codeUrl}, status = 'PENDING', update_time = NOW() "
            + "WHERE out_trade_no = #{outTradeNo} AND status = 'PENDING'")
    int updateCodeUrl(@Param("outTradeNo") String outTradeNo, @Param("codeUrl") String codeUrl);

    @Update("UPDATE ai_wechat_order SET status = 'PAID', transaction_id = #{transactionId}, "
            + "paid_time = #{paidTime}, update_time = NOW() "
            + "WHERE out_trade_no = #{outTradeNo} AND status <> 'PAID'")
    int markPaid(@Param("outTradeNo") String outTradeNo,
                 @Param("transactionId") String transactionId,
                 @Param("paidTime") LocalDateTime paidTime);

    @Update("UPDATE ai_wechat_order SET status = 'CLOSED', update_time = NOW() "
            + "WHERE out_trade_no = #{outTradeNo} AND status = 'PENDING'")
    int markClosed(@Param("outTradeNo") String outTradeNo);

    @Update("UPDATE ai_wechat_order SET status = 'FAILED', update_time = NOW() "
            + "WHERE out_trade_no = #{outTradeNo} AND status = 'PENDING'")
    int markFailed(@Param("outTradeNo") String outTradeNo);
}
