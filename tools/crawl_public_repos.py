#!/usr/bin/env python3
"""Read-only GitHub metadata crawl for public CVE-2026-114938 repositories."""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request


DEFAULT_QUERY = "CVE-2026-114938 in:name,description,readme"
SEARCH_URL = "https://api.github.com/search/repositories"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="List public GitHub repositories mentioning CVE-2026-114938."
    )
    parser.add_argument("--query", default=DEFAULT_QUERY)
    parser.add_argument("--limit", type=int, default=100)
    parser.add_argument(
        "--json", action="store_true", help="Print one JSON array instead of TSV."
    )
    return parser.parse_args()


def fetch(query: str, limit: int) -> dict:
    if not 1 <= limit <= 100:
        raise ValueError("--limit must be between 1 and 100")

    url = SEARCH_URL + "?" + urllib.parse.urlencode(
        {"q": query, "per_page": limit, "sort": "updated", "order": "desc"}
    )
    headers = {
        "Accept": "application/vnd.github+json",
        "User-Agent": "java2shell-public-research-crawler",
        "X-GitHub-Api-Version": "2022-11-28",
    }
    token = os.environ.get("GITHUB_TOKEN")
    if token:
        headers["Authorization"] = f"Bearer {token}"

    request = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(request, timeout=20) as response:
        return json.load(response)


def normalized_items(payload: dict) -> list[dict]:
    result = []
    for item in payload.get("items", []):
        result.append(
            {
                "repository": item.get("full_name"),
                "updated_at": item.get("updated_at"),
                "stars": item.get("stargazers_count"),
                "size_kb": item.get("size"),
                "url": item.get("html_url"),
                "description": item.get("description") or "",
            }
        )
    result.sort(key=lambda item: item["updated_at"] or "", reverse=True)
    return result


def main() -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="backslashreplace")

    args = parse_args()
    try:
        payload = fetch(args.query, args.limit)
    except (ValueError, urllib.error.URLError, json.JSONDecodeError) as error:
        print(f"crawl failed: {error}", file=sys.stderr)
        return 1

    items = normalized_items(payload)
    if args.json:
        print(
            json.dumps(
                {"total_count": payload.get("total_count", 0), "items": items},
                indent=2,
                sort_keys=True,
            )
        )
        return 0

    print(f"total_count\t{payload.get('total_count', 0)}")
    print("repository\tupdated_at\tstars\tsize_kb\turl\tdescription")
    for item in items:
        description = item["description"].replace("\t", " ").replace("\n", " ")
        print(
            "\t".join(
                str(item[key])
                for key in ("repository", "updated_at", "stars", "size_kb", "url")
            )
            + "\t"
            + description
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
