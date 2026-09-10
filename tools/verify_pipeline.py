"""镜像验证：用真实接口跑一遍 Kotlin 里的解析 + 归一 + 交错合并逻辑，
确认最终列表长什么样（Kotlin 端逻辑与此逐行对应）。
"""

import concurrent.futures
import json
import urllib.request

UA = {"User-Agent": "Mozilla/5.0 (Linux; Android 14) Chrome/120.0.0.0 Mobile Safari/537.36"}
LABELS = {
    "weibo": "微博", "zhihu": "知乎", "baidu": "百度",
    "toutiao": "头条", "douyin": "抖音", "bilibili": "B站",
}
PRIMARY = "https://uapis.cn/api/v1/misc/hotboard?type={}"
FALLBACK = {
    "weibo": "https://60s.viki.moe/v2/weibo",
    "zhihu": "https://60s.viki.moe/v2/zhihu",
    "toutiao": "https://60s.viki.moe/v2/toutiao",
}
MAX_ITEMS = 50
MERGED_DEPTH = 20
TTL_CACHE = {}


def get(url):
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=10) as r:
        return r.read().decode()


def format_hot(raw):
    if raw is None:
        return None
    value = str(raw).strip()
    if value in ("", "null"):
        return None
    try:
        n = float(value)
    except ValueError:
        return value.replace(" ", "")
    if n >= 1e8:
        return f"{n / 1e8:.1f}亿"
    if n >= 1e4:
        return f"{n / 1e4:.1f}万"
    return str(int(n))


def parse_primary(body, p):
    out = []
    for i, o in enumerate((json.loads(body).get("list") or [])[:MAX_ITEMS]):
        title = (o.get("title") or "").strip()
        if not title:
            continue
        out.append({"p": p, "rank": o.get("index", i + 1), "title": title,
                    "url": o.get("url", ""), "hot": format_hot(o.get("hot_value"))})
    return out


def parse_fallback(body, p):
    out = []
    for i, o in enumerate((json.loads(body).get("data") or [])[:MAX_ITEMS]):
        title = (o.get("title") or "").strip()
        if not title:
            continue
        out.append({"p": p, "rank": i + 1, "title": title,
                    "url": o.get("link", ""), "hot": format_hot(o.get("hot_value"))})
    return out


def fetch(p):
    urls = [(PRIMARY.format(p), parse_primary)]
    if p in FALLBACK:
        urls.append((FALLBACK[p], parse_fallback))
    for url, parser in urls:
        try:
            items = parser(get(url), p)
        except Exception:
            continue
        if items:
            return p, items
    return p, []


with concurrent.futures.ThreadPoolExecutor(6) as ex:
    results = list(ex.map(fetch, LABELS))

by_platform = {p: items for p, items in results if items}
failed = [p for p, items in results if not items]

depth = max(len(v) for v in by_platform.values())
merged = []
for i in range(min(depth, MERGED_DEPTH)):
    for p, items in by_platform.items():
        if i < len(items):
            merged.append(items[i])

print(f"在线平台: {[LABELS[p] for p in by_platform]}  失败: {[LABELS[p] for p in failed]}")
for p, items in by_platform.items():
    print(f"  {LABELS[p]:4} {len(items):2} 条   top1={items[0]['title'][:24]}  热度={items[0]['hot']}")
print(f"\n混合流共 {len(merged)} 条，前 12 条：\n")
for i, it in enumerate(merged[:12], 1):
    hot = it["hot"] if it["hot"] else "-"
    print(f"{i:2}. [{LABELS[it['p']]}] {it['title'][:34]:36} {hot}")

json.dump(merged, open("/tmp/merged_sample.json", "w"), ensure_ascii=False, indent=1)
