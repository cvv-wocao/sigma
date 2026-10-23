package cn.itcast.agent.ai.payment;

import cn.itcast.agent.ai.payment.dto.WechatTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 微信支付 API v3 的最小实现：Native 下单、订单查询、回调验签与解密。
 *
 * <p>不引入第三方支付 SDK，使用 JDK HttpClient + RSA 签名直接对接官方接口，
 * 避免 SDK 与 Spring Boot 4 的版本兼容问题。所有请求和响应都校验平台证书签名。</p>
 */
@Component
public class WechatPayClient {

    private static final Logger log = LoggerFactory.getLogger(WechatPayClient.class);
    private static final String AUTH_SCHEMA = "WECHATPAY2-SHA256-RSA2048";
    private static final String NATIVE_PATH = "/v3/pay/transactions/native";
    private static final long MAX_CLOCK_SKEW_SECONDS = 300;
    private static final DateTimeFormatter RFC3339 = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private final WechatPayProperties properties;
    private final JsonMapper jsonMapper;
    private final WechatPayCrypto crypto;
    private final HttpClient httpClient;

    public WechatPayClient(WechatPayProperties properties, JsonMapper jsonMapper) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        if (properties.usable()) {
            this.crypto = new WechatPayCrypto(properties);
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            log.info("微信支付已启用：mchId={}, notifyUrl={}", properties.mchId(), properties.notifyUrl());
        } else {
            this.crypto = null;
            this.httpClient = null;
            if (properties.enabled()) {
                log.warn("微信支付已开启但配置不完整，支付接口将不可用");
            }
        }
    }

    public boolean enabled() {
        return crypto != null;
    }

    /** Native 下单，返回微信生成的 code_url。 */
    public String createNativeOrder(String outTradeNo, long amountFen, String attach) {
        requireEnabled();
        try {
            Map<String, Object> amount = new LinkedHashMap<>();
            amount.put("total", amountFen);
            amount.put("currency", "CNY");

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("appid", properties.appId());
            payload.put("mchid", properties.mchId());
            payload.put("description", abbreviate(properties.description(), 127));
            payload.put("out_trade_no", outTradeNo);
            payload.put("time_expire", OffsetDateTime.now(ZoneOffset.ofHours(8))
                    .plusMinutes(properties.expireMinutes()).format(RFC3339));
            payload.put("attach", attach);
            payload.put("notify_url", properties.notifyUrl());
            payload.put("amount", amount);

            String body = jsonMapper.writeValueAsString(payload);
            String responseBody = execute("POST", NATIVE_PATH, body);
            String codeUrl = text(parseMap(responseBody), "code_url");
            if (!StringUtils.hasText(codeUrl)) {
                throw new WechatPayException("微信支付下单响应缺少 code_url");
            }
            return codeUrl;
        } catch (WechatPayException e) {
            throw e;
        } catch (Exception e) {
            throw new WechatPayException("微信支付下单失败", e);
        }
    }

    /** 按商户订单号查询微信侧交易状态。 */
    public WechatTransaction queryTransaction(String outTradeNo) {
        requireEnabled();
        String path = "/v3/pay/transactions/out-trade-no/" + outTradeNo + "?mchid=" + properties.mchId();
        String body = execute("GET", path, "");
        return parseTransaction(body);
    }

    /** 校验微信支付回调的签名、平台证书序列号与时间戳。 */
    public void verifyNotification(String timestamp, String nonce, String signature,
                                   String serial, String body) {
        requireEnabled();
        if (!StringUtils.hasText(timestamp) || !StringUtils.hasText(nonce)
                || !StringUtils.hasText(signature) || !StringUtils.hasText(serial)
                || body == null) {
            throw new WechatPayException("微信支付回调缺少签名头或请求体");
        }
        verifyFreshTimestamp(timestamp, "回调");
        verifySerial(serial);
        String message = timestamp + "\n" + nonce + "\n" + body + "\n";
        if (!crypto.verify(message, signature)) {
            throw new WechatPayException("微信支付回调签名验证失败");
        }
    }

    public String decryptResource(String ciphertext, String nonce, String associatedData) {
        requireEnabled();
        if (!StringUtils.hasText(ciphertext) || !StringUtils.hasText(nonce)
                || associatedData == null) {
            throw new WechatPayException("微信支付回调 resource 字段不完整");
        }
        return crypto.decryptResource(ciphertext, nonce, associatedData);
    }

    /** 把查询响应或解密后的回调交易报文解析成统一结构。 */
    public WechatTransaction parseTransaction(String body) {
        try {
            Map<?, ?> root = parseMap(body);
            Map<?, ?> amount = asMap(root.get("amount"));
            return new WechatTransaction(
                    text(root, "appid"),
                    text(root, "mchid"),
                    text(root, "out_trade_no"),
                    text(root, "transaction_id"),
                    text(root, "trade_state"),
                    longValue(amount.get("total")),
                    text(amount, "currency"),
                    text(root, "success_time"));
        } catch (WechatPayException e) {
            throw e;
        } catch (Exception e) {
            throw new WechatPayException("微信支付交易报文解析失败", e);
        }
    }

    private String execute(String method, String path, String body) {
        requireEnabled();
        try {
            String timestamp = String.valueOf(Instant.now().getEpochSecond());
            String nonce = UUID.randomUUID().toString().replace("-", "");
            String signMessage = method + "\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + body + "\n";
            String signature = crypto.sign(signMessage);
            String authorization = AUTH_SCHEMA
                    + " mchid=\"" + properties.mchId() + "\""
                    + ",nonce_str=\"" + nonce + "\""
                    + ",signature=\"" + signature + "\""
                    + ",timestamp=\"" + timestamp + "\""
                    + ",serial_no=\"" + properties.merchantSerialNumber() + "\"";

            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(properties.baseUrl() + path))
                    .timeout(Duration.ofSeconds(12))
                    .header("Accept", "application/json")
                    .header("User-Agent", "java-agent-platform/1.0")
                    .header("Authorization", authorization);
            if ("POST".equals(method)) {
                builder.header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            } else {
                builder.GET();
            }

            HttpResponse<String> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            boolean success = response.statusCode() / 100 == 2;
            verifyResponseSignature(response, success);
            if (!success) {
                Map<?, ?> error = safeParseMap(response.body());
                String code = text(error, "code");
                String message = text(error, "message");
                throw new WechatPayException("微信支付接口失败：HTTP " + response.statusCode()
                        + ", code=" + code + ", message=" + message);
            }
            return response.body();
        } catch (WechatPayException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WechatPayException("微信支付请求被中断", e);
        } catch (Exception e) {
            throw new WechatPayException("微信支付请求失败", e);
        }
    }

    private void verifyResponseSignature(HttpResponse<String> response, boolean required) {
        String timestamp = header(response, "Wechatpay-Timestamp");
        String nonce = header(response, "Wechatpay-Nonce");
        String signature = header(response, "Wechatpay-Signature");
        String serial = header(response, "Wechatpay-Serial");
        if (!StringUtils.hasText(timestamp) || !StringUtils.hasText(nonce)
                || !StringUtils.hasText(signature) || !StringUtils.hasText(serial)) {
            if (required) {
                throw new WechatPayException("微信支付响应缺少签名头，已拒绝使用该响应");
            }
            return;
        }
        verifyFreshTimestamp(timestamp, "响应");
        verifySerial(serial);
        String message = timestamp + "\n" + nonce + "\n" + response.body() + "\n";
        if (!crypto.verify(message, signature)) {
            throw new WechatPayException("微信支付响应签名验证失败");
        }
    }

    private void verifyFreshTimestamp(String timestamp, String scene) {
        try {
            long seconds = Long.parseLong(timestamp);
            if (Math.abs(Instant.now().getEpochSecond() - seconds) > MAX_CLOCK_SKEW_SECONDS) {
                throw new WechatPayException("微信支付" + scene + "时间戳已过期");
            }
        } catch (NumberFormatException e) {
            throw new WechatPayException("微信支付" + scene + "时间戳格式不正确", e);
        }
    }

    private void verifySerial(String serial) {
        if (!crypto.platformSerialNumber().equalsIgnoreCase(serial)) {
            throw new WechatPayException("微信支付平台证书序列号不匹配，请更新平台证书");
        }
    }

    private Map<?, ?> parseMap(String body) {
        try {
            if (!StringUtils.hasText(body)) {
                throw new WechatPayException("微信支付响应为空");
            }
            return jsonMapper.readValue(body, Map.class);
        } catch (WechatPayException e) {
            throw e;
        } catch (Exception e) {
            throw new WechatPayException("微信支付响应 JSON 解析失败", e);
        }
    }

    private Map<?, ?> safeParseMap(String body) {
        try {
            return parseMap(body);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static Map<?, ?> asMap(Object value) {
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    private static String text(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : -1L;
    }

    private static String header(HttpResponse<?> response, String name) {
        return response.headers().firstValue(name).orElse("");
    }

    private static String abbreviate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private void requireEnabled() {
        if (crypto == null) {
            throw new WechatPayException(properties.unavailableMessage());
        }
    }
}
