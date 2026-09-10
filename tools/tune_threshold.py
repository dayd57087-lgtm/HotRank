"""阈值扫描 v2：加入「数字冲突否决」规则后重新选参数。

v1 发现的问题：0.40 阈值下 iPhone17Pro 被误合并进 iPhone18Pro 事件组。
数字（版本号/价格/数量/年份）在标题里是强区分信号，两条标题若各含数字
且完全没有交集，就不该判定为同一事件。加上这条否决规则后，可以放心用较低的
阈值换取更好的召回。
"""

import concurrent.futures
import json
import math
import re
import urllib.request

UA = {"User-Agent": "Mozilla/5.0 (Linux; Android 14) Chrome/120.0.0.0 Mobile Safari/537.36"}
PLATFORMS = {"weibo": 1.00, "baidu": 0.95, "douyin": 0.95,
             "toutiao": 0.90, "zhihu": 0.88, "bilibili": 0.85}
MAX_ITEMS = 50
W_HEAT, W_RANK, NO_HEAT_PENALTY = 0.6, 0.4, 0.85
NOISE = re.compile(r"[#＃\s\W_]+")
TRIM = re.compile(r"[^\w\u4e00-\u9fff]+")
DIGITS = re.compile(r"\d+")


def get(url):
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=12) as r:
        return json.loads(r.read().decode())


def fetch(p):
    try:
        return p, (get(f"https://uapis.cn/api/v1/misc/hotboard?type={p}").get("list") or [])[:MAX_ITEMS]
    except Exception:
        return p, []


def to_number(raw):
    if raw is None:
        return None
    s = str(raw).strip().replace(" ", "")
    if not s or s == "null":
        return None
    m = re.match(r"^([\d.]+)(万|亿)?", s)
    if not m:
        return None
    return float(m.group(1)) * {"万": 1e4, "亿": 1e8}.get(m.group(2), 1)


def norm_title(t):
    return TRIM.sub("", NOISE.sub("", t)).lower()


def bigrams(s):
    return {s[i:i + 2] for i in range(len(s) - 1)} if len(s) > 1 else {s}


def digit_conflict(a, b):
    """两边都含数字、且数字集合完全不相交 -> 强判定不是同一事件。"""
    da, db = set(DIGITS.findall(a)), set(DIGITS.findall(b))
    return bool(da and db and not (da & db))


def similarity(a, b, min_short=6, guard=True):
    if not a or not b:
        return 0.0
    if guard and digit_conflict(a, b):
        return 0.0
    short, long_ = (a, b) if len(a) <= len(b) else (b, a)
    if len(short) >= min_short and short in long_:
        return 0.9
    A, B = bigrams(a), bigrams(b)
    return len(A & B) / len(A | B) if A and B else 0.0


with concurrent.futures.ThreadPoolExecutor(6) as ex:
    raw = dict(ex.map(fetch, PLATFORMS))

items = []
for p, rows in raw.items():
    if not rows:
        continue
    valid = [h for h in (to_number(r.get("hot_value")) for r in rows) if h and h > 0]
    max_h = max(valid) if valid else None
    for i, r in enumerate(rows):
        title = (r.get("title") or "").strip()
        if not title:
            continue
        h = to_number(r.get("hot_value"))
        rank_score = 1.0 if len(rows) <= 1 else 1.0 - i / (len(rows) - 1)
        heat_score = math.log(1 + h) / math.log(1 + max_h) if (h and max_h) else None
        base = rank_score * NO_HEAT_PENALTY if heat_score is None \
            else W_HEAT * heat_score + W_RANK * rank_score
        items.append({"platform": p, "rank": r.get("index", i + 1), "title": title,
                      "base": base * PLATFORMS[p], "nt": norm_title(title)})

items.sort(key=lambda x: -x["base"])


def cluster(th):
    groups = []
    for it in items:
        hit = next((c for c in groups if similarity(it["nt"], c["rep"]["nt"], guard=True) >= th), None)
        if hit:
            hit["members"].append(it)
        else:
            groups.append({"rep": it, "members": [it]})
    for c in groups:
        c["plats"] = {m["platform"] for m in c["members"]}
    return groups


print(f"{'阈值':>5} {'事件组':>6} {'多站同榜':>8} {'最大':>4}   iPhone 误合并")
print("-" * 52)
for th in (0.30, 0.35, 0.40, 0.45, 0.50, 0.60):
    g = cluster(th)
    multi = [c for c in g if len(c["plats"]) > 1]
    bad = any("17pro" in m["nt"] and "18pro" in m["nt"] for c in g for m in c["members"])
    print(f"{th:>5} {len(g):>6} {len(multi):>8} "
          f"{max((len(c['plats']) for c in multi), default=0):>4}   {'❌ 有' if bad else '✅ 无'}")

print("\n===== 阈值 0.40 + 数字守卫 的明细 =====")
for c in cluster(0.40):
    if len(c["plats"]) > 1:
        print(f"  [{len(c['plats'])}站] {c['rep']['title'][:42]}")
        for m in c["members"][1:]:
            print(f"        └ {m['platform']:9}#{m['rank']:<3} {m['title'][:44]}")
