#!/usr/bin/env python3
"""发布 Android APK 到 GitHub 和 Gitee。

用法:
  python3 tools/publish_release.py 0.17

前置条件:
  - 已执行 ./gradlew assembleDebug
  - docs/android-port/RELEASE-0.17.md 已存在
  - 当前提交已经准备好 main 分支内容
  - GitHub 凭据可由 git credential 获取
  - Gitee token 放在 GITEE_TOKEN，或 .git/gitee-credentials
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GITHUB_API = "https://api.github.com/repos/1182980735/wiliwili_android"
GITEE_API = "https://gitee.com/api/v5/repos/anhuihl/wiliwili_android"
GITHUB_UPLOAD = "https://uploads.github.com/repos/1182980735/wiliwili_android"
GITEE_WEB = "https://gitee.com/anhuihl/wiliwili_android"
GITHUB_WEB = "https://github.com/1182980735/wiliwili_android"


def run(cmd: list[str], *, capture: bool = False, check: bool = True) -> subprocess.CompletedProcess:
    return subprocess.run(cmd, cwd=ROOT, text=True, capture_output=capture, check=check)


def git_head() -> str:
    return run(["git", "rev-parse", "HEAD"], capture=True).stdout.strip()


def github_token() -> str:
    token = os.environ.get("GITHUB_TOKEN", "").strip()
    if token:
        return token
    p = subprocess.run(
        ["git", "credential", "fill"],
        cwd=ROOT,
        input="protocol=https\nhost=github.com\n\n",
        text=True,
        capture_output=True,
        check=True,
    )
    for line in p.stdout.splitlines():
        if line.startswith("password="):
            return line.split("=", 1)[1].strip()
    raise RuntimeError("找不到 GitHub token")


def gitee_token() -> str:
    token = os.environ.get("GITEE_TOKEN", "").strip()
    if token:
        return token
    cred = ROOT / ".git" / "gitee-credentials"
    if cred.exists():
        raw = cred.read_text().strip()
        m = re.search(r"://[^:]+:([^@]+)@", raw)
        if m:
            return m.group(1)
    raise RuntimeError("找不到 Gitee token：请设置 GITEE_TOKEN 或 .git/gitee-credentials")


def request_json(url: str, *, method: str = "GET", headers: dict[str, str] | None = None, body: dict | None = None):
    h = {"Accept": "application/json", "User-Agent": "wiliwili-release"}
    if headers:
        h.update(headers)
    data = None
    if body is not None:
        data = json.dumps(body).encode()
        h["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, headers=h, method=method)
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            raw = r.read().decode()
            return json.loads(raw) if raw.strip() else {}
    except urllib.error.HTTPError as e:
        if e.code == 404:
            return None
        raise RuntimeError(f"HTTP {e.code}: {e.read().decode()[:500]}") from e


def gitee_url(path: str, token: str) -> str:
    sep = "&" if "?" in path else "?"
    return f"{GITEE_API}{path}{sep}access_token={urllib.parse.quote(token)}"


def upload_with_curl(args: list[str], out_file: Path) -> dict:
    p = subprocess.run(args, cwd=ROOT, capture_output=True, text=True)
    if p.returncode != 0:
        raise RuntimeError(f"upload failed: {p.stderr[:800]}")
    try:
        return json.loads(p.stdout)
    except json.JSONDecodeError as e:
        raise RuntimeError(f"upload returned non-json: {p.stdout[:800]}") from e


def ensure_github_release(version: str, commit: str, notes: str, token: str) -> dict:
    tag = f"v{version}"
    h = {"Authorization": f"Bearer {token}"}
    rel = request_json(f"{GITHUB_API}/releases/tags/{tag}", headers=h)
    payload = {
        "tag_name": tag,
        "target_commitish": commit,
        "name": f"wiliwili Android {version}",
        "body": notes,
        "draft": False,
        "prerelease": False,
    }
    if rel is None:
        rel = request_json(f"{GITHUB_API}/releases", method="POST", headers=h, body=payload)
        print("GitHub release created")
    else:
        rel = request_json(f"{GITHUB_API}/releases/{rel['id']}", method="PATCH", headers=h, body=payload)
        print("GitHub release updated")

    for asset in rel.get("assets", []):
        if asset.get("name", "").lower().endswith(".apk"):
            request_json(f"{GITHUB_API}/releases/assets/{asset['id']}", method="DELETE", headers=h)
            print("GitHub old APK removed:", asset.get("name"))

    apk = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
    upload_url = f"{GITHUB_UPLOAD}/releases/{rel['id']}/assets?name=wiliwili-android-{version}.apk"
    uploaded = upload_with_curl(
        [
            "curl", "-sS", "--max-time", "280", "-X", "POST",
            "-H", f"Authorization: Bearer {token}",
            "-H", "Content-Type: application/vnd.android.package-archive",
            "--data-binary", f"@{apk}",
            upload_url,
        ],
        Path("/tmp/github_upload.json"),
    )
    print("GitHub APK uploaded:", uploaded.get("browser_download_url"))
    return rel


def ensure_gitee_release(version: str, commit: str, notes: str, token: str) -> dict:
    tag = f"v{version}"
    rel = request_json(gitee_url(f"/releases/tags/{tag}", token))
    payload = {
        "tag_name": tag,
        "target_commitish": commit,
        "name": f"wiliwili Android {version}",
        "body": notes,
        "prerelease": False,
    }
    if rel is None:
        rel = request_json(gitee_url("/releases", token), method="POST", body=payload)
        print("Gitee release created")
    else:
        rel = request_json(gitee_url(f"/releases/{rel['id']}", token), method="PATCH", body=payload)
        print("Gitee release updated")

    attachments = request_json(gitee_url(f"/releases/{rel['id']}/attach_files", token)) or []
    for asset in attachments:
        if asset.get("name", "").lower().endswith(".apk"):
            request_json(gitee_url(f"/releases/{rel['id']}/attach_files/{asset['id']}", token), method="DELETE")
            print("Gitee old APK removed:", asset.get("name"))

    apk = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
    with tempfile.TemporaryDirectory(prefix="wiliwili-release-") as td:
        upload_file = Path(td) / f"wiliwili-android-{version}.apk"
        shutil.copy2(apk, upload_file)
        uploaded = upload_with_curl(
            [
                "curl", "-sS", "--max-time", "280", "-X", "POST",
                "-H", "User-Agent: wiliwili-release",
                "-F", f"file=@{upload_file};type=application/vnd.android.package-archive",
                gitee_url(f"/releases/{rel['id']}/attach_files", token),
            ],
            Path("/tmp/gitee_upload.json"),
        )
    print("Gitee APK uploaded:", uploaded.get("browser_download_url"))
    return rel


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("version", help="版本号，例如 0.17")
    args = parser.parse_args()
    version = args.version.strip().removeprefix("v")
    tag = f"v{version}"
    commit = git_head()
    apk = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
    notes_path = ROOT / "docs/android-port" / f"RELEASE-{version}.md"
    if not apk.exists():
        raise SystemExit(f"APK 不存在：{apk}，请先执行 assembleDebug")
    if not notes_path.exists():
        raise SystemExit(f"发布说明不存在：{notes_path}")

    notes = notes_path.read_text()
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    print(f"APK size={apk.stat().st_size} sha256={digest}")

    print(f"pushing main and {tag} to GitHub + Gitee ...")
    run(["git", "tag", "-f", tag, commit])
    run(["git", "push", "origin", "main"])
    run(["git", "push", "origin", f"refs/tags/{tag}", "--force"])

    github = ensure_github_release(version, commit, notes, github_token())
    gitee = ensure_gitee_release(version, commit, notes, gitee_token())

    print("GitHub:", github.get("html_url"))
    print("Gitee:", f"{GITEE_WEB}/releases/tag/{tag}")
    print("Done.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
