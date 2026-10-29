#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
布隆过滤器处理脚本（压测配套，无第三方依赖，纯标准库实现 Redis RESP 客户端）。

背景：album 服务的布隆过滤器在**启动时**从数据库加载专辑 id（key: album:bloom:filter）。
灌完 10 万测试数据后，若 album 实例启动早于灌数，过滤器会误杀新专辑
（现象：GET /api/album/albumInfo/getAlbumInfo/50000 返回 code 228 "该专辑不存在50000"）。

用法：
    python bloom_saturate.py            # 把位图全部置 1（"全部放行"模式，立即生效）
    python bloom_saturate.py --delete   # 删除过滤器 key（下次重启 album 时按新数据重建）

    # 验证：curl http://localhost:8501/api/album/albumInfo/getAlbumInfo/50000
    #   返回 code 200 → 已放行；返回 228 → 仍被拦截（重启 album 后重试本脚本）

注意：
- 饱和模式会暂时失去"防缓存穿透"优化（查询会直达缓存/数据库），功能正确性不受影响。
- 正式环境请用 --delete + 重启 album 的方式让过滤器按真实数据重建。
"""
import socket
import sys

REDIS_HOST = "47.119.114.135"
REDIS_PORT = 6379
FILTER_KEY = "album:bloom:filter"
# 位图长度：expectedInsertions=100000, fpp=0.01 时 Redisson 计算出约 958,506 bit ≈ 119,814 字节


def read_reply(f):
    line = f.readline().rstrip(b"\r\n")
    if not line:
        return None
    t, rest = line[:1], line[1:]
    if t == b"+":
        return rest.decode()
    if t == b"-":
        return "ERR:" + rest.decode()
    if t == b":":
        return int(rest)
    if t == b"$":
        n = int(rest)
        if n == -1:
            return None
        return f.read(n + 2)[:-2]
    if t == b"*":
        n = int(rest)
        if n == -1:
            return None
        return [read_reply(f) for _ in range(n)]
    raise RuntimeError("unknown reply type: " + repr(line))


def cmd(s, f, *args):
    out = b"*" + str(len(args)).encode() + b"\r\n"
    for a in args:
        if not isinstance(a, (bytes, bytearray)):
            a = str(a).encode()
        out += b"$" + str(len(a)).encode() + b"\r\n" + a + b"\r\n"
    s.sendall(out)
    return read_reply(f)


def main():
    delete_mode = "--delete" in sys.argv
    s = socket.create_connection((REDIS_HOST, REDIS_PORT), timeout=20)
    f = s.makefile("rb")
    print("PING:", cmd(s, f, "PING"))
    print("TYPE:", cmd(s, f, "TYPE", FILTER_KEY))

    if delete_mode:
        print("DEL:", cmd(s, f, "DEL", FILTER_KEY))
        print("已删除。请重启 album 服务（IDEA），启动日志会显示从数据库加载的专辑数。")
        s.close()
        return

    before = cmd(s, f, "STRLEN", FILTER_KEY)
    print("修改前 STRLEN:", before)
    total = 119814
    chunk = 16384
    blob = b"\xff" * chunk
    sent = 0
    while sent < total:
        n = min(chunk, total - sent)
        cmd(s, f, "SETRANGE", FILTER_KEY, sent, blob[:n])
        sent += n
    print("修改后 STRLEN:", cmd(s, f, "STRLEN", FILTER_KEY))
    for bit in (0, 500000, 958505):
        print(f"GETBIT {bit} =", cmd(s, f, "GETBIT", FILTER_KEY, bit))
    s.close()
    print("饱和完成。等待约 5 秒（album 侧本地副本自动同步）后验证：")
    print("  curl http://localhost:8501/api/album/albumInfo/getAlbumInfo/50000")


if __name__ == "__main__":
    main()
