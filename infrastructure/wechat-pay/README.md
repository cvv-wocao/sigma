# 微信支付证书目录

启用微信 Native 扫码充值时，把商户与平台证书放在本目录，容器会只读挂载到
`/run/secrets/wechat-pay/`。证书和私钥已由 `.gitignore` 排除，禁止提交到仓库。

需要准备：

- `apiclient_key.pem`：微信支付商户 API 证书私钥（PKCS#8）。
- `platform_cert.pem`：微信支付平台证书，用于验证回调与接口响应签名。

`.env` 至少配置：

```dotenv
WECHAT_PAY_ENABLED=true
WECHAT_PAY_APP_ID=微信开放平台/公众号 APPID
WECHAT_PAY_MCH_ID=微信支付商户号
WECHAT_PAY_API_V3_KEY=32位APIv3密钥
WECHAT_PAY_MERCHANT_SERIAL_NUMBER=商户API证书序列号
WECHAT_PAY_NOTIFY_URL=https://你的域名/api/ai/wallet/wechat/notify
```

上线前确认：

- 域名已备案并配置 HTTPS，`notify_url` 能被微信服务器从公网访问。
- 腾讯云安全组放行 443；Nginx 已把 `/api/` 反向代理到网关。
- 商户平台已开通 Native 支付并关联当前 APPID。
- 服务器时间与 NTP 同步，否则 API v3 签名时间戳校验会失败。
- 平台证书轮换后替换文件并重启 `ai-service`，否则回调会因序列号不匹配被拒绝。

数据库升级要手动执行一次：

```bash
docker exec -i jap-mysql mysql -uroot -proot --default-character-set=utf8mb4 \
  < infrastructure/mysql/init/08-wechat-pay.sql
```
