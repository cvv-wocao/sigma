package cn.itcast.agent.ai.alipay;

import cn.itcast.agent.ai.alipay.dto.AlipayTransaction;
import cn.itcast.agent.ai.alipay.dto.CreateAlipayOrderRequest;
import cn.itcast.agent.ai.alipay.vo.AlipayOrderVO;
import cn.itcast.agent.ai.entity.AlipayOrder;
import cn.itcast.agent.ai.mapper.AlipayOrderMapper;
import cn.itcast.agent.common.core.exception.BusinessException;
import cn.itcast.agent.common.core.result.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** 支付宝当面付实现：前台轮询与后台定时任务共同查单，不依赖异步回调。 */
@Service
public class AlipayPayServiceImpl implements AlipayPayService {

    private static final Logger log = LoggerFactory.getLogger(AlipayPayServiceImpl.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter ORDER_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long MICROS_PER_FEN = 10_000L;
    private static final long MAX_AMOUNT_FEN = 1_000_000L;
    private static final String STATUS_PENDING = "PENDING";
    private static final int RECONCILE_BATCH_SIZE = 20;

    private final AlipayPayProperties properties;
    private final AlipayPayClient client;
    private final AlipayOrderMapper orderMapper;
    private final AlipayPaymentRecorder paymentRecorder;

    public AlipayPayServiceImpl(AlipayPayProperties properties,
                                AlipayPayClient client,
                                AlipayOrderMapper orderMapper,
                                AlipayPaymentRecorder paymentRecorder) {
        this.properties = properties;
        this.client = client;
        this.orderMapper = orderMapper;
        this.paymentRecorder = paymentRecorder;
    }

    @Override
    public AlipayOrderVO createOrder(Long userId, CreateAlipayOrderRequest request) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请登录后充值");
        }
        if (!client.enabled()) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, properties.unavailableMessage());
        }

        long amountFen = amountFen(request.amountYuan());
        LocalDateTime now = LocalDateTime.now();
        AlipayOrder order = new AlipayOrder();
        order.setUserId(userId);
        order.setAmountFen(Math.toIntExact(amountFen));
        order.setAmountMicros(amountFen * MICROS_PER_FEN);
        order.setStatus(STATUS_PENDING);
        order.setExpireTime(now.plusMinutes(properties.expireMinutes()));
        order.setOutTradeNo(newOutTradeNo());

        try {
            orderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            order.setId(null);
            order.setOutTradeNo(newOutTradeNo());
            orderMapper.insert(order);
        }

        try {
            String qrCode = client.createQrCode(order.getOutTradeNo(),
                    BigDecimal.valueOf(amountFen, 2), properties.subject());
            order.setQrCode(qrCode);
            if (orderMapper.updateQrCode(order.getOutTradeNo(), qrCode) == 0) {
                AlipayOrder latest = orderMapper.selectByOutTradeNo(order.getOutTradeNo());
                if (latest != null && "PAID".equals(latest.getStatus())) {
                    return toVO(latest);
                }
                throw new AlipayPayException("支付宝订单状态已变化，无法保存二维码");
            }
            return toVO(order);
        } catch (Exception e) {
            orderMapper.markFailed(order.getOutTradeNo());
            log.error("支付宝预下单失败：userId={}, outTradeNo={}, error={}",
                    userId, order.getOutTradeNo(), e.getMessage(), e);
            throw new BusinessException(ErrorCode.SERVER_ERROR, "支付宝下单失败，请稍后重试");
        }
    }

    @Override
    public AlipayOrderVO queryOrder(Long userId, String outTradeNo) {
        AlipayOrder order = requireOwnedOrder(userId, outTradeNo);
        if (STATUS_PENDING.equals(order.getStatus())) {
            refreshOrder(order);
        }
        AlipayOrder latest = orderMapper.selectByOutTradeNo(outTradeNo);
        return toVO(latest == null ? order : latest);
    }

    /** 每分钟补偿查询最近一天未完成的订单，避免用户支付后关闭页面导致漏记。 */
    @Override
    @Scheduled(fixedDelayString = "${ai.alipay-pay.reconcile-interval-ms:60000}",
            initialDelayString = "${ai.alipay-pay.reconcile-initial-delay-ms:30000}")
    public void reconcilePendingOrders() {
        if (!client.enabled()) {
            return;
        }
        List<AlipayOrder> orders = orderMapper.selectPendingForReconcile(RECONCILE_BATCH_SIZE);
        for (AlipayOrder order : orders) {
            try {
                refreshOrder(order);
            } catch (Exception e) {
                log.warn("支付宝补偿查单失败：outTradeNo={}, error={}", order.getOutTradeNo(), e.getMessage());
            }
        }
    }

    private void refreshOrder(AlipayOrder order) {
        try {
            AlipayTransaction transaction = client.queryTransaction(order.getOutTradeNo());
            if (!order.getOutTradeNo().equals(transaction.outTradeNo())) {
                throw new AlipayPayException("支付宝返回的商户订单号与本地订单不一致");
            }
            if ("TRADE_SUCCESS".equals(transaction.tradeStatus())
                    || "TRADE_FINISHED".equals(transaction.tradeStatus())) {
                validatePayment(order, transaction);
                paymentRecorder.recordPaid(order, transaction);
            } else if ("TRADE_CLOSED".equals(transaction.tradeStatus())) {
                orderMapper.markClosed(order.getOutTradeNo());
            }
            orderMapper.touchChecked(order.getOutTradeNo());
            closeExpiredIfNecessary(order);
        } catch (AlipayPayException e) {
            // 查单失败可能是支付宝侧短暂抖动，保留 PENDING，下一轮或下次访问继续查。
            log.warn("查询支付宝订单失败：outTradeNo={}, error={}", order.getOutTradeNo(), e.getMessage());
            closeExpiredIfNecessary(order);
        }
    }

    private void closeExpiredIfNecessary(AlipayOrder order) {
        AlipayOrder latest = orderMapper.selectByOutTradeNo(order.getOutTradeNo());
        if (latest != null && STATUS_PENDING.equals(latest.getStatus())
                && latest.getExpireTime() != null
                && LocalDateTime.now().isAfter(latest.getExpireTime().plusMinutes(2))) {
            orderMapper.markClosed(order.getOutTradeNo());
        }
    }

    private long amountFen(BigDecimal amountYuan) {
        if (amountYuan == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请填写充值金额");
        }
        BigDecimal normalized = amountYuan.stripTrailingZeros();
        if (normalized.scale() > 2) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "充值金额最多两位小数");
        }
        long amountFen;
        try {
            amountFen = normalized.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        } catch (ArithmeticException e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "充值金额格式不正确");
        }
        if (amountFen <= 0 || amountFen > MAX_AMOUNT_FEN) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "单次充值金额需在 0.01 到 10000 元之间");
        }
        return amountFen;
    }

    private AlipayOrder requireOwnedOrder(Long userId, String outTradeNo) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请登录后查看订单");
        }
        if (!StringUtils.hasText(outTradeNo)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "缺少商户订单号");
        }
        AlipayOrder order = orderMapper.selectByOutTradeNo(outTradeNo.trim());
        if (order == null || !userId.equals(order.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "充值订单不存在");
        }
        return order;
    }

    private void validatePayment(AlipayOrder order, AlipayTransaction transaction) {
        if (transaction.totalAmount() == null) {
            throw new AlipayPayException("支付宝交易缺少 total_amount");
        }
        long paidFen;
        try {
            paidFen = transaction.totalAmount().movePointRight(2)
                    .setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        } catch (ArithmeticException e) {
            throw new AlipayPayException("支付宝交易金额格式不正确");
        }
        if (paidFen != order.getAmountFen()) {
            throw new AlipayPayException("支付宝支付金额与本地订单不一致");
        }
        if (!StringUtils.hasText(transaction.tradeNo())) {
            throw new AlipayPayException("支付宝交易缺少 trade_no");
        }
    }

    private AlipayOrderVO toVO(AlipayOrder order) {
        return new AlipayOrderVO(
                order.getOutTradeNo(),
                BigDecimal.valueOf(order.getAmountFen(), 2),
                order.getStatus(),
                order.getQrCode(),
                format(order.getExpireTime()),
                format(order.getPaidTime()));
    }

    private String newOutTradeNo() {
        StringBuilder random = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            random.append(RANDOM.nextInt(10));
        }
        return "ALI" + ORDER_TIME_FORMATTER.format(LocalDateTime.now()) + random;
    }

    private String format(LocalDateTime time) {
        return time == null ? null : TIME_FORMATTER.format(time);
    }
}
