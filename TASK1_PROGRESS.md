# 任务一开发进度：核心链路测试

更新时间：2026-09-27

## 当前目标

为支付、Redis 缓存一致性、RabbitMQ 消费、Canal CDC 核心链路补充单元测试和集成测试。当前按以下优先级推进：

1. 支付：订单金额签名验证
2. Redis 缓存：分布式锁竞争
3. RabbitMQ：消费幂等
4. Canal CDC：缓存与数据库一致性

## 已完成

### 订单签名测试

新增文件：service/service-order/src/test/java/com/atguigu/tingshu/order/helper/SignHelperTest.java

已覆盖：
- 未篡改订单数据签名通过
- 修改订单金额后签名校验失败
- 过期签名校验失败

已执行并通过：mvn -pl service/service-order -am test "-DskipTests=false"

结果：Tests run: 3；Failures: 0；Errors: 0；BUILD SUCCESS

## 当前未完成但已确认的问题

### 支付金额硬编码

文件：service/service-payment/src/main/java/com/atguigu/tingshu/payment/service/impl/WxPayServiceImpl.java

当前微信支付请求使用 amount.setTotal(1)，这会导致所有订单按 1 分钱发起支付。数据库支付记录中的 PaymentInfo.amount 才是应使用的订单金额，单位为元，微信支付 Amount.total 单位为分。

### 支付回调金额未校验

文件：service/service-payment/src/main/java/com/atguigu/tingshu/payment/service/impl/PaymentInfoServiceImpl.java

updatePaymentStatus(Transaction transaction) 当前查询支付记录后直接更新为已支付，没有校验微信回调金额与数据库金额是否一致。需要先校验金额，再更新状态和发送 MQ。

### 支付回调幂等已有基础但并发仍需关注

当前已有“已支付直接返回”的判断，可以防止顺序重复回调；但并发回调可能同时通过判断，后续应使用条件更新、唯一约束或事务方案强化，并补测试。

## 下一步任务清单

### A. 支付链路（下一次优先处理）

- [ ] 新增金额校验/元转分的可测试小组件（建议使用 BigDecimal，拒绝 null、0、负数、超过两位小数和整数范围溢出）
- [ ] 编写支付金额测试：9.99 元转换为 999 分
- [ ] 修复 WxPayServiceImpl，使用 PaymentInfo.amount，移除 1 分钱硬编码
- [ ] 编写微信回调金额一致性测试
- [ ] 修改 PaymentInfoServiceImpl：回调金额不一致时拒绝更新支付状态且不发送 MQ
- [ ] 补充支付回调成功和重复回调测试，验证 MQ 只发送一次
- [ ] 评估并发回调下的条件更新/事务边界
- [ ] 运行 mvn -pl service/service-payment -am test "-DskipTests=false"

### B. Redis 缓存与分布式锁

文件：common/service-util/src/main/java/com/atguigu/tingshu/common/RedisCache/Aspect/RedisCacheAspect.java

- [ ] 缓存命中时不执行原方法
- [ ] 缓存未命中时只回源一次
- [ ] 双重检查命中时不重复查库
- [ ] null 结果写入短 TTL，验证缓存穿透防护
- [ ] 正常结果 TTL 带随机抖动，验证雪崩防护
- [ ] 回源异常后释放锁
- [ ] 锁竞争重试及达到最大重试后的降级行为
- [ ] 不同缓存 key 使用不同锁
- [ ] 优先 Mockito 单元测试；后续视需要增加 Redis/Testcontainers 集成测试

### C. RabbitMQ 消费幂等

重点消费者：service/service-account/.../AccountReceiver.java、service/service-order/.../OrderReceiver.java、service/service-search/.../SearchReceiver.java、service/service-album/.../TrackReceiver.java

- [ ] 业务成功后 ACK
- [ ] 业务异常时不 ACK
- [ ] 空消息处理
- [ ] 重复消息只产生一次业务结果
- [ ] 重点检查充值成功消费：状态判断与余额增加之间存在并发重复入账风险
- [ ] 评估数据库条件更新、订单号唯一性和事务边界

### D. Canal CDC 缓存一致性

重点文件：service/service-cdc/src/main/java/com/atguigu/tingshu/handler/AlbumInfoCdcHandler.java、service/service-cdc/src/main/java/com/atguigu/tingshu/handler/UserCdcHandler.java

- [ ] album_info update/delete 删除正确的 album:info:[id] 缓存
- [ ] user_info update/delete 删除正确的 userInfoVo:[id] 缓存
- [ ] 先写 handler 单元测试，再视基础设施情况增加 Canal/MySQL/Redis 集成测试

## 当前变更与约束

- 尚未执行 commit 或 push。
- 不要自动 commit/push；提交前先展示变更摘要。
- 不要执行任务二的命名清理。
- 不修改配置中心、密钥、token、证书或 CI 配置。
- 当前签名工具 SignHelper 的硬编码密钥和现有算法先不改，避免破坏已有前后端协议；后续可单独设计兼容性重构。
- Maven 测试时使用 -DskipTests=false，因为部分模块 POM 默认配置了 skipTests=true。

## 下次开始方式

先阅读本文件，然后检查 Git 工作区，确认 SignHelperTest.java 未被覆盖；接着从“支付链路 A”第一项开始实现，并在每个纵向切片完成后运行对应模块测试。
