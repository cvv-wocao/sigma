package cn.itcast.agent.ai.mapper;

import cn.itcast.agent.ai.entity.AlipayOrder;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/** 支付宝当面付订单 Mapper。 */
@Mapper
public interface AlipayOrderMapper extends BaseMapper<AlipayOrder> {

    @Select("SELECT * FROM ai_alipay_order WHERE out_trade_no = #{outTradeNo} LIMIT 1")
    AlipayOrder selectByOutTradeNo(@Param("outTradeNo") String outTradeNo);

    @Update("UPDATE ai_alipay_order SET qr_code = #{qrCode}, update_time = NOW() "
            + "WHERE out_trade_no = #{outTradeNo} AND status = 'PENDING'")
    int updateQrCode(@Param("outTradeNo") String outTradeNo, @Param("qrCode") String qrCode);

    @Update("UPDATE ai_alipay_order SET status = 'PAID', trade_no = #{tradeNo}, "
            + "paid_time = #{paidTime}, last_checked_time = NOW(), update_time = NOW() "
            + "WHERE out_trade_no = #{outTradeNo} AND status <> 'PAID'")
    int markPaid(@Param("outTradeNo") String outTradeNo,
                 @Param("tradeNo") String tradeNo,
                 @Param("paidTime") LocalDateTime paidTime);

    @Update("UPDATE ai_alipay_order SET status = 'CLOSED', last_checked_time = NOW(), update_time = NOW() "
            + "WHERE out_trade_no = #{outTradeNo} AND status = 'PENDING'")
    int markClosed(@Param("outTradeNo") String outTradeNo);

    @Update("UPDATE ai_alipay_order SET status = 'FAILED', last_checked_time = NOW(), update_time = NOW() "
            + "WHERE out_trade_no = #{outTradeNo} AND status = 'PENDING'")
    int markFailed(@Param("outTradeNo") String outTradeNo);

    @Update("UPDATE ai_alipay_order SET last_checked_time = NOW() WHERE out_trade_no = #{outTradeNo}")
    int touchChecked(@Param("outTradeNo") String outTradeNo);

    @Select("SELECT * FROM ai_alipay_order WHERE status = 'PENDING' "
            + "AND create_time > DATE_SUB(NOW(), INTERVAL 1 DAY) "
            + "ORDER BY id LIMIT #{limit}")
    List<AlipayOrder> selectPendingForReconcile(@Param("limit") int limit);
}
