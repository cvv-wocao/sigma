package cn.itcast.agent.user.security;

import cn.itcast.agent.common.core.exception.BusinessException;
import cn.itcast.agent.common.core.result.ErrorCode;
import cn.itcast.agent.common.core.result.Result;
import cn.itcast.agent.common.security.jwt.JwtTokenProvider;
import cn.itcast.agent.common.security.jwt.LoginUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 令牌认证过滤器：
 * 1. 请求头带 Authorization: Bearer <token> 时解析令牌，成功则写入安全上下文；
 * 2. 令牌非法或过期直接返回 401，不再放行；
 * 3. 未携带令牌的请求交给 Spring Security 的认证入口处理（同样返回 401）。
 *
 * 注：Spring Boot 4 默认使用 Jackson 3，自动配置提供的 Bean 类型是
 * tools.jackson.databind.json.JsonMapper，不再是 Jackson 2 的 ObjectMapper。
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final JsonMapper jsonMapper;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, JsonMapper jsonMapper) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.jsonMapper = jsonMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        try {
            LoginUser loginUser = jwtTokenProvider.parseToken(token);
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    loginUser, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (BusinessException e) {
            SecurityContextHolder.clearContext();
            writeJson(response, jsonMapper, e.getErrorCode(), HttpServletResponse.SC_UNAUTHORIZED);
        }
    }

    /**
     * 以统一 Result 结构输出错误响应。
     */
    public static void writeJson(HttpServletResponse response,
                                 JsonMapper jsonMapper,
                                 ErrorCode errorCode,
                                 int httpStatus) throws IOException {
        response.setStatus(httpStatus);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(jsonMapper.writeValueAsString(Result.error(errorCode)));
    }
}