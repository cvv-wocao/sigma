package cn.itcast.agent.user.security;

import cn.itcast.agent.common.core.result.ErrorCode;
import cn.itcast.agent.common.security.jwt.JwtProperties;
import cn.itcast.agent.common.security.jwt.JwtTokenProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import tools.jackson.databind.json.JsonMapper;

/**
 * Spring Security 配置：
 * 1. 无状态（不使用 Session），登录态完全由 JWT 承载；
 * 2. 注册与登录接口放行，其余接口必须携带有效令牌；
 * 3. 认证失败返回 401，权限不足返回 403，响应体统一为 Result 结构。
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    /** 无需登录即可访问的路径（网关去掉 /api 前缀后的路径） */
    private static final String[] PUBLIC_PATHS = {
            "/user/register",
            "/user/login",
            "/error",
            "/actuator/**"
    };

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtTokenProvider jwtTokenProvider(JwtProperties jwtProperties) {
        return new JwtTokenProvider(jwtProperties);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtTokenProvider jwtTokenProvider,
                                                   JsonMapper jsonMapper) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(registry -> registry
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, ex) -> JwtAuthenticationFilter.writeJson(
                                response, jsonMapper, ErrorCode.UNAUTHORIZED, 401))
                        .accessDeniedHandler((request, response, ex) -> JwtAuthenticationFilter.writeJson(
                                response, jsonMapper, ErrorCode.FORBIDDEN, 403)))
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider, jsonMapper),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}