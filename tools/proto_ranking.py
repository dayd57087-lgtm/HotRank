"""聚合排序算法原型 v2 —— 修掉 v1 的重複分組问题，并调准参数。

v1 的问题：聚类时限定「同平台不能进同一组」，导致同一个事件里来自同平台的
两条相近标题各自成组，于是「青岛货轮火灾25人遇难」被拆成两个组。

v2 改为：先按标题相似度把「事件」聚出来（不限平台），组内每个平台保留分数最高
的那条作为代表，加成按「涉及几个不同平台」算。同时用「只跟组代表比」的星型
聚类替代单链接聚类，避免 A~B、B~C 传递成一个大杂烩。
"""

import concurrent.futures
import json
import math
import re
import urllib.request

UA = {"User-Agent": "Mozilla/5.0 (Linux; Android 14) Chrome/120.0.0.0 Mobile Safari/537.36"}
PLATFORMS = {
    "weibo": ("微博", 1.00),
    "baidu": ("百度", 0.95),
    "douyin": ("抖音", 0.95),
    "toutiao": ("头条", 0.90),
    "zhihu": ("知乎", 0.88),
    "bilibili": ("B站", 0.85),
}
MAX_ITEMS = 50
W_HEAT, W_RANK = 0.6, 0.4
NO_HEAT_PENALTY = 0.85
CLUSTER_STEP, CLUSTER_CAP = 0.15, 1.60
SIM_THRESHOLD = 0.50
MIN_SHORT_LEN = 6

NOISE = re.compile(r"[#＃\s\W_]+")
TRIM = re.compile(r"[^\w\u4e00-\u9fff]+")


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
    n = float(m.group(1))
    return n * {"万": 1e4, "亿": 1e8}.get(m.group(2), 1)


def norm_title(t):
    return TRIM.sub("", NOISE.sub("", t)).lower()


def bigrams(s):
    return {s[i:i + 2] for i in range(len(s) - 1)} if len(s) > 1 else {s}


def similarity(a, b):
    if not a or not b:
        return 0.0
    short, long_ = (a, b) if len(a) <= len(b) else (b, a)
    # 包含关系算强相似，但短串太短容易误合并（如「真的假的」被任意长标题包含）
    if len(short) >= MIN_SHORT_LEN and short in long_:
        return 0.9
    A, B = bigrams(a), bigrams(b)
    return len(A & B) / len(A | B) if A and B else 0.0


with concurrent.futures.ThreadPoolExecutor(6) as ex:
    raw = dict(ex.map(fetch, PLATFORMS))

# ---- 平台内归一化 ----
items = []
for p, rows in raw.items():
    label, weight = PLATFORMS[p]
    if not rows:
        continue
    valid = [h for h in (to_number(r.get("hot_value")) for r in rows) if h and h > 0]
    max_h = max(valid) if valid else None
    n = len(rows)
    for i, r in enumerate(rows):
        title = (r.get("title") or "").strip()
        if not title:
            continue
        h = to_number(r.get("hot_value"))
        rank_score = 1.0 if n <= 1 else 1.0 - i / (n - 1)
        heat_score = math.log(1 + h) / math.log(1 + max_h) if (h and max_h) else None
        base = rank_score * NO_HEAT_PENALTY if heat_score is None \
            else W_HEAT * heat_score + W_RANK * rank_score
        items.append({"platform": p, "label": label, "rank": r.get("index", i + 1),
                      "title": title, "url": r.get("url", ""),
                      "base": base * weight, "nt": norm_title(title)})

# ---- 星型聚类：只跟组代表比 ----
items.sort(key=lambda x: -x["base"])
clusters = []
for it in items:
    hit = None
    for c in clusters:
        if similarity(it["nt"], c["rep"]["nt"]) >= SIM_THRESHOLD:
            hit = c
            break
    if hit:
        hit["members"].append(it)
    else:
        clusters.append({"rep": it, "members": [it]})

# ---- 加成 + 综合热度 ----
for c in clusters:
    plats = {m["platform"] for m in c["members"]}
    c["plats"] = plats
    c["boost"] = min(1 + CLUSTER_STEP * (len(plats) - 1), CLUSTER_CAP)
    c["score"] = c["rep"]["base"] * c["boost"]

clusters.sort(key=lambda c: -c["score"])
peak = clusters[0]["score"] if clusters else 1.0

print(f"共 {len(items)} 条 -> {len(clusters)} 个事件")
print(f"多站同榜：{sum(1 for c in clusters if len(c['plats']) > 1)} 个\n")
print(f"{'#':>3} {'综合热度':>6}  {'同榜':<10} 标题")
print("-" * 96)
for i, c in enumerate(clusters[:22], 1):
    score = round(c["score"] / peak * 100)
    tag = f"{len(c['plats'])}站" if len(c["plats"]) > 1 else ""
    plats = "+".join(m["label"] for m in sorted(c["members"], key=lambda m: -m["base"]))
    print(f"{i:>3} {score:>6}  {tag:<10} {c['rep']['title'][:44]}")

print("\n=== 多站同榜明细（聚合榜的核心价值） ===")
for c in clusters:
    if len(c["plats"]) > 1:
        print(f"\n★ {len(c['plats'])} 站同榜  {c['boost']:.2f}x  综合热度 {round(c['score'] / peak * 100)}")
        for m in sorted(c["members"], key=lambda m: -m["base"]):
            print(f"   [{m['label']:4}{m['rank']:>3}] base={m['base']:.3f}  {m['title'][:46]}")
