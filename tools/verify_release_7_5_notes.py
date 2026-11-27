#!/usr/bin/env python3
"""Verify the coordinated GitHub, update dialog, and bundled release copy."""
import pathlib
import sys

title = "# ZEROCHILL 7.5: The Experience Update"
version, code = sys.argv[1:]
release = pathlib.Path("RELEASE_NOTES.md").read_text()
bundled = pathlib.Path("app/src/main/res/raw/whats_new_7_0.md").read_text()
assert pathlib.Path("RELEASE_VERSION").read_text().strip() == version == "7.5.0"
assert pathlib.Path("RELEASE_VERSION_CODE").read_text().strip() == code == "7005081"
assert release.splitlines()[0] == bundled.splitlines()[0] == title
assert f"`{code}`" in release
summary = release.split("\n\n")[1]
assert len(summary) <= 180 and "\n" not in summary
assert summary.startswith("New sources. Smarter search. Your content, your way.")
assert summary.endswith("NO LIMITS. ALL CONTENT.")
assert 10 <= sum(line.startswith("- ") for line in bundled.splitlines()) <= 15
print(title.removeprefix("# "))
print(summary)
