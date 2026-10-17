package cn.itcast.agent.gateway.config;

import cn.itcast.agent.common.security.jwt.JwtProperties;
import cn.itcast.agent.common.security.jwt.JwtTokenProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 网关鉴权相关 Bean 装配。
 *
 * <p>JwtTokenProvider 与 user-service 用的是同一个实现（common-security），
 * 因此两端对令牌的签发与校验规则天然一致，密钥通过 agent.auth.jwt.secret 统一提供。</p>
 */
@Configuration
@EnableConfigurationProperties({JwtProperties.class, GatewayAuthProperties.class})
public class GatewayAuthConfig {

    @Bean
    public JwtTokenProvider jwtTokenProvider(JwtProperties jwtProperties) {
        return new JwtTokenProvider(jwtProperties);
    }
}