#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
ES 语料灌数器 v2：以 ES 现存真实文档为模板，重建 10 万级语料并批量写回。

为什么这么设计（三条路都被堵死后选定的最稳路径）：
- 直连 MySQL：被 "Too many connections" 拒绝（服务连接池打满）
- app 的 upperAlbum 管线：受 WAN 延迟 + 小线程池限制，10 万条要 5+ 小时
- user 服务：DB 连接池同样被拖死，接口超时

语料重建规则（与灌数 SQL 的复制规则等价，标题多重集一致）：
  1..1500    ← 直接复用 ES 里 app 写入的真实文档（全字段保真，含 announcerName）
  1501..1608 ← 缺失时经 album 服务 API 补取（19 个占位行按规则补造）
  1609..100608 ← 1..1500 每条 × 66 份副本，标题加后缀 "·听N"，其余字段继承源文档

用法：
    python bulk_load_from_api.py           # 全量
    python bulk_load_from_api.py 200       # 冒烟：只写前 200 条
"""
import base64
import http.client
import json
import sys
from concurrent.futures import ThreadPoolExecutor, as_completed

ALBUM_HOST, ALBUM_PORT = "localhost", 8501
ES_HOST, ES_PORT = "192.168.13.100", 9200
ES_AUTH = "Basic " + base64.b64encode(b"elastic:111111").decode()
INDEX = "albuminfo"

ORIGINAL_MAX = 1608          # 1..1608 原始数据（含 19 个占位行）
COPY_SOURCE_MAX = 1500       # id<=1500 参与复制
COPY_FACTOR = 66
TOTAL = ORIGINAL_MAX + COPY_SOURCE_MAX * COPY_FACTOR  # 100608
LIMIT = int(sys.argv[1]) if len(sys.argv) > 1 else TOTAL


def es_request(method, path, body=None, ctype="application/json"):
    conn = http.client.HTTPConnection(ES_HOST, ES_PORT, timeout=120)
    headers = {"Authorization": ES_AUTH}
    data = None
    if body is not None:
        data = body.encode("utf-8") if isinstance(body, str) else body
        headers["Content-Type"] = ctype
    conn.request(method, path, body=data, headers=headers)
    resp = conn.getresponse()
    out = resp.read()
    conn.close()
    return json.loads(out) if out else {}


def fetch_all_existing_docs():
    """取 ES 现存全部文档的 _source"""
    out = es_request("POST", "/%s/_search" % INDEX,
                     json.dumps({"size": 3000, "_source": True}))
    return [h["_source"] for h in out.get("hits", {}).get("hits", [])]


def fetch_album(album_id):
    conn = http.client.HTTPConnection(ALBUM_HOST, ALBUM_PORT, timeout=30)
    try:
        conn.request("GET", "/api/album/albumInfo/getAlbumInfo/%d" % album_id)
        resp = conn.getresponse()
        body = json.loads(resp.read())
        if body.get("code") == 200 and body.get("data"):
            return album_id, body["data"]
    except Exception:
        pass
    finally:
        conn.close()
    return album_id, None


def fetch_category_tree():
    """category3Id -> (category1Id, category2Id)，字段为 categoryChild/categoryId"""
    conn = http.client.HTTPConnection(ALBUM_HOST, ALBUM_PORT, timeout=30)
    mapping = {}
    try:
        conn.request("GET", "/api/album/category/getBaseCategoryList")
        d = json.loads(conn.getresponse().read())
        for c1 in (d.get("data") or []):
            for c2 in (c1.get("categoryChild") or []):
                for c3 in (c2.get("categoryChild") or []):
                    mapping[c3["categoryId"]] = (c1["categoryId"], c2["categoryId"])
    except Exception as e:
        print("分类树获取失败:", e)
    finally:
        conn.close()
    return mapping


def to_iso(t):
    if not t or len(str(t)) < 19:
        return None
    return str(t).replace(" ", "T") + ".000Z"


def doc_from_album_api(raw, cat_map):
    """album API 实体 → ES 文档"""
    c3 = raw.get("category3Id") or 0
    c1, c2 = cat_map.get(c3, (0, 0))
    return {
        "_class": "com.atguigu.tingshu.model.search.AlbumInfoIndex",
        "id": raw.get("id"),
        "albumTitle": raw.get("albumTitle"),
        "albumIntro": raw.get("albumIntro"),
        "announcerName": None,
        "coverUrl": raw.get("coverUrl"),
        "includeTrackCount": raw.get("includeTrackCount") or 0,
        "payType": raw.get("payType"),
        "createTime": to_iso(raw.get("createTime")),
        "category1Id": c1, "category2Id": c2, "category3Id": c3,
        "playStatNum": 0, "subscribeStatNum": 0, "buyStatNum": 0, "commentStatNum": 0,
        "hotScore": 0.0,
    }


def bulk_index(docs, batch=1000):
    errors = 0
    for i in range(0, len(docs), batch):
        chunk = docs[i:i + batch]
        lines = []
        for d in chunk:
            lines.append(json.dumps({"index": {"_index": INDEX, "_id": str(d["id"])}},
                                    ensure_ascii=False))
            lines.append(json.dumps(d, ensure_ascii=False, default=str))
        payload = ("\n".join(lines) + "\n").encode("utf-8")
        out = es_request("POST", "/_bulk?refresh=false", payload,
                         "application/x-ndjson")
        if out.get("errors"):
            errors += sum(1 for it in out["items"] if it.get("index", {}).get("error"))
        print("  已写入 %d/%d（错误 %d）" % (min(i + batch, len(docs)), len(docs), errors),
              flush=True)
    return errors


def main():
    print("1) 读取 ES 现存真实文档...")
    existing = {d["id"]: d for d in fetch_all_existing_docs() if d.get("id")}
    print("   现存 %d 条" % len(existing))

    print("2) 缺失的原始 id 经 album API 补取...")
    missing = [i for i in range(1, ORIGINAL_MAX + 1) if i not in existing]
    if missing:
        cat_map = fetch_category_tree()
        with ThreadPoolExecutor(max_workers=10) as pool:
            for f in as_completed([pool.submit(fetch_album, i) for i in missing]):
                aid, raw = f.result()
                if raw:
                    existing[aid] = doc_from_album_api(raw, cat_map)
        print("   补取后共 %d 条（缺失 %d 条已处理）" % (len(existing), len(missing)))
    # 仍缺失的（占位行）按灌数规则补造
    for i in range(1501, ORIGINAL_MAX + 1):
        if i not in existing:
            existing[i] = {
                "_class": "com.atguigu.tingshu.model.search.AlbumInfoIndex",
                "id": i, "albumTitle": "占位专辑%d" % i, "albumIntro": "",
                "announcerName": None, "coverUrl": "", "includeTrackCount": 0,
                "payType": "0101", "createTime": None,
                "category1Id": 1, "category2Id": 100, "category3Id": 1002,
                "playStatNum": 0, "subscribeStatNum": 0, "buyStatNum": 0,
                "commentStatNum": 0, "hotScore": 0.0,
            }
    # 1..1500 若仍缺（ES 缺 + API 补取失败）则终止提示
    for i in range(1, COPY_SOURCE_MAX + 1):
        if i not in existing:
            print("错误：复制源 %d 缺失，终止" % i)
            sys.exit(1)

    print("3) 组装文档（原始 + 66 倍副本）...")
    docs = [existing[i] for i in range(1, ORIGINAL_MAX + 1)]
    new_id = ORIGINAL_MAX + 1
    for src in range(1, COPY_SOURCE_MAX + 1):
        base = existing[src]
        for n in range(1, COPY_FACTOR + 1):
            copy = dict(base)
            copy["id"] = new_id
            copy["albumTitle"] = "%s·听%d" % (base.get("albumTitle"), n)
            docs.append(copy)
            new_id += 1
    docs = docs[:LIMIT]
    print("   共 %d 条文档" % len(docs))

    print("4) 批量写入 ES...")
    errors = bulk_index(docs)
    print("5) 强制 refresh 并核对计数...")
    es_request("POST", "/%s/_refresh" % INDEX)
    print(es_request("GET", "/%s/_count" % INDEX))
    print("完成：写入 %d 条，失败 %d 条" % (len(docs), errors))


if __name__ == "__main__":
    main()
