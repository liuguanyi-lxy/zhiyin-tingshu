package com.atguigu.tingshu.gateway.filter;

import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.result.ResultCodeEnum;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * <p>
 * 全局Filter，统一登录鉴权。
 * 拦截规则：白名单路径直接放行；其余路径必须携带有效的 token（Redis 中存在），否则返回 208。
 * 服务层 {@code @LoginVerification} + LoginAspect 仍保留，作为第二道兜底，形成双层防护。
 * 注意：登录 token 的 Redis key 是裸 token 字符串（见 WxLoginApiController），不要拼任何前缀。
 * </p>
 */
@Slf4j
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    /**
     * 无需登录即可访问的白名单路径（Ant 风格），均为前端小程序匿名访问所需或特殊路径
     * （登录、微信支付回调、支付轮询、WebSocket 握手、公开查询/搜索/分类/详情）。
     * 放行路径不区分 HTTP 方法。
     */
    private static final List<String> WHITE_LIST = Arrays.asList(
            // 登录接口本身
            "/api/user/wxLogin/wxLogin/**",
            // 微信支付回调（微信服务器无 token）
            "/api/payment/wxPay/notify",
            // 支付状态轮询
            "/api/payment/wxPay/queryPayStatus/**",
            // WebSocket 握手（浏览器无法携带自定义 header）
            "/api/websocket/**",
            // 搜索
            "/api/search/albumInfo",
            "/api/search/albumInfo/channel/**",
            "/api/search/albumInfo/completeSuggest/**",
            "/api/search/albumInfo/findRankingList/**",
            // 专辑详情，正则限定数字 id，避免误放行 batchUpperAlbum/updateLatelyAlbumRanking 等单词路径
            "/api/search/albumInfo/{albumId:[0-9]+}",
            // 分类
            "/api/album/category/getCategoryView/**",
            "/api/album/category/findTopBaseCategory3/**",
            "/api/album/category/getBaseCategoryList/**",
            "/api/album/category/findAllCategory1",
            // 专辑信息/属性/统计
            "/api/album/albumInfo/getAlbumInfo/**",
            "/api/album/albumInfo/getAlbumInfoStat/**",
            "/api/album/albumInfo/findAlbumAttributeValue/**",
            // 文件上传
            "/api/album/fileUpload",
            // 声音分页列表（可登录可不登录）
            "/api/album/trackInfo/findAlbumTrackPage/**",
            // 判断当前用户是否购买过声音（可登录可不登录）
            "/api/user/userInfo/userIsPaidTrack/**",
            // VIP 配置查询
            "/api/user/vipServiceConfig/**",
            // 充值单查询
            "/api/account/rechargeInfo/getRechargeInfo/**"
    );

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Autowired
    private ReactiveStringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 网关过滤器的核心入口。
     *
     * <p>这里故意只做“是否允许请求继续转发”的判断，不解析用户对象，也不把用户信息写入线程上下文。
     * 用户信息的解析由下游 Servlet 服务中的 {@code LoginAspect} 完成。</p>
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        //1. 白名单路径直接放行
        if (isWhiteList(path)) {
            return chain.filter(exchange);
        }
        //2. 获取请求头中的 token
        String token = exchange.getRequest().getHeaders().getFirst("token");
        //3. token 为空直接返回 208，不再查询 Redis
        if (token == null || token.isEmpty()) {
            log.warn("[网关鉴权] 未携带token，拦截路径: {}", path);
            return writeAuthError(exchange);
        }
        //4. 查询 Redis 判断 token 是否存在（存在=已登录放行；不存在=登录过期返回 208）
        return redisTemplate.hasKey(token)
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        return chain.filter(exchange);
                    }
                    log.warn("[网关鉴权] token不存在/已过期，拦截路径: {}, token: {}", path, token);
                    return writeAuthError(exchange);
                });
    }

    /**
     * 判断请求路径是否命中白名单
     */
    private boolean isWhiteList(String path) {
        return WHITE_LIST.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    /**
     * 返回登录过期响应：HTTP 200 + {"code":208,"message":"未登陆","data":null}
     * 前端小程序 request.js 依赖 statusCode==200 且 code==208 触发重新登录
     */
    private Mono<Void> writeAuthError(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().setContentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8));
        // 登录失败统一响应，code=208 表示登录过期
        Result<Object> result = Result.build(null, ResultCodeEnum.LOGIN_AUTH);
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(result);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            log.error("[网关鉴权] 序列化208响应失败", e);
            return response.setComplete();
        }
    }

    /**
     * 指定全局过滤器的执行顺序。数值越小，优先级越高；鉴权应尽量早于业务路由执行。
     */
    @Override
    public int getOrder() {
        return 0;
    }
}
