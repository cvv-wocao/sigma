# 支付宝当面付密钥目录

启用支付宝当面付时，把应用私钥和支付宝公钥放在本目录，容器会只读挂载到
`/run/secrets/alipay/`。证书和私钥已被 `.gitignore` 排除，禁止提交到仓库。

需要准备：

- `app_private_key.pem`：支付宝开放平台应用的 RSA2 应用私钥（PKCS#8）。
- `alipay_public_key.pem`：支付宝开放平台提供的支付宝公钥，用于验签响应。

`.env` 至少配置：

```dotenv
ALIPAY_PAY_ENABLED=true
ALIPAY_PAY_APP_ID=支付宝开放平台应用 APPID
ALIPAY_PAY_GATEWAY_URL=https://openapi.alipay.com/gateway.do
```

沙箱联调时把网关改成：

```dotenv
ALIPAY_PAY_GATEWAY_URL=https://openapi-sandbox.dl.alipaydev.com/gateway.do
```

注意事项：

- 支付宝开放平台应用需要开通「当面付」权限，否则预下单会返回业务错误。
- 本方案不配置 `notify_url`，不需要备案域名、HTTPS 或公网回调端口。
- 必须使用支付宝公钥，不要误填应用公钥；两者不是同一把密钥。
- 正式环境建议使用 RSA2，并保持服务器时间与 NTP 同步。

数据库升级要手动执行一次：

```bash
docker exec -i jap-mysql mysql -uroot -proot --default-character-set=utf8mb4 \
  < infrastructure/mysql/init/09-alipay-pay.sql
```
