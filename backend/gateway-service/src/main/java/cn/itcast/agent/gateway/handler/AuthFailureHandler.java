package cn.itcast.agent.gateway.handler;

import cn.itcast.agent.common.core.result.ErrorCode;
import cn.itcast.agent.common.core.result.Result;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

/**
 * 鉴权失败时的统一响应写出。
 *
 * <p>网关工作在 WebFlux 上，没有 Servlet 的 @RestControllerAdvice，
 * 因此这里手工把 common-core 的 Result 序列化后写入响应体，
 * 保证前端拿到的错误结构与其它服务一致（HTTP 401 + {"code":401,...}）。</p>
 */
public final class AuthFailureHandler {

    private AuthFailureHandler() {
    }

    public static Mono<Void> write(ServerWebExchange exchange, ErrorCode errorCode, JsonMapper jsonMapper, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Result<Void> body = Result.error(errorCode, message == null ? errorCode.getMessage() : message);
        byte[] bytes = jsonMapper.writeValueAsBytes(body);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }
}