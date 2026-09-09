# 谷粒随享（蛊听月）—— 分布式听书平台

> 前后端分离的听书类微服务项目。后端基于 Spring Cloud Alibaba 微服务架构，前端为 uni-app 微信小程序。

## 项目简介

谷粒随享是一个支持**专辑订阅、单集收听、会员 VIP、充值购买**的听书平台：

- 用户通过微信授权登录，可浏览分类、搜索专辑、收听声音、查看排行榜
- 支持 **微信支付充值** 与 **余额购买**（VIP 会员 / 整张专辑 / 单集声音）
- 下单、扣款、权益发放跨多个微服务协作，具备订单超时自动取消等机制
- 后台侧支持创作者上传/维护专辑与声音

### 功能概览

| 端 | 能力 |
|---|---|
| C 端用户 | 微信登录、专辑/声音浏览与收听、搜索、分类、排行榜、VIP 开通、余额购买、订单、收听进度 |
| 创作者 | 专辑 / 声音的创建、上传、编辑、上/下架 |
| 平台 | 支付回调对账、订单取消、专辑统计（播放量）、内容索引（ES）、数据一致性保障 |

## 技术栈

| 分类 | 选型 |
|---|---|
| 基础 | JDK 17 · Spring Boot 3.0.5 · Spring Cloud 2022.0.2 · Spring Cloud Alibaba 2022.0.0.0-RC2 |
| 注册 / 配置 | Nacos |
| 持久层 | MySQL 8.0 · MyBatis-Plus 3.5.3.1 |
| 缓存 | Redis（Jedis）· Redisson 3.20（分布式锁 / 布隆过滤器） |
| 消息队列 | RabbitMQ（普通消息 + 延迟队列） |
| 搜索引擎 | Elasticsearch（新版 co.elastic.clients 客户端） |
| 网关 | Spring Cloud Gateway（响应式 WebFlux） |
| 远程调用 | OpenFeign（含降级实现） |
| 对象存储 / 点播 | MinIO · 腾讯云 VOD |
| 支付 | 微信支付 V3 · 微信小程序登录 |
| 中间件 | Seata（分布式事务）· Sentinel（流控）· XXL-JOB（定时任务）· Canal（MySQL binlog 同步） |
| API 文档 | Knife4j 4.1.0 |
| 前端 | uni-app · Pinia · Grace6（微信小程序） |

## 系统架构

```
                     ┌────────────────────┐
   微信小程序  ──────▶│  server-gateway      │   Spring Cloud Gateway（8500）
   (uni-app)         │  统一鉴权 · 路由转发   │
                     └─────────┬──────────┘
                               │ lb:// 负载均衡
       ┌───────────┬───────────┼───────────┬───────────┐
       ▼           ▼           ▼           ▼           ▼
 service-album service-user service-order service-payment service-account
 (8501)        (8503)        (8504)        (8506)        (8505)
   专辑/声音    用户/VIP/     订单/下单    微信支付     账户/余额/充值
   分类/上传    听书进度
       ▼           ▼           ▼
 service-search service-comment service-live
 (8502) ES     (8508) MongoDB  (8507) 直播(骨架)
       │            │
       ▼            ▼
 service-dispatch(8509) service-cdc(独立 Boot2.3.6)  service-system(8510, 仅Feign)
 XXL-JOB 执行器      Canal→Redis 缓存一致性
```

**微服务与端口 / 存储对照**

| 模块 | 端口 | 存储 |
|---|---|---|
| server-gateway | 8500 | 无 |
| service-album | 8501 | tingshu_album |
| service-search | 8502 | Elasticsearch |
| service-user | 8503 | tingshu_user |
| service-order | 8504 | tingshu_order |
| service-account | 8505 | tingshu_account |
| service-payment | 8506 | tingshu_payment |
| service-live | 8507 | tingshu_live |
| service-comment | 8508 | MongoDB |
| service-dispatch | 8509 | tingshu_dispatch |
| service-system | 8510 | tingshu_system |

## 目录结构

```
tingshu-parent
├── common                  # 通用模块
│   ├── common-util         # Result 统一响应、ResultCodeEnum、AuthContextHolder、traceId 工具等
│   ├── common-log          # @Log 操作日志注解 + AOP
│   ├── rabbit-util         # RabbitService（普通/延迟消息 + Redis 重试保障）
│   └── service-util        # 服务公共依赖：登录切面、RedisCache、全局异常、Redis/Redisson/Knife4j 配置
├── model                   # 实体 model、vo、query（按业务分包，含 ES 索引模型）
├── service                 # 业务微服务（每个模块按 api / controller / service / mapper / receiver 分层）
├── service-client          # OpenFeign 客户端（每个接口配 *DegradeFeignClient 降级）
└── server-gateway          # 网关（路由、统一登录鉴权、traceId 透传）
```

## 核心设计与亮点

### 1. 网关统一登录鉴权 + 服务层兜底
- `AuthGlobalFilter`（网关）：**默认全拦 + 白名单**——非白名单路径必须携带有效 token 且存在于 Redis，否则统一返回 `code=208`；登录、支付回调、WebSocket 握手、公开查询等 20 条白名单直接放行
- 服务层 `@LoginVerification` + `LoginAspect` 保留，形成**双层防护**；Feign 服务间调用不走网关，不受拦截影响

### 2. 全链路 traceId + ELK 日志
- 网关生成 / 透传 `X-Request-Id`，各服务 `TraceFilter` 写入 MDC，`FeignInterceptor` 透传下游，**一次请求贯穿网关→服务→Feign 同一 traceId**
- MQ 消费、定时任务各自生成独立 traceId
- 9 个服务 logback 接入 Logstash → Elasticsearch → Kibana，可按 `traceId` 检索整条调用链日志

### 3. Redis 缓存 + 多级防穿透
- `@RedisCache` 注解 + AOP：先查缓存 → Redisson 分布式锁 → 查库 → 回写，空值也缓存防穿透
- 专辑详情额外用**布隆过滤器**过滤不存在 ID 的请求
- **Canal CDC** 监听 MySQL binlog，变更实时删除对应缓存，保证缓存与 DB 最终一致

### 4. 余额购买的资金安全
- 扣减余额使用 **数据库原子 SQL + 条件更新**（`available_amount >= amount`），天然避免并发下"先查后改"的丢失更新与超扣
- 下单签名 + Redis Lua 防重复提交
- 订单支付成功通过 **RabbitMQ 延迟队列**实现超时未支付自动取消

### 5. 分布式协作
- **Seata** 保障跨服务写操作的事务边界
- **RabbitMQ 手动 ack + Redis 重试保障** 的可靠消息投递，Feign 下游消费保证幂等
- **XXL-JOB** 定时任务（专辑排行榜更新、VIP 到期状态刷新）

## 快速开始

### 环境依赖

运行时需要以下基础设施（均通过 Nacos 配置中心管理，见下）：

| 中间件 | 用途 |
|---|---|
| Nacos 2.x | 服务注册与配置中心 |
| MySQL 8.0 | 各业务库 |
| Redis | 登录态 / 缓存 / 分布式锁 |
| RabbitMQ | 消息队列 |
| Elasticsearch | 搜索 / 日志 |
| MinIO / 腾讯云 VOD | 对象存储 / 视频点播 |
| XXL-JOB | 调度中心（定时任务） |

> 各服务实际配置（数据源、Redis、第三方密钥等）在 **Nacos 配置中心**，不随代码提交；
> `src/main/resources/bootstrap.properties` 只声明 Nacos 地址与应用名。

### 构建与运行

```bash
# 编译整个工程（在 tingshu-parent 根目录）
mvn clean compile

# 打包
mvn clean package -DskipTests

# 启动单个微服务 / 网关
mvn -pl service/service-album spring-boot:run
mvn -pl server-gateway spring-boot:run
```

每个模块也可直接运行其 `*Application.java`（如 `ServiceAlbumApplication`）。
**注意：** 服务启动依赖 Nacos 与各中间件在线。

### API 文档

服务启动后访问 Knife4j：`http://localhost:{port}/doc.html`

## 其它

- 前端工程为 uni-app 微信小程序，独立维护；微信开发者工具打开 `前端代码/mp-weixin/mp-weixin` 目录运行
- 项目说明与架构约定见根目录 `CLAUDE.md`
