# 本次优化 Review 说明

> 2026-09-28 基于代码评审进行的一轮修复，每个修复项独立提交，可用 `git log --oneline` 对照本清单逐条 review。
> 所有改动处均带 `【修复#N】` 注释标记，全局搜索该关键字即可定位全部修改点。

## 提交清单

| 提交 | 修复项 | 概述 |
|---|---|---|
| 400cfc0 | 基线 | 整理 .gitignore：排除含密钥的 `资料/`（Nacos 配置备份）与个人材料 |
| 11772f0 | #2 | ItemServiceImpl 并发写 HashMap → ConcurrentHashMap |
| d1e6b9e | #3 | 统计累加改数据库原子 SQL，消除并发丢失更新 |
| ba05603 | #4 | 网关白名单收紧：fileUpload/支付轮询必须登录 |
| 4fe1b47 | #5 | 补全微信支付回调链路：notify 验签 + 金额校验 + 全链路幂等 |
| 389a181 | #6 | 订单签名对 payWay 字段失效问题 |

## 各项详情

### #1 密钥泄露（最重要，需要你线下操作）
- `资料/`（含微信支付 APIv3 密钥、小程序 appSecret、商户证书序列号、公网 IP 的 Nacos 配置备份）已确认**从未进入 git 历史**，本次加入 .gitignore 永久排除。
- **待你操作**：以上密钥视为已泄露（曾明文存放在磁盘与 Nacos），请到微信支付商户平台 / 微信公众平台**重置 APIv3 密钥和 appSecret**。

### #2 ES 聚合并发写 HashMap
- `service-search/.../ItemServiceImpl.java`：4 个 CompletableFuture 异步任务往同一个 HashMap 写数据，存在数据竞争。改用 ConcurrentHashMap。

### #3 统计累加丢失更新
- 原实现 `selectOne → 内存加 → updateById`，并发下丢失更新。
- 新增 `AlbumStatMapper.incrAlbumStatNum` / `TrackStatMapper.incrTrackStatNum`（`SET stat_num = stat_num + n`）。
- 顺带修正 `AlbumInfoServiceImpl.updateStat` 写死 `ALBUM_STAT_PLAY`、忽略传入 statType 的隐藏 bug。
- review 时注意：表 `album_stat`/`track_stat` 的 `update_time` 由 SQL 内 `NOW()` 维护。

### #4 网关白名单
- 移除 `/api/album/fileUpload`（匿名可向 MinIO 任意传文件）与 `/api/payment/wxPay/queryPayStatus/**`（会触发改库发 MQ 的写操作）。
- `/api/user/vipServiceConfig/**` 收紧为精确路径 `/findAll`（前端只调用了它）。
- **配套前端改动**：编译产物 `mp-weixin/uni_modules/cl-upload/components/cl-upload/cl-upload.js` 上传请求注入登录 token，否则收紧后封面上传会被 208 拦截（该改动已随前端目录并入本仓库）。
- `/api/payment/wxPay/notify` 保留白名单（微信服务器无 token），安全由 #5 的验签保证。

### #5 支付回调链路（改动最大，重点 review）
- **notify 端点**：`WxPayApiController#notify`（POST /api/payment/wxPay/notify），用 `NotificationParser` 完成验签+解密+防重放；成功应答 `{"code":"SUCCESS"}`，失败 HTTP 500 + `{"code":"FAIL"}` 交由微信重试。
- **下单金额**：`WxPayServiceImpl.createJsapi` 不再硬编码 1 分钱，改用支付记录真实金额（分），与回调金额校验同源。
- **金额校验**：`PaymentInfoServiceImpl.updatePaymentStatus` 比对回调金额与本地支付记录，不一致记日志并抛异常返回 FAIL。
- **幂等化三处**（全部改为条件更新，受影响行数=0 直接跳过）：
  - `updatePaymentStatus`：UNPAID→PAID 才发 MQ；
  - `OrderInfoServiceImpl.updateOrderSuccess`：0901→0902 才发放权益（VIP 重复发放会叠加续期，此处兜底）；
  - `RechargeInfoServiceImpl.rechargePaySuccess`：充值状态条件更新 + 余额改原子累加（原实现并发会丢钱）。
- `queryPayStatus` 补 `@LoginVerification`。
- **联调提醒**：notifyUrl 必须是公网 HTTPS 地址（Nacos 中已配置）；本地无法收到微信回调，轮询 queryPayStatus 路径仍可用作开发验证。

### #6 payWay 签名失效
- `submitOrder` 验签前只覆盖了 map 的 payWay，实际下单分支读取 `orderInfoVo.getPayWay()` 原值，签名对 payWay 无效。验签通过后同步覆盖 VO。

## 本次评审发现但未修复的已知问题（建议后续处理）
1. `RabbitService` 先 `convertAndSend` 再写 Redis（顺序反了，confirm 极快时回调读不到）；returns 回调从 Redis 反序列化的对象 retryCount 恒为 0，重试计数不生效。
2. `SignHelper.signKey` 硬编码 `"REDACTED"`，建议挪到 Nacos 配置。
3. 消费异常无 basicNack/死信队列，消息 unacked 挂起；`SearchReceiver` 的 `@Retryable` 无效。
4. `service-live` 空壳；测试覆盖仅 SignHelperTest；fastjson 1.2.29 有已知漏洞，建议换 fastjson2 或 jackson。
5. `AlbumInfoServiceImpl.removeAlbumInfo`/`updateAlbumInfo` 多表操作未加事务。
