#!/usr/bin/env bash
set -euo pipefail

branch="${1:-main}"
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

git push origin "$branch"
git push gitee "$branch"
git push origin --tags
git push gitee --tags
