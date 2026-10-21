"""Read-only live contract audit for the native 5.1 category integration."""
import json
import pathlib
import re
import urllib.parse
import urllib.request
import urllib.error
import time


def get(url):
    for attempt in range(3):
        try:
            with urllib.request.urlopen(url, timeout=25) as response:
                return response.read().decode()
        except (urllib.error.URLError, TimeoutError):
            if attempt == 2:
                raise
            time.sleep(attempt + 1)


config = json.loads(pathlib.Path("app/src/main/assets/source_config_defaults.json").read_text())["sources"]["onlyhaven"]
html = get(config["baseUrl"] + "creators")
chunks = [urllib.parse.urljoin(config["baseUrl"], path) for path in
          re.findall(r'(?:src|href)="([^" ]+\.js[^" ]*)', html)]
code = "\n".join(get(url) for url in dict.fromkeys(chunks))
assert "set(`gender`," in code, "Creator category request contract changed"
for key, token in config["categoryValues"].items():
    assert f"`{token}`" in code, f"Category token no longer exposed: {key}"
    route = config["routes"]["creatorCategoryApi"].replace("{category}", token).replace("{limit}", "50").replace("{offset}", "0")
    response = json.loads(get(config["baseUrl"] + route))
    creators = response if isinstance(response, list) else next(
        (response[k] for k in ("creators", "items", "results", "data") if isinstance(response.get(k), list)), None)
    assert creators is not None, f"Unrecognized creator response: {key}"
    checked = 0
    for creator in creators:
        # ThumbHash is placeholder metadata. Verify the actual portrait endpoint,
        # rather than merely recognizing the JSON array as the earlier audit did.
        if not creator.get("avatarThumbhash"):
            continue
        service = urllib.parse.quote(str(creator["service"]), safe="")
        identity = urllib.parse.quote(str(creator["id"]), safe="")
        portrait = config["imageBaseUrl"] + f"creator/{service}/{identity}/avatar.webp"
        request = urllib.request.Request(portrait, headers={"Referer": config["baseUrl"]})
        with urllib.request.urlopen(request, timeout=25) as image:
            data = image.read()
            assert image.headers.get_content_type().startswith("image/"), f"Non-image portrait: {key}"
            assert len(data) > 256, f"Empty portrait: {key}"
        checked += 1
        break
    if checked:
        print(f"{key}: HTTP 200, {len(creators)} creator(s), portrait HTTP 200/image verified")
    else:
        print(f"{key}: HTTP 200, {len(creators)} creator(s), no avatar metadata in current sample")
print("OnlyHaven category contract verified")
