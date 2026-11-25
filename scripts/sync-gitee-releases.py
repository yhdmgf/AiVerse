#!/usr/bin/env python3
"""把 GitHub 上已有的 Release APK 历史资产同步到 Gitee（幂等可重跑）。

用法：
  GITEE_TOKEN=<token> python3 scripts/sync-gitee-releases.py            # 同步保留集合
  GITEE_TOKEN=<token> python3 scripts/sync-gitee-releases.py --all      # 同步 GitHub 全部 Release

可选环境变量：
  GH_REPO         GitHub 仓库，默认 yhdmgf/AiVerse
  GITEE_OWNER     Gitee 用户名，默认 aiverse
  GITEE_REPO      Gitee 仓库名，默认 aiverse
  GITHUB_TOKEN    GitHub 令牌（可选，提高 API 限额）
  RELEASE_FILTER  Python 正则，覆盖自动推导，仅同步匹配 tag_name 的 Release
  KEEP_STABLE     保留的正式版数量，默认 3

Gitee v5 说明：
  - 认证：统一用 Authorization: Bearer <token> header，避免 token 进 URL / 日志
  - 创建 Release：tag 不存在时自动在 target_commitish(main) 上创建，无需等待镜像同步
  - 上传附件：POST /releases/{release_id}/attach_files，multipart/form-data，
    文件字段名为 file、带 filename，定位用 release_id（整数）而非 tag
  - 附件上限：Gitee 未公布单文件大小限制，沿用 2GiB 兜底
  - prerelease 标记：本次未依赖，GitHub 的 prerelease 状态不同步
"""

import json
import os
import re
import shutil
import sys
import tempfile
import time
import urllib.parse
import urllib.request
import uuid

UA = "aiverse-gitee-sync"

GH_REPO = os.environ.get("GH_REPO", "yhdmgf/AiVerse")
GITEE_OWNER = os.environ.get("GITEE_OWNER", "aiverse")
GITEE_REPO = os.environ.get("GITEE_REPO", "aiverse")
GITEE_TOKEN = os.environ.get("GITEE_TOKEN", "")
GITHUB_TOKEN = os.environ.get("GITHUB_TOKEN", "")
RELEASE_FILTER = os.environ.get("RELEASE_FILTER", "")
KEEP_STABLE = int(os.environ.get("KEEP_STABLE", "3"))
MAX_ATTACH = 2 * 1024 * 1024 * 1024  # Gitee 未公布上限，2GiB 兜底（GitHub 单附件上限）

GITEE_API = f"https://gitee.com/api/v5/repos/{GITEE_OWNER}/{GITEE_REPO}"
GH_API = f"https://api.github.com/repos/{GH_REPO}"

TAG_RE = re.compile(r"^v(\d+)\.(\d+)\.(\d+)(?:-rc(\d+))?$")


def parse_tag(tag: str) -> tuple | None:
    m = TAG_RE.match(tag)
    if not m:
        return None
    return (int(m.group(1)), int(m.group(2)), int(m.group(3)), bool(m.group(4)))


def http_json(url: str, payload: dict | None = None, method: str | None = None,
              headers: dict | None = None, timeout: int = 120):
    hdrs = {"User-Agent": UA}
    if GITHUB_TOKEN and url.startswith(GH_API):
        hdrs["Authorization"] = f"Bearer {GITHUB_TOKEN}"
    if headers:
        hdrs.update(headers)
    if payload is None:
        req = urllib.request.Request(url, headers=hdrs, method=method)
    else:
        req = urllib.request.Request(
            url,
            data=json.dumps(payload).encode(),
            headers={**hdrs, "Content-Type": "application/json"},
            method=method or "POST",
        )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        body = resp.read().decode()
        if not body:
            return None
        return json.loads(body)


def gitee_get(path: str, params: dict | None = None):
    url = f"{GITEE_API}{path}"
    if params:
        url += "?" + urllib.parse.urlencode(params)
    return http_json(url, headers={"Authorization": f"Bearer {GITEE_TOKEN}"})


def gitee_post(path: str, payload: dict):
    return http_json(
        f"{GITEE_API}{path}",
        payload,
        headers={"Authorization": f"Bearer {GITEE_TOKEN}"},
    )


def gh_get(path: str):
    return http_json(f"{GH_API}{path}")


def download(url: str, dest: str) -> bool:
    for attempt in range(3):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=300) as resp, open(dest, "wb") as f:
                shutil.copyfileobj(resp, f)
            return True
        except Exception as e:
            print(f"    下载失败（第 {attempt + 1} 次）：{e}")
            time.sleep(2 * (attempt + 1))
    return False


def multipart_upload(release_id: int, path: str) -> bool:
    """Gitee 上传附件：POST attach_files，手拼 multipart/form-data（标准库，无第三方依赖）。"""
    fname = os.path.basename(path)
    boundary = uuid.uuid4().hex
    with open(path, "rb") as f:
        file_data = f.read()
    body = b"".join([
        f"--{boundary}\r\n".encode(),
        (
            f'Content-Disposition: form-data; name="file"; filename="{fname}"\r\n'
            "Content-Type: application/octet-stream\r\n\r\n"
        ).encode(),
        file_data,
        f"\r\n--{boundary}--\r\n".encode(),
    ])
    for attempt in range(3):
        try:
            url = f"{GITEE_API}/releases/{release_id}/attach_files"
            req = urllib.request.Request(
                url,
                data=body,
                method="POST",
                headers={
                    "User-Agent": UA,
                    "Authorization": f"Bearer {GITEE_TOKEN}",
                    "Content-Type": f"multipart/form-data; boundary={boundary}",
                },
            )
            with urllib.request.urlopen(req, timeout=1800) as resp:
                resp.read()
            return True
        except Exception as e:
            print(f"    上传失败（第 {attempt + 1} 次）：{e}")
            time.sleep(2 * (attempt + 1))
    return False


def list_gitee_releases() -> dict:
    out = {}
    page = 1
    while True:
        batch = gitee_get("/releases", {"per_page": 100, "page": page})
        if not isinstance(batch, list):
            raise RuntimeError(f"Gitee releases 列表返回异常: {batch}")
        for r in batch:
            out[r["tag_name"]] = r
        if len(batch) < 100:
            break
        page += 1
    return out


def derive_keep_set(releases: list) -> set:
    parsed = [(parse_tag(r["tag_name"]), r["tag_name"]) for r in releases]
    stable = sorted((p for p, _ in parsed if p and not p[3]), reverse=True)
    keep_main = {s[:3] for s in stable[:KEEP_STABLE]}
    return {t for p, t in parsed if p and not p[3] and p[:3] in keep_main}


def main() -> int:
    if not GITEE_TOKEN:
        print("错误：请通过环境变量提供 GITEE_TOKEN（Gitee 个人访问令牌）")
        return 1
    print(f"源: GitHub {GH_REPO} -> 目标: Gitee {GITEE_OWNER}/{GITEE_REPO}")

    try:
        gitee_get("")
    except Exception as e:
        print(f"错误：无法访问 Gitee 仓库 {GITEE_OWNER}/{GITEE_REPO}：{e}")
        return 1

    gh_releases = []
    page = 1
    while True:
        batch = gh_get(f"/releases?per_page=100&page={page}")
        if not isinstance(batch, list):
            raise RuntimeError(f"GitHub releases 列表返回异常: {batch}")
        gh_releases.extend(batch)
        if len(batch) < 100:
            break
        page += 1

    if "--all" in sys.argv:
        keep = {r["tag_name"] for r in gh_releases}
        print("同步模式: 全部 Release")
    elif RELEASE_FILTER:
        pattern = re.compile(RELEASE_FILTER)
        keep = {r["tag_name"] for r in gh_releases if pattern.match(r["tag_name"])}
        print(f"版本过滤（正则）: {RELEASE_FILTER}")
    else:
        keep = derive_keep_set(gh_releases)
        print(f"保留策略: 仅最新 {KEEP_STABLE} 个正式版（不含 RC）")
    gh_releases = [r for r in gh_releases if r["tag_name"] in keep]
    print(f"GitHub 上 {len(gh_releases)} 个 Release 需同步")

    gitee_releases = list_gitee_releases()
    print(f"Gitee 已有 {len(gitee_releases)} 个 Release")

    ok = skipped = failed = 0
    tmpdir = tempfile.mkdtemp(prefix="gitee-sync-")
    try:
        for rel in sorted(gh_releases, key=lambda r: r["created_at"]):
            tag = rel["tag_name"]
            existing = gitee_releases.get(tag)
            release_id = None
            attach_names = set()
            if existing is not None:
                release_id = existing.get("id")
                attach_names = {
                    a.get("name") for a in existing.get("assets", [])
                    if a.get("name", "").endswith(".apk")
                }
                print(f"[跳过创建] {tag}（已存在，APK 附件 {len(attach_names)} 个）")
            else:
                print(f"[创建] {tag} ...")
                try:
                    created = gitee_post("/releases", {
                        "tag_name": tag,
                        "name": rel.get("name") or f"Release {tag}",
                        "body": rel.get("body") or "",
                        "target_commitish": "main",
                    })
                    if isinstance(created, dict) and (
                        created.get("id") or created.get("tag_name")
                    ):
                        release_id = created.get("id")
                        attach_names = set()
                    else:
                        raise RuntimeError(f"创建返回异常: {created}")
                except Exception as e:
                    print(f"  错误：创建 Release 失败：{e}")
                    failed += 1
                    continue

            if release_id is None:
                print(f"  错误：缺少 release_id，无法上传附件：{tag}")
                failed += 1
                continue

            for asset in rel.get("assets", []):
                name = asset["name"]
                size = asset["size"]
                if size > MAX_ATTACH:
                    print(f"  [跳过] {name}（{size / 1048576:.1f}MB 超过 {MAX_ATTACH // 1048576}MB 上限）")
                    skipped += 1
                    continue
                if name in attach_names:
                    print(f"  [已存在] {name}")
                    skipped += 1
                    continue
                dest = os.path.join(tmpdir, name)
                print(f"  下载 {name}（{size / 1048576:.1f}MB）...")
                if not download(asset["browser_download_url"], dest):
                    print(f"  [失败] {name} 下载失败")
                    failed += 1
                    continue
                if multipart_upload(release_id, dest):
                    print(f"  [成功] {name} -> Gitee Release {tag}")
                    ok += 1
                else:
                    print(f"  [失败] {name} 上传失败")
                    failed += 1
                os.remove(dest)
    finally:
        shutil.rmtree(tmpdir, ignore_errors=True)

    print(f"\n完成：成功 {ok}，跳过 {skipped}，失败 {failed}")
    if failed:
        print("有失败项，重跑本脚本即可续传（已成功的会跳过）。")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
