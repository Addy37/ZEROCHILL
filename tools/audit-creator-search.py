#!/usr/bin/env python3
"""Read-only source search probe using bundled configuration. No media is downloaded."""
import concurrent.futures
import json
import pathlib
import re
import urllib.parse
import urllib.request

CONFIG = json.loads((pathlib.Path(__file__).resolve().parents[1]
                     / 'app/src/main/assets/source_config_defaults.json').read_text())['sources']
JOBS = [('fapello', 'CarrotSoki'), ('fapello', 'Carrot Soki'),
        ('fapello', 'sonya blaze'), ('onlyhaven', 'CarrotSoki'), ('onlyhaven', 'jane')]


def rows(value, output):
    if len(output) >= 10:
        return
    if isinstance(value, dict):
        name = next((value[k] for k in ('displayName', 'display_name', 'name', 'title', 'username')
                     if isinstance(value.get(k), str)), '')
        url = next((value[k] for k in ('url', 'profile_url', 'profileUrl', 'href')
                    if isinstance(value.get(k), str)), '')
        if name or url:
            output.append({'name': name, 'url': url, 'keys': sorted(value.keys())})
        for key in ('results', 'models', 'items', 'data', 'suggestions', 'creators'):
            rows(value.get(key), output)
    elif isinstance(value, list):
        for item in value[:20]:
            rows(item, output)


def probe(job):
    source, query = job
    config = CONFIG[source]
    route = config['routes']['search' if source == 'fapello' else 'creatorSearchApi']
    for key, value in {'query': urllib.parse.quote(query), 'limit': '8', 'offset': '0'}.items():
        route = route.replace('{' + key + '}', value)
    headers = dict(config.get('requestHeaders') or {})
    headers.update(config.get('ajaxHeaders') or {})
    headers.pop('Accept-Encoding', None)
    headers['User-Agent'] = config['userAgent']
    headers['Referer'] = config.get('refererOverride') or config['baseUrl']
    output = {'source': source, 'query': query}
    try:
        request = urllib.request.Request(config['baseUrl'] + route, headers=headers)
        with urllib.request.urlopen(request, timeout=15) as response:
            body = response.read(262144).decode('utf-8', 'replace')
            output.update(status=response.status, content_type=response.headers.get('Content-Type'),
                          response_bytes=len(body))
        try:
            data = json.loads(body)
            output['root_keys'] = sorted(data.keys()) if isinstance(data, dict) else ['array']
            output['sample'] = []
            rows(data, output['sample'])
            output['format'] = 'json'
        except (ValueError, TypeError):
            output['format'] = 'non-json'
            output['page_title'] = re.findall(r'<title>(.*?)</title>', body, re.I | re.S)[:1]
    except Exception as error:
        output['error'] = str(error)
    return output


if __name__ == '__main__':
    with concurrent.futures.ThreadPoolExecutor(max_workers=5) as pool:
        for result in pool.map(probe, JOBS):
            print('CREATOR_SEARCH_AUDIT ' + json.dumps(result, ensure_ascii=True), flush=True)
