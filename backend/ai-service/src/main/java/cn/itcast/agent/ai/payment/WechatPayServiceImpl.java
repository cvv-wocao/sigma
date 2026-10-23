package cn.itcast.agent.ai.payment;

import cn.itcast.agent.ai.entity.WechatOrder;
import cn.itcast.agent.ai.mapper.WechatOrderMapper;
import cn.itcast.agent.ai.payment.dto.CreateWechatOrderRequest;
import cn.itcast.agent.ai.payment.dto.WechatTransaction;
import cn.itcast.agent.ai.payment.vo.WechatOrderVO;
import cn.itcast.agent.common.core.exception.BusinessException;
import cn.itcast.agent.common.core.result.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/** 微信 Native 扫码支付实现。 */
@Service
public class WechatPayServiceImpl implements WechatPayService {

    private static final Logger log = LoggerFactory.getLogger(WechatPayServiceImpl.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter ORDER_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long MICROS_PER_FEN = 10_000L;
    private static final long MAX_AMOUNT_FEN = 1_000_000L;
    private static final String STATUS_PENDING = "PENDING";

    private final WechatPayProperties properties;
    private final WechatPayClient client;
    private final WechatOrderMapper orderMapper;
    private final WechatPaymentRecorder paymentRecorder;
    private final JsonMapper jsonMapper;

    public WechatPayServiceImpl(WechatPayProperties properties,
                                WechatPayClient client,
                                WechatOrderMapper orderMapper,
                                WechatPaymentRecorder paymentRecorder,
                                JsonMapper jsonMapper) {
        this.properties = properties;
        this.client = client;
        this.orderMapper = orderMapper;
        this.paymentRecorder = paymentRecorder;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public WechatOrderVO createOrder(Long userId, CreateWechatOrderRequest request) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请登录后充值");
        }
        if (!client.enabled()) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, properties.unavailableMessage());
        }

        long amountFen = amountFen(request.amountYuan());
        LocalDateTime now = LocalDateTime.now();
        WechatOrder order = new WechatOrder();
        order.setUserId(userId);
        order.setAmountFen(Math.toIntExact(amountFen));
        order.setAmountMicros(amountFen * MICROS_PER_FEN);
        order.setStatus("PENDING");
        order.setExpireTime(now.plusMinutes(properties.expireMinutes()));
        order.setOutTradeNo(newOutTradeNo());

        try {
            orderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            // 时间戳 + 10 位随机数的碰撞概率极低；真遇到时重新生成一次即可。
            order.setId(null);
            order.setOutTradeNo(newOutTradeNo());
            orderMapper.insert(order);
        }

        try {
            String codeUrl = client.createNativeOrder(order.getOutTradeNo(), amountFen, String.valueOf(userId));
            order.setCodeUrl(codeUrl);
            if (orderMapper.updateCodeUrl(order.getOutTradeNo(), codeUrl) == 0) {
                throw new WechatPayException("微信支付订单状态已变化，无法保存二维码");
            }
            return toVO(order);
        } catch (Exception e) {
            orderMapper.markFailed(order.getOutTradeNo());
            log.error("微信支付下单失败：userId={}, outTradeNo={}, error={}",
                    userId, order.getOutTradeNo(), e.getMessage(), e);
            throw new BusinessException(ErrorCode.SERVER_ERROR, "微信支付下单失败，请稍后重试");
        }
    }

    @Override
    public WechatOrderVO queryOrder(Long userId, String outTradeNo) {
        WechatOrder order = requireOwnedOrder(userId, outTradeNo);
        if (!STATUS_PENDING.equals(order.getStatus())) {
            return toVO(order);
        }

        try {
            WechatTransaction transaction = client.queryTransaction(outTradeNo);
            validateIdentity(transaction);
            if ("SUCCESS".equals(transaction.tradeState())) {
                validatePayment(order, transaction);
                paymentRecorder.recordPaid(order, transaction);
            } else if ("CLOSED".equals(transaction.tradeState()) || "REVOKED".equals(transaction.tradeState())) {
                orderMapper.markClosed(outTradeNo);
            } else if ("PAYERROR".equals(transaction.tradeState())) {
                orderMapper.markFailed(outTradeNo);
            }
        } catch (WechatPayException e) {
            // 轮询遇到微信侧网络抖动时保留 PENDING，让前端下一轮继续查；回调仍可正常入账。
            log.warn("查询微信支付订单失败：outTradeNo={}, error={}", outTradeNo, e.getMessage());
        }

        WechatOrder latest = orderMapper.selectByOutTradeNo(outTradeNo);
        if (latest != null && STATUS_PENDING.equals(latest.getStatus())
                && latest.getExpireTime() != null
                && LocalDateTime.now().isAfter(latest.getExpireTime().plusMinutes(2))) {
            orderMapper.markClosed(outTradeNo);
            latest = orderMapper.selectByOutTradeNo(outTradeNo);
        }
        return toVO(latest == null ? order : latest);
    }

    @Override
    public void handleNotification(String body, String timestamp, String nonce,
                                   String signature, String serial) {
        if (!client.enabled()) {
            throw new WechatPayException(properties.unavailableMessage());
        }
        client.verifyNotification(timestamp, nonce, signature, serial, body);

        try {
            Map<?, ?> root = jsonMapper.readValue(body, Map.class);
            if (!"TRANSACTION.SUCCESS".equals(text(root, "event_type"))) {
                log.info("忽略非支付成功事件：eventType={}", text(root, "event_type"));
                return;
            }
            Map<?, ?> resource = asMap(root.get("resource"));
            if (!"AEAD_AES_256_GCM".equals(text(resource, "algorithm"))) {
                throw new WechatPayException("微信支付回调使用了不支持的解密算法");
            }
            String plaintext = client.decryptResource(
                    text(resource, "ciphertext"),
                    text(resource, "nonce"),
                    text(resource, "associated_data"));
            WechatTransaction transaction = client.parseTransaction(plaintext);
            validateIdentity(transaction);

            WechatOrder order = orderMapper.selectByOutTradeNo(transaction.outTradeNo());
            if (order == null) {
                throw new WechatPayException("微信支付回调对应的本地订单不存在");
            }
            validatePayment(order, transaction);
            boolean recorded = paymentRecorder.recordPaid(order, transaction);
            if (!recorded) {
                log.info("微信支付回调重复投递，订单已入账：outTradeNo={}", transaction.outTradeNo());
            }
        } catch (WechatPayException e) {
            throw e;
        } catch (Exception e) {
            throw new WechatPayException("微信支付回调处理失败", e);
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

    private WechatOrder requireOwnedOrder(Long userId, String outTradeNo) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请登录后查看订单");
        }
        if (!StringUtils.hasText(outTradeNo)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "缺少商户订单号");
        }
        WechatOrder order = orderMapper.selectByOutTradeNo(outTradeNo.trim());
        if (order == null || !userId.equals(order.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "充值订单不存在");
        }
        return order;
    }

    private void validateIdentity(WechatTransaction transaction) {
        if (!properties.appId().equals(transaction.appId())
                || !properties.mchId().equals(transaction.mchId())) {
            throw new WechatPayException("微信支付交易的应用或商户号不匹配");
        }
    }

    private void validatePayment(WechatOrder order, WechatTransaction transaction) {
        if (!"SUCCESS".equals(transaction.tradeState())) {
            throw new WechatPayException("微信支付交易尚未成功");
        }
        if (!order.getOutTradeNo().equals(transaction.outTradeNo())) {
            throw new WechatPayException("微信支付商户订单号与本地订单不一致");
        }
        if (transaction.amountFen() != order.getAmountFen()) {
            throw new WechatPayException("微信支付金额与本地订单不一致");
        }
        if (!"CNY".equals(transaction.currency())) {
            throw new WechatPayException("微信支付币种不是 CNY");
        }
        if (!StringUtils.hasText(transaction.transactionId())) {
            throw new WechatPayException("微信支付交易缺少 transaction_id");
        }
    }

    private WechatOrderVO toVO(WechatOrder order) {
        return new WechatOrderVO(
                order.getOutTradeNo(),
                BigDecimal.valueOf(order.getAmountFen(), 2),
                order.getStatus(),
                order.getCodeUrl(),
                format(order.getExpireTime()),
                format(order.getPaidTime()));
    }

    private String newOutTradeNo() {
        StringBuilder random = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            random.append(RANDOM.nextInt(10));
        }
        return "JAP" + ORDER_TIME_FORMATTER.format(LocalDateTime.now()) + random;
    }

    private String format(LocalDateTime time) {
        return time == null ? null : TIME_FORMATTER.format(time);
    }

    private static Map<?, ?> asMap(Object value) {
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    private static String text(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value);
    }
}
