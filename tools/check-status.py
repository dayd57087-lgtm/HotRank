#!/usr/bin/env python3
"""查看 HotRank 仓库状态：分支、文件数、Actions 构建情况。带重试，容忍网络抖动。"""
import json
import ssl
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

TOKEN = Path("/root/.ghtoken").read_text().strip()
OWNER = "dayd57087-lgtm"
REPO = "HotRank"

# 每次新建 SSL 上下文，并把 keep-alive 关掉 —— 这台设备上长连接容易被重置
CTX = ssl.create_default_context()


def call(path, retries=6):
    last = None
    for attempt in range(retries):
        request = urllib.request.Request("https://api.github.com" + path)
        for key, value in {
            "Authorization": "Bearer " + TOKEN,
            "Accept": "application/vnd.github+json",
            "User-Agent": "minis",
            "Connection": "close",
        }.items():
            request.add_header(key, value)
        try:
            with urllib.request.urlopen(request, timeout=45, context=CTX) as response:
                return response.status, json.loads(response.read().decode() or "{}")
        except urllib.error.HTTPError as exc:
            return exc.code, json.loads(exc.read().decode() or "{}")
        except Exception as exc:
            last = exc
            if attempt < retries - 1:
                time.sleep(1.5 * (attempt + 1))
    return 0, {"message": f"{type(last).__name__}: {last}"}


def main():
    code, ref = call(f"/repos/{OWNER}/{REPO}/git/ref/heads/main")
    if code == 200:
        print("main 分支:", ref["object"]["sha"][:7])
    elif code == 409:
        print("main 分支: 仓库还是空的（首次推送没成功）")
    else:
        print(f"main 分支: 查询失败 ({code}) {ref.get('message')}")

    code, tree = call(f"/repos/{OWNER}/{REPO}/git/trees/main?recursive=1")
    if code == 200:
        paths = [t["path"] for t in tree.get("tree", [])]
        print(f"仓库文件数: {len(paths)}")
        for path in paths:
            print("   ", path)
    elif code != 409:
        print(f"树查询失败 ({code}) {tree.get('message')}")

    code, runs = call(f"/repos/{OWNER}/{REPO}/actions/runs?per_page=3")
    if code == 200:
        found = runs.get("workflow_runs", [])
        if not found:
            print("\n还没有任何构建记录")
        for run in found:
            print(f"\n构建 #{run['run_number']}  status={run['status']}  "
                  f"conclusion={run.get('conclusion')}")
            print(f"   {run['html_url']}")
    else:
        print(f"\nActions 查询失败 ({code}) {runs.get('message')}")


if __name__ == "__main__":
    main()
