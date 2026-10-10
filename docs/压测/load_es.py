#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
ES 灌数器：直调单条上架接口 + 多路并发 + 失败重试（幂等）。

为什么不直接用 batchUpperAlbum/{upperId}？
    upperAlbum 内部会往一个 core=10/队列=3/AbortPolicy 的小线程池提交 4 个
    CompletableFuture；批量接口 12 个外层线程并发提交会瞬间打满池子、任务被
    AbortPolicy 直接拒绝（实测 /100608 → ok=0, skip=100608，0.96 秒全跳过）。
    本脚本改为：N 路并发直调 GET /api/search/albumInfo/upperAlbum/{id}，
    池内稳态负载 ≈ N×1~2 个任务 < 13 个槽位，不触发拒绝；失败条目收集后重试。

用法：
    python load_es.py                # 默认 1..100608，4 并发
    python load_es.py 1000 8         # 灌 1..1000，8 并发
"""
import http.client
import json
import queue
import sys
import threading
import time

HOST, PORT = "localhost", 8502
PATH_PREFIX = "/api/search/albumInfo/upperAlbum/"
TOTAL = int(sys.argv[1]) if len(sys.argv) > 1 else 100608
THREADS = int(sys.argv[2]) if len(sys.argv) > 2 else 4

q = queue.Queue()
lock = threading.Lock()
done = [0]
failed = []


def fetch(conn, album_id):
    try:
        conn.request("GET", PATH_PREFIX + str(album_id))
        resp = conn.getresponse()
        body = resp.read()
        return resp.status == 200 and b'"code":200' in body
    except Exception:
        return False


def worker():
    conn = http.client.HTTPConnection(HOST, PORT, timeout=60)
    while True:
        item = q.get()
        if item is None:
            break
        if not fetch(conn, item):
            with lock:
                failed.append(item)
        with lock:
            done[0] += 1
            if done[0] % 5000 == 0:
                print(f"[{time.strftime('%H:%M:%S')}] 进度 {done[0]}/{TOTAL}，失败 {len(failed)}",
                      flush=True)
        q.task_done()
    conn.close()


def run_pass(ids):
    global done, failed
    done = [0]
    failed = []
    for i in ids:
        q.put(i)
    threads = [threading.Thread(target=worker, daemon=True) for _ in range(THREADS)]
    for t in threads:
        t.start()
    q.join()
    for _ in threads:
        q.put(None)
    for t in threads:
        t.join()
    return list(failed)


def main():
    start = time.time()
    print(f"开始灌数 1..{TOTAL}，并发 {THREADS}", flush=True)
    left = run_pass(range(1, TOTAL + 1))
    round_no = 1
    while left and round_no < 3:
        round_no += 1
        print(f"第 {round_no} 轮重试 {len(left)} 条失败项...", flush=True)
        left = run_pass(left)
    cost = time.time() - start
    print(f"完成：总耗时 {cost:.0f}s（{cost/60:.1f} 分钟），最终失败 {len(left)} 条", flush=True)
    if left:
        print("失败 id：", left[:50], flush=True)
        with open("load_es_failed.json", "w") as f:
            json.dump(left, f)


if __name__ == "__main__":
    main()
