package cn.itcast.agent.common.security.jwt;

import cn.itcast.agent.common.core.exception.BusinessException;
import cn.itcast.agent.common.core.result.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

/**
 * JWT 令牌签发与解析。仅依赖 jjwt，不绑定 Web 框架，网关（WebFlux）与业务服务（Servlet）都可复用。
 */
public class JwtTokenProvider {

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_NICKNAME = "nickname";
    private static final String CLAIM_ROLE = "role";

    private static final int MIN_SECRET_BYTES = 32;

    private final JwtProperties properties;
    private final SecretKey secretKey;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        byte[] keyBytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "agent.auth.jwt.secret 长度不足：HMAC 签名要求密钥至少 " + MIN_SECRET_BYTES + " 字节，当前 " + keyBytes.length + " 字节");
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 签发令牌。
     */
    public String createToken(LoginUser loginUser) {
        Date issuedAt = new Date();
        Date expiration = new Date(issuedAt.getTime() + getExpireSeconds() * 1000L);
        return Jwts.builder()
                .subject(String.valueOf(loginUser.id()))
                .claim(CLAIM_USER_ID, loginUser.id())
                .claim(CLAIM_USERNAME, loginUser.username())
                .claim(CLAIM_NICKNAME, loginUser.nickname())
                .claim(CLAIM_ROLE, loginUser.role())
                .issuer(properties.getIssuer())
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }

    /**
     * 解析令牌，失败时抛出业务异常（TOKEN_EXPIRED / TOKEN_INVALID）。
     */
    public LoginUser parseToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Number userId = claims.get(CLAIM_USER_ID, Number.class);
            String role = claims.get(CLAIM_ROLE, String.class);
            return new LoginUser(
                    userId == null ? null : userId.longValue(),
                    claims.get(CLAIM_USERNAME, String.class),
                    claims.get(CLAIM_NICKNAME, String.class),
                    role);
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
    }

    /**
     * 令牌有效期（秒），返回给前端用于展示与刷新判断。
     */
    public long getExpireSeconds() {
        return Duration.ofMinutes(properties.getExpireMinutes()).toSeconds();
    }
}