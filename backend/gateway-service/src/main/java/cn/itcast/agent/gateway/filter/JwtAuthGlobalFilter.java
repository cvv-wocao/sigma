package cn.itcast.agent.gateway.filter;

import cn.itcast.agent.common.core.exception.BusinessException;
import cn.itcast.agent.common.core.result.ErrorCode;
import cn.itcast.agent.common.security.AuthHeaders;
import cn.itcast.agent.common.security.jwt.JwtTokenProvider;
import cn.itcast.agent.common.security.jwt.LoginUser;
import cn.itcast.agent.gateway.config.GatewayAuthProperties;
import cn.itcast.agent.gateway.handler.AuthFailureHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

/**
 * 网关统一鉴权过滤器。
 *
 * <p>处理流程：</p>
 * <ol>
 *   <li>先无条件删除客户端传入的身份头（{@code X-User-Id} 等），防止伪造；</li>
 *   <li>命中白名单（或 CORS 预检 OPTIONS）直接放行；</li>
 *   <li>否则要求 {@code Authorization: Bearer <token>}，用与 user-service 相同的逻辑校验令牌；</li>
 *   <li>校验通过后把用户身份写入请求头再转发给下游，下游无需再解析令牌；</li>
 *   <li>校验失败（缺失 / 非法 / 过期）直接返回 HTTP 401 + 统一 Result 结构，不再转发。</li>
 * </ol>
 *
 * <p>放在 HIGHEST_PRECEDENCE + 1，保证在路由转发之前执行。</p>
 */
@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthGlobalFilter.class);

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final GatewayAuthProperties authProperties;
    private final JsonMapper jsonMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public JwtAuthGlobalFilter(JwtTokenProvider jwtTokenProvider,
                               GatewayAuthProperties authProperties,
                               JsonMapper jsonMapper) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.authProperties = authProperties;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // 1. 无论是否鉴权，先摘掉客户端可能伪造的身份头
        ServerHttpRequest sanitizedRequest = request.mutate()
                .headers(headers -> {
                    headers.remove(AuthHeaders.USER_ID);
                    headers.remove(AuthHeaders.USERNAME);
                    headers.remove(AuthHeaders.NICKNAME);
                    headers.remove(AuthHeaders.USER_ROLE);
                })
                .build();

        // 2. 白名单与 CORS 预检直接放行
        if (isWhitelisted(request) || HttpMethod.OPTIONS.equals(request.getMethod())) {
            return chain.filter(exchange.mutate().request(sanitizedRequest).build());
        }

        // 3. 取令牌
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            log.warn("鉴权失败：缺少令牌，path={}", request.getPath().value());
            return AuthFailureHandler.write(exchange, ErrorCode.UNAUTHORIZED, jsonMapper, "未登录，请先登录");
        }

        // 4. 校验令牌
        LoginUser loginUser;
        try {
            loginUser = jwtTokenProvider.parseToken(authorization.substring(BEARER_PREFIX.length()).trim());
        } catch (BusinessException e) {
            log.warn("鉴权失败：{}，path={}", e.getMessage(), request.getPath().value());
            return AuthFailureHandler.write(exchange, e.getErrorCode(), jsonMapper, e.getMessage());
        }

        // 5. 写入可信身份头后转发
        ServerHttpRequest authenticatedRequest = sanitizedRequest.mutate()
                .headers(headers -> {
                    if (loginUser.id() != null) {
                        headers.set(AuthHeaders.USER_ID, String.valueOf(loginUser.id()));
                    }
                    headers.set(AuthHeaders.USERNAME, nullToEmpty(loginUser.username()));
                    headers.set(AuthHeaders.NICKNAME, nullToEmpty(loginUser.nickname()));
                    headers.set(AuthHeaders.USER_ROLE, nullToEmpty(loginUser.role()));
                })
                .build();
        return chain.filter(exchange.mutate().request(authenticatedRequest).build());
    }

    private boolean isWhitelisted(ServerHttpRequest request) {
        String path = request.getPath().value();
        return authProperties.whitelist().stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private String nullToEmpty(String value) {
        return StringUtils.hasText(value) ? value : "";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}