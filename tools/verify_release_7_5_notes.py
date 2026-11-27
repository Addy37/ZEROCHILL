#!/usr/bin/env python3
"""Verify the coordinated GitHub, update dialog, and bundled release copy."""
import pathlib
import sys

title = "# ZEROCHILL 7.5: The Experience Update"
version, code = sys.argv[1:]
release = pathlib.Path("RELEASE_NOTES.md").read_text()
bundled = pathlib.Path("app/src/main/res/raw/whats_new_7_0.md").read_text()
assert pathlib.Path("RELEASE_VERSION").read_text().strip() == version == "7.5.0"
assert pathlib.Path("RELEASE_VERSION_CODE").read_text().strip() == code == "7005085"
assert release.splitlines()[0] == bundled.splitlines()[0] == title
assert f"`{code}`" in release
summary = release.split("\n\n")[1]
assert len(summary) <= 180 and "\n" not in summary
assert summary == "Five independent ShitTok feeds, new sources, smarter search, custom collections, redesigned menus and smoother playback. NO LIMITS. ALL CONTENT."
assert summary.endswith("NO LIMITS. ALL CONTENT.")
highlights = [line[2:] for line in bundled.splitlines() if line.startswith("- ")]
assert len(highlights) == 8
assert all(len(line) <= 90 for line in highlights)
print(title.removeprefix("# "))
print(summary)
