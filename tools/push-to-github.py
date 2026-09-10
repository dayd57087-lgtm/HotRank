#!/usr/bin/env python3
"""把 HotRank 项目一次性推到 GitHub，并触发 Actions 自动构建 APK。

不需要 git，不需要在手机上敲命令 —— 全部走 GitHub REST API。

用法：
    python3 tools/push-to-github.py --token <PAT> --repo HotRank [--owner 用户名]
    python3 tools/push-to-github.py --token <PAT> --repo HotRank --watch
"""

import argparse
import base64
import json
import os
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

API = "https://api.github.com"
ROOT = Path(__file__).resolve().parent.parent

# 这些目录/文件不进仓库
SKIP_DIRS = {".git", ".gradle", "build", ".idea", "__pycache__", ".kotlin"}
SKIP_NAMES = {".DS_Store", "local.properties", ".ghtoken", ".ghtoken.tmp"}


def call(method, path, token, data=None, retries=4):
    """所有 GitHub API 请求走这里。返回 (status_code, 解析后的响应)。

    本机到 GitHub 的连接偶尔会被重置（RemoteDisconnected），所以做指数退避重试。
    """
    url = path if path.startswith("http") else API + path
    body = json.dumps(data).encode("utf-8") if data is not None else None

    last = (0, {"message": "no attempt"})
    for attempt in range(retries):
        request = urllib.request.Request(url, data=body, method=method)
        request.add_header("Authorization", "Bearer " + token)
        request.add_header("Accept", "application/vnd.github+json")
        request.add_header("X-GitHub-Api-Version", "2022-11-28")
        request.add_header("User-Agent", "minis-push")
        request.add_header("Connection", "close")
        if body is not None:
            request.add_header("Content-Type", "application/json")

        try:
            with urllib.request.urlopen(request, timeout=90) as response:
                raw = response.read().decode("utf-8", "replace")
                return response.status, (json.loads(raw) if raw.strip() else {})
        except urllib.error.HTTPError as exc:
            raw = exc.read().decode("utf-8", "replace")
            try:
                out = json.loads(raw)
            except json.JSONDecodeError:
                out = {"message": raw[:400]}
            # 4xx 是确定性问题，重试没意义
            if exc.code < 500:
                return exc.code, out
            last = (exc.code, out)
        except Exception as exc:
            last = (0, {"message": f"{type(exc).__name__}: {exc}"})

        if attempt < retries - 1:
            time.sleep(2 ** attempt + 1)

    return last


def ensure_initialized(token, owner, repo):
    """空仓库上 Git Data API 会返回 409 'Git Repository is empty.'。
    必须先用 Contents API 造一个提交把仓库激活，之后 blob/tree/commit 才能用。
    这个占位文件不会进最终 tree，所以提交完成后会自然消失。
    """
    code, _ = call("GET", f"/repos/{owner}/{repo}/git/ref/heads/main", token)
    if code == 200:
        return

    print("🔧 仓库还没初始化，先造一个占位提交激活 Git 数据接口")
    code, out = call(
        "PUT",
        f"/repos/{owner}/{repo}/contents/.init",
        token,
        {"message": "init", "content": base64.b64encode(b"init\n").decode("ascii")},
    )
    if code not in (200, 201):
        print(f"❌ 初始化仓库失败 ({code}): {out.get('message')}")
        sys.exit(1)


def collect_files():
    """收集要上传的文件，跳过构建产物和缓存。"""
    files = []
    for path in sorted(ROOT.rglob("*")):
        if not path.is_file():
            continue
        rel = path.relative_to(ROOT)
        if any(part in SKIP_DIRS for part in rel.parts):
            continue
        if rel.name in SKIP_NAMES:
            continue
        files.append((rel.as_posix(), path))
    return files


def upload_tree(token, owner, repo, files):
    """每个文件建一个 blob，再合成 tree。Git Data API 是一次原子的提交。"""
    tree = []
    total = len(files)
    for index, (rel, path) in enumerate(files, 1):
        payload = {
            "content": base64.b64encode(path.read_bytes()).decode("ascii"),
            "encoding": "base64",
        }
        code, out = call("POST", f"/repos/{owner}/{repo}/git/blobs", token, payload)

        if code not in (200, 201):
            message = str(out.get("message", out))
            if "workflow" in message.lower():
                print("\n❌ token 缺少 workflow 权限，推不了 .github/workflows/ 里的文件。")
                print("   classic token 请勾选 `workflow` 这一项，然后重新生成。")
            else:
                print(f"\n❌ 上传 {rel} 失败 ({code}): {message}")
            sys.exit(1)

        mode = "100755" if os.access(path, os.X_OK) else "100644"
        tree.append({"path": rel, "mode": mode, "type": "blob", "sha": out["sha"]})
        print(f"\r  上传中 {index}/{total}  {rel[:48]:<50}", end="", flush=True)

    print()
    code, out = call("POST", f"/repos/{owner}/{repo}/git/trees", token, {"tree": tree})
    if code not in (200, 201):
        print(f"❌ 建 tree 失败 ({code}): {out}")
        sys.exit(1)
    return out["sha"]


def commit_and_push(token, owner, repo, tree_sha):
    """提交并更新 main 分支。空仓库需要新建 ref，非空要带 parent。"""
    code, ref = call("GET", f"/repos/{owner}/{repo}/git/ref/heads/main", token)
    parents = [ref["object"]["sha"]] if code == 200 else []

    code, commit = call(
        "POST",
        f"/repos/{owner}/{repo}/git/commits",
        token,
        {"message": "热榜聚合 MVP：6 站热搜合并流", "tree": tree_sha, "parents": parents},
    )
    if code not in (200, 201):
        print(f"❌ 提交失败 ({code}): {commit}")
        sys.exit(1)

    sha = commit["sha"]
    if parents:
        code, out = call(
            "PATCH",
            f"/repos/{owner}/{repo}/git/refs/heads/main",
            token,
            {"sha": sha, "force": True},
        )
    else:
        code, out = call(
            "POST",
            f"/repos/{owner}/{repo}/git/refs",
            token,
            {"ref": "refs/heads/main", "sha": sha},
        )

    if code not in (200, 201):
        print(f"❌ 更新分支失败 ({code}): {out}")
        sys.exit(1)
    return sha


def wait_for_build(token, owner, repo, timeout=900):
    """轮询 Actions，构建完就把 APK 下载链接打出来。"""
    print("\n⏳ 等 GitHub 编译（首次约 3~5 分钟，要下 Gradle 和依赖）")
    deadline = time.time() + timeout

    while time.time() < deadline:
        code, runs = call(
            "GET",
            f"/repos/{owner}/{repo}/actions/runs?branch=main&per_page=1",
            token,
        )
        if code == 200 and runs.get("workflow_runs"):
            run = runs["workflow_runs"][0]
            status = run.get("status")
            conclusion = run.get("conclusion")

            if status == "completed":
                if conclusion == "success":
                    print("\n✅ 编译成功")
                    return True
                print(f"\n❌ 编译失败 (conclusion={conclusion})")
                print(f"   日志：{run['html_url']}")
                return False

            elapsed = int(time.time() - deadline + timeout)
            print(f"\r  构建中… {elapsed}s  status={status}   ", end="", flush=True)

        time.sleep(15)

    print("\n⚠️ 等待超时，去 Actions 页面看进度")
    return None


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--token", help="GitHub Personal Access Token（或用 --token-file / $GITHUB_TOKEN）")
    parser.add_argument("--token-file", help="从文件读 token，避免明文出现在命令行里")
    parser.add_argument("--repo", default="HotRank", help="仓库名")
    parser.add_argument("--owner", default=None, help="用户名，默认用 token 对应的账号")
    parser.add_argument("--private", action="store_true", help="建成私有仓库（Actions 有免费额度限制）")
    parser.add_argument("--watch", action="store_true", help="推送后等着看构建结果")
    args = parser.parse_args()

    if args.token_file:
        token = Path(args.token_file).read_text(encoding="utf-8").strip()
    elif args.token:
        token = args.token.strip()
    else:
        token = os.environ.get("GITHUB_TOKEN", "").strip()

    if not token:
        print("❌ 没有拿到 token。用 --token / --token-file，或设置 $GITHUB_TOKEN。")
        sys.exit(1)

    # 先验证 token 是谁的
    code, user = call("GET", "/user", token)
    if code != 200:
        print(f"❌ token 无效或没网络 ({code}): {user.get('message')}")
        sys.exit(1)

    owner = args.owner or user["login"]
    repo = args.repo
    print(f"👤 账号：{user['login']}")

    # 仓库不存在就建一个
    code, out = call("GET", f"/repos/{owner}/{repo}", token)
    if code == 404:
        print(f"📦 创建仓库 {owner}/{repo}")
        code, out = call(
            "POST",
            "/user/repos",
            token,
            {
                "name": repo,
                "private": args.private,
                "auto_init": False,
                "description": "多平台热搜聚合榜 · Kotlin + Compose",
            },
        )
        if code not in (200, 201):
            print(f"❌ 建仓库失败 ({code}): {out.get('message')}")
            if code == 403:
                print("   token 需要 `repo` 权限（classic）才能创建仓库。")
            sys.exit(1)
    elif code == 200:
        print(f"📦 仓库已存在，更新内容：{owner}/{repo}")
    else:
        print(f"❌ 访问仓库失败 ({code}): {out.get('message')}")
        sys.exit(1)

    files = collect_files()
    print(f"📄 待上传 {len(files)} 个文件")

    ensure_initialized(token, owner, repo)
    tree_sha = upload_tree(token, owner, repo, files)
    sha = commit_and_push(token, owner, repo, tree_sha)
    print(f"🚀 已推送 {sha[:7]}")

    actions_url = f"https://github.com/{owner}/{repo}/actions"
    apk_url = f"https://github.com/{owner}/{repo}/releases/download/latest-build/app-debug.apk"

    print(f"\n构建进度：{actions_url}")
    print(f"APK 地址（构建完才能下）：{apk_url}")

    if args.watch:
        if wait_for_build(token, owner, repo) is True:
            print(f"\n📲 手机点这个链接直接下载安装：\n{apk_url}")


if __name__ == "__main__":
    main()
