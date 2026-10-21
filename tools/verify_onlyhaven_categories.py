"""Read-only live contract audit for the native 5.1 category integration."""
import json
import pathlib
import re
import urllib.parse
import urllib.request


def get(url):
    with urllib.request.urlopen(url, timeout=25) as response:
        return response.read().decode()


config = json.loads(pathlib.Path("app/src/main/assets/source_config_defaults.json").read_text())["sources"]["onlyhaven"]
html = get(config["baseUrl"] + "creators")
chunks = [urllib.parse.urljoin(config["baseUrl"], path) for path in
          re.findall(r'(?:src|href)="([^" ]+\.js[^" ]*)', html)]
code = "\n".join(get(url) for url in dict.fromkeys(chunks))
assert "set(`gender`," in code, "Creator category request contract changed"
for key, token in config["categoryValues"].items():
    assert f"`{token}`" in code, f"Category token no longer exposed: {key}"
    route = config["routes"]["creatorCategoryApi"].replace("{category}", token).replace("{limit}", "1").replace("{offset}", "0")
    response = json.loads(get(config["baseUrl"] + route))
    creators = response if isinstance(response, list) else next(
        (response[k] for k in ("creators", "items", "results", "data") if isinstance(response.get(k), list)), None)
    assert creators is not None, f"Unrecognized creator response: {key}"
    print(f"{key}: HTTP 200, {len(creators)} creator(s), response schema recognized")
print("OnlyHaven category contract verified")
