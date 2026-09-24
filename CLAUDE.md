# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

谷粒随享（蛊听月）听书平台后端，Spring Cloud 微服务项目，Maven 多模块聚合工程（`tingshu-parent` 为父 pom）。前端为微信小程序 uni-app，单独维护，不在本仓库内。

## 技术栈

- JDK 17、Spring Boot 3.0.5、Spring Cloud 2022.0.2、Spring Cloud Alibaba 2022.0.0.0-RC2
- MyBatis-Plus 3.5.3.1（MySQL 8.0）、Redis（Jedis + Redisson 3.20）
- RabbitMQ（普通消息 + 延迟队列）、Elasticsearch（co.elastic.clients 新版客户端）
- 微信支付、腾讯云 VOD、MinIO、Seata、Sentinel、XXL-JOB 2.4.0、Knife4j 4.1.0

## 常用命令

```bash
# 编译整个工程（在 tingshu-parent 根目录）
mvn clean compile

# 打包
mvn clean package -DskipTests

# 启动单个微服务 / 网关
mvn -pl service/service-album spring-boot:run
mvn -pl server-gateway spring-boot:run
```

每个模块也可直接运行其 `*Application.java`（如 `ServiceAlbumApplication`）。注意：服务启动依赖 Nacos 注册中心与各中间件在线。

## 模块结构

```
tingshu-parent
├── common
│   ├── common-util        # Result 统一响应、ResultCodeEnum、MD5、PinYinUtils、AuthContextHolder 等工具
│   ├── common-log         # @Log 操作日志注解 + LogAspect
│   ├── rabbit-util        # RabbitService（普通/延迟消息）+ GuiguCorrelationData + 消息回调初始化
│   └── service-util       # 全局异常、登录切面、RedisCache 缓存注解、MyBatis-Plus/Redis/Redisson/Knife4j 配置
├── model                  # 实体 model/、视图 vo/、查询条件 query/（按业务分包，含 ES 索引模型 search/）
├── service                # 10 个业务微服务
├── service-client         # OpenFeign 客户端（每个接口配 *DegradeFeignClient 降级）
└── server-gateway         # Spring Cloud Gateway（路由 + CORS）
```

### 微服务与端口、数据库

| 模块 | 端口 | 数据库 |
|---|---|---|
| server-gateway | 8500 | 无 |
| service-album | 8501 | tingshu_album |
| service-search | 8502 | 无（ES） |
| service-user | 8503 | tingshu_user |
| service-order | 8504 | tingshu_order |
| service-account | 8505 | tingshu_account |
| service-payment | 8506 | tingshu_payment |
| service-live | 8507 | tingshu_live |
| service-comment | 8508 | 无（MongoDB） |
| service-dispatch | 8509 | tingshu_dispatch |
| service-system | 8510 | tingshu_system |

> service-system 当前仅有 Feign 客户端（service-system-client），尚无独立服务模块。

每个微服务内部统一分层：`api/`（对前端开放、Feign 调用，路径 `api/...`）、`controller/`（后台管理，路径 `admin/...`）、`receiver/`（MQ 消费）、`service` + `service/impl`、`mapper`、`config`。多个服务含 `admin/` 后台包（如 service-album）。

## 外部基础设施（Nacos 拉取的真实配置）

- Nacos 注册/配置中心：`47.119.114.135:8848`
- MySQL：`47.119.114.135:3306`（root/root，按服务分库）
- Redis：`47.119.114.135:6379` db0
- RabbitMQ：`47.119.114.135:5672`（guest/guest）
- MongoDB：`47.119.114.135:27017`，库 `tingshu`
- Elasticsearch：`47.119.114.135:9200`（elastic/111111）
- Seata：共享配置 `seata-common.yaml`，seata-server 已注册进 Nacos
- XXL-JOB 调度中心 2.4.0；Sentinel 控制台 `localhost:8080`
- `资料/DEFAULT_GROUP/` 下为 Nacos 上各 `*-dev.yaml` 配置的本地备份（修改配置时注意同步）

## 配置管理

所有服务的实际配置（数据源、Redis、MQ、第三方密钥等）都在 **Nacos 配置中心**，不随代码提交。每个服务 `src/main/resources/bootstrap.properties` 声明 Nacos 地址、`spring.application.name`、profile=dev、配置前缀与扩展名；部分服务（order/user 等）额外引入 `shared-configs[0].data-id=seata-common.yaml`。本地改 `资料/DEFAULT_GROUP/*.yaml` 需手动同步到 Nacos 才生效。

## 关键架构模式（跨模块，需理解）

### 统一响应与异常
- 所有接口返回 `Result<T>`（common-util），错误码见 `ResultCodeEnum`。
- 业务异常抛 `GuiguException`，由 `GlobalExceptionHandler` 统一转成 `Result`；登录过期为 208（`LOGIN_AUTH`）。

### 登录校验与用户上下文
- `@LoginVerification(required=...)` 注解 + `LoginAspect` AOP 切面（切点限定 `*.api.*` 包）。从请求头 `token` 查 Redis 用户信息，`required=true` 时未登录抛 `LOGIN_AUTH`。
- 校验通过后用户 ID 写入 `AuthContextHolder`（ThreadLocal），业务代码通过 `AuthContextHolder.getUserId()` 获取当前用户；无注解的接口拿不到。

### Feign 调用
- 跨服务调用走 OpenFeign，接口在 service-client，每个接口配 `*DegradeFeignClient` 降级实现（@FeignClient fallback）。
- `FeignInterceptor` 自动把上游 `token` 头透传给下游服务。
- Feign 路径指向对端 `api/...` 接口。

### Redis 缓存
- `@RedisCache(prefix=...)` 注解 + `RedisCacheAspect` 实现「先查缓存→未命中加 Redisson 分布式锁→查库→写缓存」，空值也缓存防穿透，key 由 prefix + 参数组成。
- 专辑详情额外用布隆过滤器（`album:bloom:filter`）防穿透。
- Redis key 常量集中在 `RedisConstant`（如 `album:info:`、`user:login:`、`ranking:`、`user:trade:`）。

### 消息队列
- 发送：`RabbitService.sendMessage(exchange, routingKey, message)` 或 `sendDealyMessage(..., delayTime)`（秒）。发送时将 `GuiguCorrelationData` 连同业务数据存入 Redis（10 分钟），用于发布确认失败后的重试保障。
- 消费：`@RabbitListener` + `@QueueBinding` 声明交换机/队列/路由键，方法内**手动 ack**（`channel.basicAck(deliveryTag, false)`），用 `@SneakyThrows` 包异常。
- 队列/交换机/路由键常量集中在 `MqConst`；`RabbitInitConfigApplicationListener` 统一配置 publish-confirm/return 回调。
- 典型用法：微信支付成功发消息解耦订单/账户更新；下单后发延迟消息实现超时未支付自动取消订单。

### 缓存一致性（Canal CDC）
- `service-cdc` 用 Canal 监听 MySQL binlog，`@CanalTable("表名")` + `EntryHandler` 实现（如 `AlbumInfoCdcHandler` 监听 album_info 变化后删除对应 Redis 缓存），保证缓存与数据库一致。

### 搜索（ES）
- `service-search` 是唯一直接操作 ES 的模块，使用新版 `co.elastic.clients` 客户端 + Spring Data Repository；索引模型在 `model/search`（AlbumInfoIndex、AttributeValueIndex、SuggestIndex）。
- 专辑上/下架、检索、排行、搜索框联想补全都在此模块；拼音搜索用 `PinYinUtils`。

### 定时任务
- `service-dispatch` 是 XXL-JOB 执行器，`@XxlJob("方法名")` 定义任务（如 `updateLatelyAlbumRanking` 更新排行榜），通过 Feign 调 search 服务。

### 分布式事务与数据落库
- Seata 通过 Nacos 共享配置启用；订单/支付/账户涉及多表写操作的服务注意事务边界。
- MyBatis-Plus 通过 `MyMetaObjectHandler` 自动填充 `createTime`/`updateTime`，分页用 `Page`。

### 网关
- 路由规则在 Nacos `server-gateway-dev.yaml`：按 `Path=/*/album/**` 等前缀把请求转发到对应 `lb://` 服务；`lb:ws://` 处理直播 WebSocket。
- `AuthGlobalFilter` **已实现统一登录鉴权**（默认全拦 + 白名单）：20 条 Ant 风格白名单路径直接放行，其余路径校验请求头 `token` 在 Redis 中是否存在（登录 token 的 key 是**裸 token 字符串**，无前缀），不存在则返回 HTTP 200 + body `{"code":208}`（前端 `request.js` 依赖 `statusCode==200 && code==208` 触发重新登录）。与各服务 `@LoginVerification` 构成双层防护。
- 白名单中专辑详情路径用 `/api/search/albumInfo/{albumId:[0-9]+}` 正则限定为数字 ID，避免误放行 `batchUpperAlbum`、`updateLatelyAlbumRanking` 等单词路径。
