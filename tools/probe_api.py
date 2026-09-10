import json, urllib.request, concurrent.futures

UA = {"User-Agent": "Mozilla/5.0 (Linux; Android 14) Chrome/120.0.0.0 Mobile Safari/537.36"}
PLATFORMS = ["weibo", "zhihu", "baidu", "toutiao", "douyin", "bilibili"]


def get(url):
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=10) as r:
        return json.loads(r.read().decode())


def probe(p):
    try:
        d = get(f"https://uapis.cn/api/v1/misc/hotboard?type={p}")
        lst = d.get("list") or []
        keys = sorted(lst[0].keys()) if lst else []
        sample = lst[0] if lst else {}
        return p, len(lst), keys, sample.get("index"), repr(sample.get("hot_value"))
    except Exception as e:
        return p, 0, f"ERR {e}", None, None


with concurrent.futures.ThreadPoolExecutor(6) as ex:
    rows = list(ex.map(probe, PLATFORMS))

print("=== 主源 uapis.cn ===")
for r in rows:
    print(f"{r[0]:9} n={r[1]:3} keys={r[2]} index={r[3]} hot_value={r[4]}")

print("\n=== 备用源 60s.viki.moe ===")
for p in ["weibo", "zhihu", "toutiao"]:
    try:
        d = get(f"https://60s.viki.moe/v2/{p}")
        lst = d.get("data") or []
        print(f"{p:9} n={len(lst):3} keys={sorted(lst[0].keys()) if lst else []} "
              f"hot_value={repr(lst[0].get('hot_value')) if lst else None}")
    except Exception as e:
        print(f"{p:9} ERR {e}")
