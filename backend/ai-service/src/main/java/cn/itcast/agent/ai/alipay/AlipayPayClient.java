package cn.itcast.agent.ai.alipay;

import cn.itcast.agent.ai.alipay.dto.AlipayTransaction;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayTradePrecreateModel;
import com.alipay.api.domain.AlipayTradeQueryModel;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * 支付宝当面付客户端：只做预下单与查单，不接收异步回调。
 *
 * <p>SDK 负责 RSA2 请求签名和支付宝响应的公钥验签；本类负责把订单参数转换为其模型，
 * 并把业务失败翻译成统一异常。</p>
 */
@Component
public class AlipayPayClient {

    private static final Logger log = LoggerFactory.getLogger(AlipayPayClient.class);
    private static final String TRADE_NOT_EXIST = "ACQ.TRADE_NOT_EXIST";

    private final AlipayPayProperties properties;
    private final AlipayClient alipayClient;

    public AlipayPayClient(AlipayPayProperties properties) {
        this.properties = properties;
        if (properties.usable()) {
            String appPrivateKey = readKey(properties.privateKeyPath(), "支付宝应用私钥");
            String alipayPublicKey = readKey(properties.alipayPublicKeyPath(), "支付宝公钥");
            this.alipayClient = new DefaultAlipayClient(
                    properties.gatewayUrl(),
                    properties.appId(),
                    appPrivateKey,
                    "json",
                    "UTF-8",
                    alipayPublicKey,
                    "RSA2");
            log.info("支付宝当面付已启用：appId={}, gateway={}", properties.appId(), properties.gatewayUrl());
        } else {
            this.alipayClient = null;
            if (properties.enabled()) {
                log.warn("支付宝支付已开启但配置不完整，支付接口将不可用");
            }
        }
    }

    public boolean enabled() {
        return alipayClient != null;
    }

    /** 调用 alipay.trade.precreate，返回支付宝二维码内容。 */
    public String createQrCode(String outTradeNo, BigDecimal amountYuan, String subject) {
        requireEnabled();
        try {
            AlipayTradePrecreateModel model = new AlipayTradePrecreateModel();
            model.setOutTradeNo(outTradeNo);
            model.setTotalAmount(amountYuan.setScale(2, RoundingMode.UNNECESSARY).toPlainString());
            model.setSubject(abbreviate(subject, 256));
            model.setProductCode("FACE_TO_FACE_PAYMENT");
            model.setTimeoutExpress(properties.expireMinutes() + "m");

            AlipayTradePrecreateRequest request = new AlipayTradePrecreateRequest();
            request.setBizModel(model);
            // 刻意不设置 notifyUrl：支付结果由前端轮询和后台定时查单确认。

            AlipayTradePrecreateResponse response = alipayClient.execute(request);
            if (!response.isSuccess()) {
                throw new AlipayPayException("支付宝预下单失败：code=" + response.getCode()
                        + ", subCode=" + response.getSubCode() + ", subMsg=" + response.getSubMsg());
            }
            if (!StringUtils.hasText(response.getQrCode())) {
                throw new AlipayPayException("支付宝预下单响应缺少 qr_code");
            }
            return response.getQrCode();
        } catch (AlipayPayException e) {
            throw e;
        } catch (Exception e) {
            throw new AlipayPayException("支付宝预下单请求失败", e);
        }
    }

    /** 主动查询交易状态；订单在支付宝侧尚不存在时返回 NOT_EXIST。 */
    public AlipayTransaction queryTransaction(String outTradeNo) {
        requireEnabled();
        try {
            AlipayTradeQueryModel model = new AlipayTradeQueryModel();
            model.setOutTradeNo(outTradeNo);
            AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
            request.setBizModel(model);
            AlipayTradeQueryResponse response = alipayClient.execute(request);

            if (!response.isSuccess()) {
                if (TRADE_NOT_EXIST.equals(response.getSubCode())) {
                    return new AlipayTransaction(outTradeNo, null, "NOT_EXIST", null, null);
                }
                throw new AlipayPayException("支付宝查单失败：code=" + response.getCode()
                        + ", subCode=" + response.getSubCode() + ", subMsg=" + response.getSubMsg());
            }

            BigDecimal totalAmount = StringUtils.hasText(response.getTotalAmount())
                    ? new BigDecimal(response.getTotalAmount()) : null;
            return new AlipayTransaction(
                    StringUtils.hasText(response.getOutTradeNo()) ? response.getOutTradeNo() : outTradeNo,
                    response.getTradeNo(),
                    response.getTradeStatus(),
                    totalAmount,
                    toLocalDateTime(response.getSendPayDate()));
        } catch (AlipayPayException e) {
            throw e;
        } catch (Exception e) {
            throw new AlipayPayException("支付宝查单请求失败", e);
        }
    }

    private static String readKey(String path, String name) {
        try {
            String content = Files.readString(Path.of(path), StandardCharsets.UTF_8);
            String normalized = content
                    .replaceAll("-----BEGIN [^-]+-----", "")
                    .replaceAll("-----END [^-]+-----", "")
                    .replaceAll("\\s", "");
            if (!StringUtils.hasText(normalized)) {
                throw new IllegalStateException(name + "文件为空：" + path);
            }
            return normalized;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("读取" + name + "失败：" + path, e);
        }
    }

    private static LocalDateTime toLocalDateTime(Date date) {
        return date == null ? null : date.toInstant()
                .atZone(ZoneId.of("Asia/Shanghai")).toLocalDateTime();
    }

    private static String abbreviate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private void requireEnabled() {
        if (alipayClient == null) {
            throw new AlipayPayException(properties.unavailableMessage());
        }
    }
}
