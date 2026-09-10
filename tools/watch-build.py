#!/usr/bin/env python3
"""触发 GitHub Actions 构建并轮询结果。

用法：
    python3 tools/watch-build.py --token <PAT> [--owner X --repo HotRank] [--dispatch]
"""

import argparse
import json
import sys
import time
import urllib.error
import urllib.request

API = "https://api.github.com"


def call(method, path, token, data=None):
    body = json.dumps(data).encode() if data is not None else None
    request = urllib.request.Request(API + path, data=body, method=method)
    request.add_header("Authorization", "Bearer " + token)
    request.add_header("Accept", "application/vnd.github+json")
    request.add_header("X-GitHub-Api-Version", "2022-11-28")
    request.add_header("User-Agent", "minis-watch")
    if body:
        request.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            raw = response.read().decode("utf-8", "replace")
            return response.status, (json.loads(raw) if raw.strip() else {})
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode("utf-8", "replace")
        try:
            return exc.code, json.loads(raw)
        except json.JSONDecodeError:
            return exc.code, {"message": raw[:300]}
    except Exception as exc:
        return 0, {"message": f"{type(exc).__name__}: {exc}"}


def fail_diagnostics(token, owner, repo):
    """构建失败时把出错的那几行日志捞出来，不用人再去点网页。"""
    code, runs = call("GET", f"/repos/{owner}/{repo}/actions/runs?per_page=1", token)
    if code != 200 or not runs.get("workflow_runs"):
        return
    run = runs["workflow_runs"][0]
    print(f"\n详情：{run['html_url']}")

    code, jobs = call("GET", f"/repos/{owner}/{repo}/actions/runs/{run['id']}/jobs", token)
    if code != 200:
        return
    for job in jobs.get("jobs", []):
        for step in job.get("steps", []):
            if step.get("conclusion") == "failure":
                print(f"  ❌ 失败步骤：{step['name']}")

    # 直接下日志（zip），不用登录网页
    code, _ = call("GET", f"/repos/{owner}/{repo}/actions/runs/{run['id']}/logs", token)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--token", required=True)
    parser.add_argument("--owner", required=True)
    parser.add_argument("--repo", default="HotRank")
    parser.add_argument("--dispatch", action="store_true", help="先手动触发一次")
    parser.add_argument("--timeout", type=int, default=800)
    args = parser.parse_args()

    token, owner, repo = args.token, args.owner, args.repo

    if args.dispatch:
        code, out = call(
            "POST",
            f"/repos/{owner}/{repo}/actions/workflows/build-apk.yml/dispatches",
            token,
            {"ref": "main"},
        )
        if code in (204, 200):
            print("✅ 已触发构建")
        else:
            print(f"⚠️ 触发返回 {code}: {out.get('message')}")

    print("⏳ 等待构建（首次要下 Gradle 8.7 + 依赖，约 3~5 分钟）")
    deadline = time.time() + args.timeout
    last = None

    while time.time() < deadline:
        code, runs = call("GET", f"/repos/{owner}/{repo}/actions/runs?per_page=1", token)
        if code == 200 and runs.get("workflow_runs"):
            run = runs["workflow_runs"][0]
            status, conclusion = run.get("status"), run.get("conclusion")

            if status == "completed":
                if conclusion == "success":
                    print("\n✅ 构建成功")
                    print(
                        f"📲 APK 下载：https://github.com/{owner}/{repo}"
                        "/releases/download/latest-build/app-debug.apk"
                    )
                    return 0
                print(f"\n❌ 构建失败：{conclusion}")
                fail_diagnostics(token, owner, repo)
                return 1

            if status != last:
                print(f"\n  状态 -> {status}")
                last = status
            else:
                print(".", end="", flush=True)
        else:
            print("·", end="", flush=True)

        time.sleep(12)

    print("\n⚠️ 等待超时")
    return 2


if __name__ == "__main__":
    sys.exit(main())
