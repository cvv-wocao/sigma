package cn.itcast.agent.ai.payment;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Locale;

/**
 * 微信支付 API v3 的 RSA 签名、平台证书验签与回调 AES-GCM 解密。
 *
 * <p>所有密钥只在启动时读取，不写日志、不落库。</p>
 */
public final class WechatPayCrypto {

    private static final String SIGN_ALGORITHM = "SHA256withRSA";
    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;

    private final PrivateKey merchantPrivateKey;
    private final X509Certificate platformCertificate;
    private final byte[] apiV3Key;

    public WechatPayCrypto(WechatPayProperties properties) {
        this.merchantPrivateKey = loadPrivateKey(properties.privateKeyPath());
        this.platformCertificate = loadCertificate(properties.platformCertPath());
        this.apiV3Key = properties.apiV3Key().getBytes(StandardCharsets.UTF_8);
        if (apiV3Key.length != 32) {
            throw new IllegalStateException("微信支付 APIv3 Key 必须是 32 个字符");
        }
    }

    public String sign(String message) {
        try {
            Signature signer = Signature.getInstance(SIGN_ALGORITHM);
            signer.initSign(merchantPrivateKey);
            signer.update(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signer.sign());
        } catch (Exception e) {
            throw new WechatPayException("微信支付请求签名失败", e);
        }
    }

    public boolean verify(String message, String signature) {
        try {
            Signature verifier = Signature.getInstance(SIGN_ALGORITHM);
            verifier.initVerify(platformCertificate.getPublicKey());
            verifier.update(message.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(signature));
        } catch (Exception e) {
            return false;
        }
    }

    public String platformSerialNumber() {
        return platformCertificate.getSerialNumber().toString(16).toUpperCase(Locale.ROOT);
    }

    public String decryptResource(String ciphertext, String nonce, String associatedData) {
        try {
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            SecretKeySpec key = new SecretKeySpec(apiV3Key, "AES");
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH,
                    nonce.getBytes(StandardCharsets.UTF_8));
            cipher.init(Cipher.DECRYPT_MODE, key, spec);
            cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            byte[] plaintext = cipher.doFinal(Base64.getDecoder().decode(ciphertext));
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new WechatPayException("微信支付回调报文解密失败", e);
        }
    }

    private static PrivateKey loadPrivateKey(String path) {
        try {
            String pem = Files.readString(Path.of(path), StandardCharsets.UTF_8);
            if (!pem.contains("BEGIN PRIVATE KEY")) {
                throw new IllegalStateException("商户私钥必须是 PKCS#8 PEM（apiclient_key.pem）");
            }
            String base64 = pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] encoded = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(encoded));
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("读取微信支付商户私钥失败：" + path, e);
        }
    }

    private static X509Certificate loadCertificate(String path) {
        try (InputStream input = Files.newInputStream(Path.of(path))) {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            return (X509Certificate) factory.generateCertificate(input);
        } catch (Exception e) {
            throw new IllegalStateException("读取微信支付平台证书失败：" + path, e);
        }
    }
}
