package cn.itcast.agent.ai.alipay;

import cn.itcast.agent.ai.alipay.dto.AlipayTransaction;
import cn.itcast.agent.ai.entity.AlipayOrder;
import cn.itcast.agent.ai.entity.RechargeRecord;
import cn.itcast.agent.ai.mapper.AlipayOrderMapper;
import cn.itcast.agent.ai.mapper.RechargeRecordMapper;
import cn.itcast.agent.ai.mapper.UserWalletMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 支付宝支付成功入账：条件更新保证轮询与定时任务并发时只加一次余额。 */
@Service
public class AlipayPaymentRecorder {

    private static final Logger log = LoggerFactory.getLogger(AlipayPaymentRecorder.class);

    private final AlipayOrderMapper orderMapper;
    private final UserWalletMapper walletMapper;
    private final RechargeRecordMapper rechargeMapper;

    public AlipayPaymentRecorder(AlipayOrderMapper orderMapper,
                                 UserWalletMapper walletMapper,
                                 RechargeRecordMapper rechargeMapper) {
        this.orderMapper = orderMapper;
        this.walletMapper = walletMapper;
        this.rechargeMapper = rechargeMapper;
    }

    @Transactional
    public boolean recordPaid(AlipayOrder order, AlipayTransaction transaction) {
        int updated = orderMapper.markPaid(order.getOutTradeNo(), transaction.tradeNo(),
                transaction.paidTime() == null ? LocalDateTime.now() : transaction.paidTime());
        if (updated == 0) {
            return false;
        }

        walletMapper.ensureExists(order.getUserId());
        walletMapper.addBalance(order.getUserId(), order.getAmountMicros());

        RechargeRecord record = new RechargeRecord();
        record.setUserId(order.getUserId());
        record.setOperatorId(0L);
        record.setAmountMicros(order.getAmountMicros());
        record.setRemark("支付宝支付，订单号 " + order.getOutTradeNo());
        rechargeMapper.insert(record);

        log.info("支付宝入账成功：userId={}, outTradeNo={}, amountFen={}, tradeNo={}",
                order.getUserId(), order.getOutTradeNo(), order.getAmountFen(), transaction.tradeNo());
        return true;
    }
}
