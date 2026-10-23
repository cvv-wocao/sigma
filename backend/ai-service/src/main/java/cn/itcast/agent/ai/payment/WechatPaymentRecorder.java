package cn.itcast.agent.ai.payment;

import cn.itcast.agent.ai.entity.RechargeRecord;
import cn.itcast.agent.ai.entity.WechatOrder;
import cn.itcast.agent.ai.mapper.RechargeRecordMapper;
import cn.itcast.agent.ai.mapper.UserWalletMapper;
import cn.itcast.agent.ai.mapper.WechatOrderMapper;
import cn.itcast.agent.ai.payment.dto.WechatTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 支付成功入账。
 *
 * <p>独立成一个 Bean 是为了让 {@code @Transactional} 真正经过 Spring 代理。
 * 订单状态使用条件更新保证回调重复投递时只入账一次。</p>
 */
@Service
public class WechatPaymentRecorder {

    private static final Logger log = LoggerFactory.getLogger(WechatPaymentRecorder.class);

    private final WechatOrderMapper orderMapper;
    private final UserWalletMapper walletMapper;
    private final RechargeRecordMapper rechargeMapper;

    public WechatPaymentRecorder(WechatOrderMapper orderMapper,
                                 UserWalletMapper walletMapper,
                                 RechargeRecordMapper rechargeMapper) {
        this.orderMapper = orderMapper;
        this.walletMapper = walletMapper;
        this.rechargeMapper = rechargeMapper;
    }

    /**
     * 幂等入账：只有把订单从非 PAID 改为 PAID 的线程才增加余额。
     *
     * @return true 表示本次完成了入账；false 表示订单已经入账过
     */
    @Transactional
    public boolean recordPaid(WechatOrder order, WechatTransaction transaction) {
        int updated = orderMapper.markPaid(order.getOutTradeNo(), transaction.transactionId(),
                parsePaidTime(transaction.successTime()));
        if (updated == 0) {
            return false;
        }

        walletMapper.ensureExists(order.getUserId());
        walletMapper.addBalance(order.getUserId(), order.getAmountMicros());

        RechargeRecord record = new RechargeRecord();
        record.setUserId(order.getUserId());
        // 微信支付没有管理员操作人，用 0 表示系统自动入账。
        record.setOperatorId(0L);
        record.setAmountMicros(order.getAmountMicros());
        record.setRemark("微信支付，订单号 " + order.getOutTradeNo());
        rechargeMapper.insert(record);

        log.info("微信支付入账成功：userId={}, outTradeNo={}, amountFen={}, transactionId={}",
                order.getUserId(), order.getOutTradeNo(), order.getAmountFen(), transaction.transactionId());
        return true;
    }

    private LocalDateTime parsePaidTime(String successTime) {
        // 微信返回 RFC3339，例如 2026-10-20T18:30:00+08:00；解析失败时以本地到账时间为准。
        try {
            return java.time.OffsetDateTime.parse(successTime)
                    .atZoneSameInstant(java.time.ZoneId.of("Asia/Shanghai"))
                    .toLocalDateTime();
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }
}
