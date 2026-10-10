# 压测任务 · 进度与下次继续步骤

> ## ✅ 任务已完成（2026-10-10）
>
> **最终实测数据**（10 万级语料：MySQL 100,608 行 = ES 100,608 文档；JMeter 10 并发 × 1000 采样，错误率 0%）：
>
> | 指标 | LIKE 基线（MySQL 全表扫描） | ES 检索（倒排索引） | 提升 |
> |---|---|---|---|
> | 平均 | 559ms | 18ms | 30.8x |
> | 中位数 | 545ms | 16ms | 34.1x |
> | P90 | 768ms | 24ms | 32.0x |
>
> 原始采样归档：`求职资料/压测结果/result-like.csv`、`result-es.csv`。
> 已回填三版简历（`D:\java_study\简历\AI定制简历\`）。
>
> **执行路径备忘**：直连 MySQL 被连接数打满 + app 灌数管线过慢（5h）→ 改用
> `docs/压测/bulk_load_from_api.py`（以 ES 现存 1500 条真实文档为模板重建 10 万语料，
> 90 秒灌完，0 错误）。JMeter jmx 关键修复：CSVDataSet 属性名 `variableNames`（复数）、
> LIKE 路径中文关键词必须预编码（`keywords_encoded.txt`）、CSVDataSet 放入 ThreadGroup。
> JMeter 安装位置：`D:\Serverpache-jmeter-5.6pache-jmeter-5.6in`。
>
> 以下为任务完成前的原始步骤记录（保留供参考）。

> 记录于 2026-10-06 深夜。目标：用真实压测数字替换简历中"10 万级曲库检索"的性能声明。
> 配套文档：`docs/压测指引.md`（完整方法论）、`docs/压测/`（SQL/关键词/JMX 计划/本脚本）。

## 一、当前进度（已完成的部分）

| 步骤 | 状态 | 证据 |
|---|---|---|
| MySQL 凑数据到 10 万级 | ✅ **100,608 条，id 1~100608 完全连续**（含 19 个空洞已补齐） | `SELECT COUNT(*),MAX(id) FROM tingshu_album.album_info` |
| ES 环境可达 | ✅ `192.168.13.100:9200`（elastic/111111，在虚拟机容器里） | `GET /albuminfo/_count` = 3410（**待灌数**） |
| LIKE 基线接口 | ✅ 已实现并在 8501 验证：`GET /api/album/albumInfo/likeBenchmark/三国` 返回真实数据 | 返回"米小圈三国演义"等 5 条 |
| 布隆过滤器阻塞 | ✅ 已处理（位图饱和，`bloom_saturate.py` 可复用） | `getAlbumInfo/50000` 返回 200 |
| album / user 服务 | ✅ 运行在 IDEA（8501 / 8503） | 端口验证 |
| **service-search** | ❌ **在跑的是旧代码**（无并行容错灌数），新版已提交 `da86a88` | `/batchUpperAlbum/3` 曾返回 201 |

## 二、下次继续的步骤

### 第 0 步：环境上线检查（5 分钟）

```bash
# 1) 阿里云中间件（应有 5 个 OPEN）
for p in 3306 6379 5672 27017 8848; do
  timeout 3 bash -c "echo > /dev/tcp/47.119.114.135/$p" 2>/dev/null && echo "$p OPEN" || echo "$p CLOSED"
done
# 2) 虚拟机的 ES
powershell -Command "Test-NetConnection -ComputerName 192.168.13.100 -Port 9200 -InformationLevel Quiet"
```

### 第 1 步：IDEA 启动三个服务（关键注意事项）

启动 **service-album、service-user、service-search**（前两个不变，**search 必须重启以加载最新代码**）：

- ⚠️ 如果 IDEA 弹出"文件已在磁盘上更改，是否重新加载？"→ 选**是**（SearchApiController.java 被我改过）
- ⚠️ **不要在命令行/mvn 启动这些服务**：中文 Windows + JDK17 的 JVM 默认 `file.encoding=GBK`，
  解析 Nacos 里的中文配置会抛 `MalformedInputException`（服务起不来、报 Redisson `host is empty`）。
  IDEA 默认带 `-Dfile.encoding=UTF-8` 所以正常；若必须命令行启动，加：
  `-Dspring-boot.run.jvmArguments=-Dfile.encoding=UTF-8`
  （另：项目路径含中文，命令行的 jacoco exec 也写不出来，需 `-Djacoco.destFile` 指向纯 ASCII 路径）

### 第 2 步：检查布隆过滤器（1 分钟）

```bash
curl -s "http://localhost:8501/api/album/albumInfo/getAlbumInfo/50000"
# 返回 code 200 → 跳过本步
# 返回 code 228 "该专辑不存在" → 执行（前提：Redis key 未被清除过）：
python docs/压测/bloom_saturate.py
# 等待约 5 秒后重新验证；仍失败则可能是 album 重启过，重跑本脚本
```
> 原理：album 启动时把专辑 id 装进 Redis 位图（key `album:bloom:filter`）。它启动早于灌数据时会误杀新专辑。
> 饱和=全部放行（暂时失去防穿透，功能不受影响）。彻底修复：`python bloom_saturate.py --delete` 后重启 album（10 万条重建较慢）。

### 第 3 步：灌 ES 数据（新版代码 10~30 分钟）

```bash
# 先冒烟（应返回 {"code":200,...,"data":"加载完成：ok=3, skip=0"}）
curl -s "http://localhost:8502/api/search/albumInfo/batchUpperAlbum/3"

# 正式灌数（后台跑，返回 ok/skip 统计）
curl -s -m 7200 "http://localhost:8502/api/search/albumInfo/batchUpperAlbum/100608"

# 另开窗口监控进度（目标 ≈ 100,608）
curl -s -u elastic:111111 "http://192.168.13.100:9200/albuminfo/_count"
```
> 若 search 仍是旧代码：无并行无容错（串行约 2 小时；id 空洞已补齐所以不会中断——实在不想重启时可以凑合，但慢 6 倍）。

### 第 4 步：JMeter 压测（30 分钟）

1. 确认 JMeter 已装（纯英文路径，如 `D:\jmeter`），打开 `docs/压测/like-baseline.jmx`
2. 冒烟：线程数改 1、循环改 1 → 启动 → 聚合报告错误率应为 0.00%
3. 正式：线程数 50、循环 20 → 启动 → 记录 **Average / 中位数 / 90% Line(P90) / 异常%**
4. 同法跑 `docs/压测/es-search.jmx`
5. 非 GUI 更准：`jmeter -n -t like-baseline.jmx -l like-res.csv`

### 第 5 步：数字回填简历（15 分钟）

三版简历中把 `约 500ms 降至约 50ms` 替换为实测值，格式：
> 10 万级曲库，JMeter 50 并发 × 1000 采样，P90 由约 XXX ms 降至约 XX ms，错误率 0%

文件位置：`D:\java_study\简历\AI定制简历\resume_大厂.md / resume_国企.md / resume_银行.md`
（同步更新：`docs/面试追问预演.md` 第四节"500ms→50ms 怎么测的"话术用实测数字）。

### 第 6 步（可选）：环境收尾

- 恢复布隆过滤器：`python docs/压测/bloom_saturate.py --delete` 后在 IDEA 重启 album（重建较慢）——
  或保持饱和状态（开发环境可接受）。
- 未来不需要 10 万测试数据时：删除 `album_info` 中 id > 1608 的行 + 清空 ES `albuminfo` 索引，
  可让 album 启动恢复秒级（否则每次启动要加载 10 万 id）。

## 三、本次踩过的坑（避免重复调查）

| 现象 | 根因 | 处理 |
|---|---|---|
| 命令行启动服务报 `MalformedInputException` + Redisson `host is empty` | JDK17 + 中文 Windows 默认 GBK，UTF-8 中文配置解析失败 | 加 `-Dfile.encoding=UTF-8`（IDEA 天然有） |
| 临时实例指定 8599 端口无效 | Nacos 配置的 `server.port: 8502` 优先级高于命令行参数/环境变量 | 已放弃临时实例方案，直接用 IDEA 实例 |
| `batchUpperAlbum` 跑到一半 500 | id 空洞触发 `Assert.notNull` 中断 | 已补齐空洞 + 新版代码单条失败跳过 |
| 新专辑查不到（code 228） | 布隆过滤器加载早于灌数据 | `bloom_saturate.py` |
| jacoco 报告不生成 | 项目路径含中文，agent 写不出 exec | `-Djacoco.destFile` 指 ASCII 路径（本机跑覆盖率时用） |

## 四、本次新提交（供参考）

```
da86a88 [压测配套] batchUpperAlbum 容错+并行
78f7417 [压测配套] LIKE 基线接口 + 凑数据 SQL + JMeter 计划 + 指引
0600548 [修复] JMeter 计划：Arguments 属性缺失 + ${keyword} 被 shell 吞掉
```
