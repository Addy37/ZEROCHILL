#!/usr/bin/env python3
"""Verify the coordinated GitHub, update dialog, and bundled release copy."""
import pathlib
import sys
import xml.etree.ElementTree as ET

title = "# ZEROCHILL 7.5: The Experience Update"
version, code = sys.argv[1:]
release = pathlib.Path("RELEASE_NOTES.md").read_text()
bundled = pathlib.Path("app/src/main/res/raw/whats_new_7_0.md").read_text()
assert pathlib.Path("RELEASE_VERSION").read_text().strip() == version
assert pathlib.Path("RELEASE_VERSION_CODE").read_text().strip() == code
assert f"`{code}`" in release
summary = release.split("\n\n")[1]
assert len(summary) <= 180 and "\n" not in summary
if version == "7.5.0":
    assert code == "7005085"
    assert summary == "Five independent ShitTok feeds, new sources, smarter search, custom collections, redesigned menus and smoother playback. NO LIMITS. ALL CONTENT."
    assert summary.endswith("NO LIMITS. ALL CONTENT.")
    highlights = [line[2:] for line in bundled.splitlines() if line.startswith("- ")]
    assert len(highlights) == 8
    assert all(len(line) <= 90 for line in highlights)
else:
    assert version == "7.5.1" and code == "7005100"
    title = "# ZEROCHILL 7.5.1"
    strings = {node.attrib["name"]: node.text for node in ET.parse(
        "app/src/main/res/values/release_7_5_1.xml").getroot()}
    assert summary == strings["whats_new_summary"] == "Better previews. Smarter search."
    bullets = [line for line in release.splitlines() if line.startswith("- ")]
    assert len(bullets) == 3
    assert bundled.splitlines() == [title, "", summary, "", *bullets]
    assert ("## " + strings["pawchive_preview_notice_title"] + "\n\n"
            + strings["pawchive_preview_notice_body"].replace("\\'", "'") + "\n") in release
assert release.splitlines()[0] == bundled.splitlines()[0] == title
print(title.removeprefix("# "))
print(summary)
