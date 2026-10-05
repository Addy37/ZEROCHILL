#!/usr/bin/env python3
"""Probe BaddieHub for ShitTok-compatible portrait videos without changing the app.

The probe crawls recent listing pages, resolves the clean-tube player payload to the
original MP4 URL, then asks ffprobe for the real video dimensions. It does not save
or upload video files.
"""

from __future__ import annotations

import argparse
import base64
import json
import re
import subprocess
import sys
from dataclasses import asdict, dataclass
from typing import Iterable
from urllib.parse import parse_qs, unquote, urljoin, urlparse

import requests
from bs4 import BeautifulSoup

BASE = "https://baddiehub.com/"
LATEST_FIRST = BASE + "?filter=latest"
USER_AGENT = (
    "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36"
)
TIMEOUT = 15


@dataclass
class ProbeResult:
    title: str
    page_url: str
    media_url: str
    width: int
    height: int
    rotation: int
    portrait: bool


def listing_url(page: int) -> str:
    if page <= 1:
        return LATEST_FIRST
    return f"{BASE}page/{page}/?filter=latest"


def session() -> requests.Session:
    result = requests.Session()
    result.headers.update(
        {
            "User-Agent": USER_AGENT,
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language": "en-US,en;q=0.9",
        }
    )
    return result


def get_html(client: requests.Session, url: str) -> str:
    response = client.get(url, timeout=TIMEOUT, allow_redirects=True)
    response.raise_for_status()
    return response.text


def is_candidate_page(url: str) -> bool:
    parsed = urlparse(url)
    host = (parsed.hostname or "").lower()
    if host not in {"baddiehub.com", "www.baddiehub.com"}:
        return False
    path = parsed.path.strip("/")
    if not path or "/" in path:
        return False
    lowered = path.lower()
    blocked = {
        "categories",
        "category",
        "tags",
        "tag",
        "login",
        "register",
        "privacy-policy",
        "dmca",
        "contact",
    }
    if lowered in blocked or lowered.startswith("wp-"):
        return False
    if parsed.query and "filter=" in parsed.query.lower():
        return False
    return True


def recent_pages(client: requests.Session, pages: int, limit: int) -> list[str]:
    found: list[str] = []
    seen: set[str] = set()
    for page in range(1, pages + 1):
        html = get_html(client, listing_url(page))
        soup = BeautifulSoup(html, "html.parser")
        for anchor in soup.select("a[href]"):
            candidate = urljoin(listing_url(page), anchor.get("href", ""))
            if not is_candidate_page(candidate):
                continue
            # Current BaddieHub video cards contain thumbnail images. Prefer those so
            # category/navigation links are not treated as videos. Individual pages
            # still get validated by the player resolver below.
            scope = anchor
            has_image = scope.find("img") is not None
            if not has_image and anchor.parent is not None:
                has_image = anchor.parent.find("img") is not None
            if not has_image:
                continue
            canonical = candidate.split("#", 1)[0].split("?", 1)[0]
            if canonical in seen:
                continue
            seen.add(canonical)
            found.append(canonical)
            if len(found) >= limit:
                return found
    return found


def decode_player(frame_url: str) -> tuple[str, str]:
    query = parse_qs(urlparse(frame_url).query)
    encoded = query.get("q", [""])[0]
    if not encoded:
        return "", ""
    try:
        payload = base64.b64decode(encoded).decode("utf-8", errors="replace")
    except Exception:
        return "", ""
    values = parse_qs(payload)
    tag_html = values.get("tag", [""])[0]
    if not tag_html:
        tag_html = unquote(payload)
    fragment = BeautifulSoup(tag_html, "html.parser")
    source = fragment.find("source", src=True)
    video = fragment.find("video")
    media_url = source.get("src", "").strip() if source else ""
    poster = video.get("poster", "").strip() if video else ""
    return media_url, poster


def resolve_media(client: requests.Session, page_url: str) -> tuple[str, str]:
    html = get_html(client, page_url)
    soup = BeautifulSoup(html, "html.parser")
    title_node = soup.find("h1")
    title = title_node.get_text(" ", strip=True) if title_node else page_url.rstrip("/").rsplit("/", 1)[-1]
    for frame in soup.select("iframe[src]"):
        frame_url = urljoin(page_url, frame.get("src", ""))
        if "player-x.php" not in frame_url or "q=" not in frame_url:
            continue
        media_url, _ = decode_player(frame_url)
        if media_url.lower().split("?", 1)[0].endswith((".mp4", ".m4v", ".webm")):
            return title, media_url
    return title, ""


def int_value(value: object) -> int:
    try:
        return int(value)  # type: ignore[arg-type]
    except (TypeError, ValueError):
        return 0


def video_dimensions(media_url: str, referer: str) -> tuple[int, int, int]:
    headers = f"Referer: {referer}\r\nUser-Agent: {USER_AGENT}\r\n"
    command = [
        "ffprobe",
        "-v",
        "error",
        "-rw_timeout",
        "12000000",
        "-headers",
        headers,
        "-show_streams",
        "-of",
        "json",
        media_url,
    ]
    completed = subprocess.run(command, capture_output=True, text=True, timeout=20, check=False)
    if completed.returncode != 0:
        raise RuntimeError(completed.stderr.strip() or "ffprobe failed")
    document = json.loads(completed.stdout)
    for stream in document.get("streams", []):
        if stream.get("codec_type") != "video":
            continue
        width = int_value(stream.get("width"))
        height = int_value(stream.get("height"))
        rotation = int_value(stream.get("tags", {}).get("rotate"))
        for side_data in stream.get("side_data_list", []):
            if "rotation" in side_data:
                rotation = int_value(side_data.get("rotation"))
                break
        rotation = abs(rotation) % 360
        if rotation in {90, 270}:
            width, height = height, width
        return width, height, rotation
    return 0, 0, 0


def probe(client: requests.Session, page_urls: Iterable[str], max_resolved: int) -> tuple[list[ProbeResult], list[str]]:
    results: list[ProbeResult] = []
    errors: list[str] = []
    for page_url in page_urls:
        if len(results) >= max_resolved:
            break
        try:
            title, media_url = resolve_media(client, page_url)
            if not media_url:
                errors.append(f"NO_MEDIA {page_url}")
                continue
            width, height, rotation = video_dimensions(media_url, page_url)
            if width <= 0 or height <= 0:
                errors.append(f"NO_DIMENSIONS {page_url} -> {media_url}")
                continue
            results.append(
                ProbeResult(
                    title=title,
                    page_url=page_url,
                    media_url=media_url,
                    width=width,
                    height=height,
                    rotation=rotation,
                    portrait=height > width,
                )
            )
        except Exception as error:
            errors.append(f"ERROR {page_url}: {error}")
    return results, errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--pages", type=int, default=3)
    parser.add_argument("--candidates", type=int, default=30)
    parser.add_argument("--resolved", type=int, default=18)
    parser.add_argument("--json-out", default="baddiehub-probe.json")
    args = parser.parse_args()

    client = session()
    try:
        pages = recent_pages(client, max(1, args.pages), max(1, args.candidates))
    except Exception as error:
        print(f"Listing fetch failed: {error}", file=sys.stderr)
        return 2

    print(f"Candidate pages: {len(pages)}")
    results, errors = probe(client, pages, max(1, args.resolved))
    portrait = [item for item in results if item.portrait]
    landscape = [item for item in results if not item.portrait]

    for item in results:
        orientation = "PORTRAIT" if item.portrait else "NONPORTRAIT"
        print(
            f"[{orientation}] {item.width}x{item.height} rot={item.rotation:>3} "
            f"{item.title} | {item.page_url} | {item.media_url}"
        )
    for error in errors[:12]:
        print(error, file=sys.stderr)

    output = {
        "candidate_pages": len(pages),
        "resolved": len(results),
        "portrait": len(portrait),
        "nonportrait": len(landscape),
        "errors": errors,
        "results": [asdict(item) for item in results],
    }
    with open(args.json_out, "w", encoding="utf-8") as handle:
        json.dump(output, handle, indent=2)

    print(
        f"SUMMARY resolved={len(results)} portrait={len(portrait)} "
        f"nonportrait={len(landscape)} errors={len(errors)}"
    )
    if len(results) < 3:
        print("Probe failed: fewer than three playable videos resolved.", file=sys.stderr)
        return 3
    if not portrait:
        print("Probe failed: no portrait videos found in the sample.", file=sys.stderr)
        return 4
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
