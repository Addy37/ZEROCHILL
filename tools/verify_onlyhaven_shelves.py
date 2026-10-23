#!/usr/bin/env python3
"""Read-only metadata evidence for category shelves. Never fetch media or change config."""
import json
import re
import urllib.parse as url
from audit_source_contracts import CONFIG, fetch, route, rows, creator, require


def main():
    source = CONFIG['onlyhaven']
    base = source['baseUrl']
    html = fetch(url.urljoin(base, source['routes']['creatorSearch'].replace('{query}', '')), 'onlyhaven')
    assets = re.findall(r'[\"\']([^\"\']+\.js)[\"\']', html)
    queue = [url.urljoin(base, path) for path in assets]
    seen = set()
    # Follow same-origin frontend module imports only, with finite request/body caps.
    while queue and len(seen) < 16:
        address = queue.pop(0)
        if address in seen or url.urlsplit(address).netloc != url.urlsplit(base).netloc:
            continue
        seen.add(address)
        try:
            body = fetch(address, 'onlyhaven')
        except Exception as error:
            print(json.dumps({'asset': address, 'error': str(error)}), flush=True)
            continue
        snippets = [body[max(0, m.start()-160):m.end()+220] for m in re.finditer(
            r'api/v1/creators|bookmarked|contentUpdated|orderBy|sortBy|content.{0,30}imported|gender:', body)]
        print(json.dumps({'asset': address, 'evidence': snippets[:35]}), flush=True)
        imports = re.findall(r'(?:from\s*|import\s*)[\"\']([^\"\']+\.js)[\"\']', body)
        imports += re.findall(r'[\"\'](\./[^\"\']+\.js)[\"\']', body)
        queue.extend(url.urljoin(address, path) for path in imports)
    for mode, token in source['categoryValues'].items():
        if mode == 'non_binary':
            continue
        pages = []
        for offset in (0, 16):
            address = route('onlyhaven', 'creatorCategoryApi', category=token, limit=16, offset=offset)
            found = rows(fetch(address, 'onlyhaven', ajax=True))
            require(len(found) <= 16, 'provider ignored page limit')
            identities = []
            for row in found:
                try:
                    identities.append(creator(row))
                except ValueError:
                    print(json.dumps({'mode': mode, 'offset': offset, 'unsupportedIdentity': {k: row.get(k) for k in ('service', 'id')}}), flush=True)
            for row in found:
                if row.get('gender'):
                    require(row['gender'] == token, 'different provider category returned')
            pages.append(set(identities))
            print(json.dumps({'mode': mode, 'offset': offset, 'count': len(found),
                              'bookmarks': [row.get('bookmarked') for row in found],
                              'keys': sorted({key for row in found for key in row}),
                              'sample': [{k: v for k, v in row.items() if k not in ('description', 'bio', 'avatarThumbhash')}
                                         for row in found[:2]]}), flush=True)
        require(not pages[0] or not pages[1] or bool(pages[1] - pages[0]), 'offset repeated first page')
    print('ONLYHAVEN_SHELF_CONTRACT_PASS', flush=True)


if __name__ == '__main__':
    main()
