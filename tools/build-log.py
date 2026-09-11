#!/usr/bin/env python3
"""抓取 GitHub Actions 最近一次构建的日志并提取报错。

用法：
    python3 tools/build-log.py --token <PAT> --owner X --repo HotRank
"""

import argparse
import io
import json
import re
import urllib.error
import urllib.request
import zipfile

API = "https://api.github.com"


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise urllib.error.HTTPError(req.full_url, code, msg, headers, fp)


def api(method, path, token, data=None):
    body = json.dumps(data).encode() if data is not None else None
    req = urllib.request.Request(API + path, data=body, method=method)
    req.add_header("Authorization", "Bearer " + token)
    req.add_header("Accept", "application/vnd.github+json")
    req.add_header("X-GitHub-Api-Version", "2022-11-28")
    req.add_header("User-Agent", "minis-log")
    if body:
        req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            raw = r.read().decode("utf-8", "replace")
            return r.status, (json.loads(raw) if raw.strip() else {})
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "replace")
        try:
            return e.code, json.loads(raw)
        except json.JSONDecodeError:
            return e.code, {"message": raw[:300]}


def download_logs(token, owner, repo, run_id):
    """日志接口会 302 跳到对象存储，且跳转后不能带 Authorization，否则 401。"""
    url = f"{API}/repos/{owner}/{repo}/actions/runs/{run_id}/logs"
    req = urllib.request.Request(url)
    req.add_header("Authorization", "Bearer " + token)
    req.add_header("User-Agent", "minis-log")

    location = None
    try:
        opener = urllib.request.build_opener(NoRedirect)
        opener.open(req, timeout=60)
    except urllib.error.HTTPError as e:
        location = e.headers.get("Location")

    if not location:
        return None

    plain = urllib.request.Request(location)
    plain.add_header("User-Agent", "minis-log")
    with urllib.request.urlopen(plain, timeout=180) as r:
        return r.read()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--token", required=True)
    ap.add_argument("--owner", required=True)
    ap.add_argument("--repo", default="HotRank")
    ap.add_argument("--lines", type=int, default=45)
    args = ap.parse_args()

    code, runs = api("GET", f"/repos/{args.owner}/{args.repo}/actions/runs?per_page=1", args.token)
    if code != 200 or not runs.get("workflow_runs"):
        print("拿不到构建记录")
        return
    run = runs["workflow_runs"][0]
    print(f"构建 #{run['run_number']}  status={run['status']}  conclusion={run.get('conclusion')}")
    print(f"{run['html_url']}\n")

    blob = download_logs(args.token, args.owner, args.repo, run["id"])
    if not blob:
        print("日志下载失败（可能过期）。去网页看：", run["html_url"])
        return

    zf = zipfile.ZipFile(io.BytesIO(blob))
    # 优先看编译那一步的日志
    names = [n for n in zf.namelist() if re.search(r"assemble|compile|build", n, re.I)]
    names += [n for n in zf.namelist() if n not in names]

    # GitHub 日志每行前面都带 ISO 时间戳，判断前必须先剥掉，否则 startswith("e: ") 永远不匹配
    stamp = re.compile(r"^\d{4}-\d\d-\d\dT[\d:.]+Z\s?")

    for name in names:
        text = zf.read(name).decode("utf-8", "replace")
        hits = []
        for raw in text.splitlines():
            line = stamp.sub("", raw).rstrip()
            if not line:
                continue
            # Kotlin 编译器错误：e: file:///... : (12, 34): error text
            if line.startswith("e: ") or "error:" in line.lower():
                hits.append(line)
            elif line.startswith("* What went wrong") or line.startswith("Caused by") \
                    or "FAILED" in line or line.startswith("> Task"):
                hits.append(line)

        if any(h.startswith("e: ") for h in hits):
            print(f"───── {name} ─────")
            for ln in hits:
                if ln.startswith("e: ") or "FAILED" in ln:
                    print("  " + ln[:400])
            print()
            return

    # 没找到编译错误就打印所有警告行
    for name in names:
        text = zf.read(name).decode("utf-8", "replace")
        hits = [stamp.sub("", l).rstrip() for l in text.splitlines()
                if "What went wrong" in l or "FAILED" in l or "Caused by" in l]
        if hits:
            print(f"───── {name} ─────")
            for ln in hits[:args.lines]:
                print("  " + ln[:300])
            print()
            return

    print("没找到明显报错行，打印最后一个日志文件的尾部：")
    text = zf.read(names[-1]).decode("utf-8", "replace")
    for ln in text.splitlines()[-args.lines:]:
        print("  " + stamp.sub("", ln).rstrip()[:220])


if __name__ == "__main__":
    main()
