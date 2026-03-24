#!/usr/bin/env python3
"""
查询 OpenAI 兼容 API 的可用模型 ID（GET …/models）。

Python 执行示例（PowerShell，在项目根目录）：
  python toolkits\\list_openai_models.py
  python toolkits\\list_openai_models.py --raw
  python toolkits\\list_openai_models.py `
    --base-url https://ark.cn-beijing.volces.com/api/v3 --api-key <YOUR_KEY>

火山方舟（Ark）OpenAI 兼容「列出模型」请求 URL 示例（脚本会在 base-url 后拼接 /models）：
  https://ark.cn-beijing.volces.com/api/v3/models

base-url：仅来自命令行 --base-url；未传时使用模块内 DEFAULT_OPENAI_BASE_URL（不读环境变量）。

api-key 使用顺序（后者仅在前者未提供时生效）：命令行 --api-key → 模块内 DEFAULT_OPENAI_API_KEY →
项目根或当前工作目录下 .env 中的 VOLCENGINE_API_KEY（若无则尝试 ARK_API_KEY）。
未传 --api-key 时才会尝试默认变量与 .env；若显式传入空字符串则不再回退。可复制 .env.example 为 .env。
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from pathlib import Path
from typing import Any

import httpx

# DEFAULT_OPENAI_BASE_URL = "https://wyaigw-sales-jvs.wuyinggw.com/v1"
# DEFAULT_OPENAI_API_KEY = "eyJhbGciOiJIUzI1NiIsImtpZCI6Ind1eWluZy1rZXktMSJ9.eyJqdGkiOiJpTHl6eTBENTZrWWVfYjNBMDVIeHh3IiwiaWF0IjoxNzczOTkxNDA2LCJleHAiOjE3NzQwMDIyMDYsIm5iZiI6MTc3Mzk5MTM0NiwidWlkIjoicThyc2R4aXlqYTFlN29iNDV1bWxndDlrbjJ3dmYwaDYiLCJhbGlVaWQiOiIxNDgzMzQxODQyNTUxMTQxIiwicmVzb3VyY2VUeXBlIjoiQ2xhd1BsYW4iLCJpbnN0YW5jZUlkIjoid3MtMGMyemtseXk4OTNhOHFraGwifQ.B_p1HwKJGByHuJ1fDi-bDb0kdWNr_FkvLA8V8y5squ8"

# DEFAULT_OPENAI_BASE_URL = "https://ark.cn-beijing.volces.com/api/coding/v3"
DEFAULT_OPENAI_BASE_URL = "https://ark.cn-beijing.volces.com/api/v3"
DEFAULT_OPENAI_API_KEY: str | None = None


def _repo_root() -> Path:
    return Path(__file__).resolve().parent.parent


def _find_dotenv_path() -> Path | None:
    cwd = Path.cwd() / ".env"
    if cwd.is_file():
        return cwd
    root = _repo_root() / ".env"
    if root.is_file():
        return root
    return None


def _parse_dotenv_file(path: Path) -> dict[str, str]:
    text = path.read_text(encoding="utf-8")
    out: dict[str, str] = {}
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export "):
            line = line[7:].strip()
        if "=" not in line:
            continue
        key, _, val = line.partition("=")
        key = key.strip()
        val = val.strip()
        if len(val) >= 2 and val[0] == val[-1] and val[0] in "\"'":
            val = val[1:-1]
        out[key] = val
    return out


def _api_key_from_dotenv() -> str | None:
    path = _find_dotenv_path()
    if path is None:
        return None
    env_vars = _parse_dotenv_file(path)
    for name in ("VOLCENGINE_API_KEY", "ARK_API_KEY"):
        v = env_vars.get(name, "").strip()
        if v:
            return v
    return None


def _resolve_api_key(api_key_arg: str | None) -> str | None:
    """参数 → DEFAULT_OPENAI_API_KEY → .env。api_key_arg 为 None 表示未传 --api-key。"""
    if api_key_arg is not None:
        return api_key_arg.strip() or None
    if DEFAULT_OPENAI_API_KEY:
        s = DEFAULT_OPENAI_API_KEY.strip()
        if s:
            return s
    return _api_key_from_dotenv()


def _normalize_base_url(base: str) -> str:
    """补全为 OpenAI 兼容根路径：无版本后缀时追加 /v1；已有 /v1、/v3 等则不重复追加。"""
    base = base.rstrip("/")
    if re.search(r"/v\d+$", base, re.IGNORECASE):
        return base
    if not base.endswith("/v1"):
        base = f"{base}/v1"
    return base


def fetch_models_json(base_url: str, api_key: str | None, timeout: float) -> dict[str, Any]:
    url = f"{_normalize_base_url(base_url)}/models"
    headers: dict[str, str] = {}
    if api_key:
        headers["Authorization"] = f"Bearer {api_key}"

    with httpx.Client(timeout=timeout) as client:
        print(url, file=sys.stderr)
        response = client.get(url, headers=headers)
    response.raise_for_status()
    payload = response.json()
    if not isinstance(payload, dict):
        raise ValueError(f"期望 JSON 对象，实际为: {type(payload).__name__}")
    return payload


def extract_model_ids(payload: dict[str, Any]) -> list[str]:
    """解析常见 OpenAI 兼容格式：{ \"data\": [ { \"id\": \"...\" }, ... ] }"""
    data = payload.get("data")
    if not isinstance(data, list):
        raise ValueError('响应中缺少 \"data\" 列表或格式不符合 OpenAI 兼容约定')

    ids: list[str] = []
    for item in data:
        if isinstance(item, dict) and "id" in item:
            mid = item.get("id")
            if isinstance(mid, str) and mid:
                ids.append(mid)
    return sorted(set(ids))


def main() -> int:
    parser = argparse.ArgumentParser(
        description="列出 OpenAI 兼容 API 可用的 model id（/v1/models）"
    )
    parser.add_argument(
        "--base-url",
        default=DEFAULT_OPENAI_BASE_URL,
        help="API 根地址，可带或不带 /v1（未传时使用 DEFAULT_OPENAI_BASE_URL，不读环境变量）",
    )
    parser.add_argument(
        "--api-key",
        default=None,
        metavar="KEY",
        help="Bearer Token；省略时依次使用 DEFAULT_OPENAI_API_KEY、.env（VOLCENGINE_API_KEY / ARK_API_KEY）",
    )
    parser.add_argument(
        "--timeout",
        type=float,
        default=30.0,
        help="请求超时秒数",
    )
    parser.add_argument(
        "--raw",
        action="store_true",
        help="打印完整 JSON 响应到 stdout",
    )
    args = parser.parse_args()
    api_key = _resolve_api_key(args.api_key)

    try:
        payload = fetch_models_json(args.base_url, api_key, args.timeout)
    except httpx.HTTPStatusError as e:
        print(f"HTTP 错误: {e.response.status_code} {e.response.reason_phrase}", file=sys.stderr)
        try:
            print(e.response.text[:2000], file=sys.stderr)
        except Exception:
            pass
        return 1
    except httpx.RequestError as e:
        print(f"请求失败: {e}", file=sys.stderr)
        return 1
    except (ValueError, json.JSONDecodeError) as e:
        print(f"解析失败: {e}", file=sys.stderr)
        return 1

    if args.raw:
        data = payload.get("data")
        n = len(data) if isinstance(data, list) else 0
        print(f"模型数量: {n}", file=sys.stderr)
        print(json.dumps(payload, ensure_ascii=False, indent=2))
        return 0

    try:
        ids = extract_model_ids(payload)
    except ValueError as e:
        print(f"{e}", file=sys.stderr)
        if os.environ.get("DEBUG"):
            print(json.dumps(payload, ensure_ascii=False, indent=2)[:4000], file=sys.stderr)
        return 1

    print(f"模型数量: {len(ids)}", file=sys.stderr)
    for mid in ids:
        print(mid)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
