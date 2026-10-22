#!/usr/bin/env python3
"""Bounded, read-only source contracts. Fetch metadata, never video/image bodies.

Routes and headers come from the shipped configuration. Each failed contract is
reported separately; this tool never publishes config or disables a provider.
"""
import argparse
import html
from html.parser import HTMLParser
import json
import pathlib
import re
import sys
import urllib.parse as url
import urllib.request

from refresh_onlyfap_creator_index import FapelloCreatorParser, fapello_model_slug

ROOT = pathlib.Path(__file__).resolve().parents[1]
CONFIG = json.loads((ROOT / 'app/src/main/assets/source_config_defaults.json').read_text())['sources']
MAX_RESPONSE = 2 * 1024 * 1024
RESULTS = []
CACHE = {}


def require(condition, reason):
    if not condition:
        raise ValueError(reason)


def fetch(address, source='', ajax=False, head=False):
    key = (address, source, ajax, head)
    if key in CACHE:
        return CACHE[key]
    config = CONFIG.get(source, {})
    headers = {'User-Agent': config.get('userAgent', 'Mozilla/5.0'), **config.get('requestHeaders', {})}
    if ajax:
        headers.update(config.get('ajaxHeaders', {}))
    headers['Accept-Encoding'] = 'identity'
    headers['Referer'] = config.get('refererOverride') or config.get('baseUrl') or config.get('indexUrl') or address
    with urllib.request.urlopen(urllib.request.Request(address, headers=headers, method='HEAD' if head else 'GET'), timeout=12) as response:
        require(response.status == 200, f'HTTP {response.status}')
        if head:
            result = response.headers.get('Content-Type', '')
        else:
            payload = response.read(MAX_RESPONSE + 1)
            require(len(payload) <= MAX_RESPONSE, 'metadata exceeded 2 MB cap')
            result = payload.decode('utf8', errors='replace')
            lower = result.lower()
            require(not any(marker in lower for marker in ('site unavailable', 'cf-chl-', 'checking your browser', 'verify you are human', 'just a moment...')), 'upstream unavailable/challenge response')
    CACHE[key] = result
    return result


class Metadata(HTMLParser):
    def __init__(self, body, base):
        super().__init__(convert_charrefs=True)
        self.base, self.links, self.images, self.frames, self.media = base, [], [], [], []
        self.feed(body)

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        for attr, output in [('href', self.links if tag == 'a' else []),
                             ('src', self.images if tag == 'img' else self.frames if tag == 'iframe' else self.media if tag in ('video', 'source') else [])]:
            value = attrs.get(attr) or (attrs.get('data-src') if attr == 'src' else '')
            if value:
                output.append(url.urljoin(self.base, value))
        if tag == 'meta':
            key = attrs.get('property') or attrs.get('name', '')
            if key.startswith('og:video') or key == 'twitter:player:stream':
                self.media.append(url.urljoin(self.base, attrs.get('content', '')))
            elif key == 'og:image' and attrs.get('content'):
                self.images.append(url.urljoin(self.base, attrs['content']))


def route(source, key, **values):
    path = CONFIG[source]['routes'][key]
    for token, value in values.items():
        path = path.replace('{' + token + '}', url.quote(str(value), safe=''))
    require('{' not in path and '}' not in path, 'unfilled configured route token')
    return url.urljoin(CONFIG[source]['baseUrl'], path)


def https(value):
    parsed = url.urlsplit(str(value))
    return parsed.scheme == 'https' and bool(parsed.hostname) and not parsed.username and not parsed.password


def canonical_links(body, base, pattern):
    host = url.urlsplit(base).hostname.removeprefix('www.')
    return set(address.split('#')[0] for address in Metadata(body, base).links
               if url.urlsplit(address).hostname and url.urlsplit(address).hostname.removeprefix('www.') == host
               and re.fullmatch(pattern, url.urlsplit(address).path))


def pages(source, first, second, parse):
    left, right = parse(fetch(first, source), first), parse(fetch(second, source), second)
    require(bool(left), 'first page has no canonical results')
    require(bool(right), 'second page has no canonical results')
    require(bool(set(right) - set(left)), 'pagination repeated first-page results')
    return left


def artwork(body, base, source):
    images = [address for address in Metadata(body, base).images if https(address) and not address.endswith('.svg')]
    require(bool(images), 'usable artwork URL missing')
    require(fetch(images[0], source, head=True).lower().startswith('image/'), 'artwork HEAD did not return an image')


def detail(body, base):
    media = Metadata(body, base).media
    media.extend(html.unescape(match).replace('\\/', '/') for match in re.findall(r'https?://[^\s\"\'<>]+?\.(?:mp4|m3u8|mpd|webm|m4v)(?:\?[^\s\"\'<>]*)?', body.replace('\\/', '/'), re.I))
    require(any(https(address) for address in media), 'direct playable metadata missing')


def rows(body):
    data = json.loads(body)
    if isinstance(data, list):
        return data
    if isinstance(data, dict):
        for key in ('creators', 'items', 'results', 'data', 'posts'):
            if isinstance(data.get(key), list):
                return data[key]
    raise ValueError('creator JSON list envelope missing')


def creator(row):
    require(isinstance(row, dict), 'creator row is not an object')
    service, identifier = str(row.get('service', '')), str(row.get('id') or row.get('creatorId') or row.get('creator_id') or row.get('user') or '')
    require(bool(re.fullmatch(r'[a-zA-Z0-9_-]+', service)) and bool(re.fullmatch(r'[a-zA-Z0-9_-]+', identifier)), 'invalid creator service/id')
    address = CONFIG['onlyhaven']['baseUrl'] + f'creators/{service}/{identifier}'
    require(https(address), 'canonical creator profile URL invalid')
    for key in ('avatarUrl', 'avatar_url', 'imageUrl', 'image_url'):
        if row.get(key):
            require(https(row[key]), f'unusable {key}')
    avatar = row.get('avatar')
    if isinstance(avatar, str) and avatar.startswith('http'):
        require(https(avatar), 'unusable avatar URL')
    return address


def run(source, contract, callback):
    try:
        callback()
        result = {'source': source, 'contract': contract, 'status': 'PASS'}
    except Exception as error:
        result = {'source': source, 'contract': contract, 'status': 'FAIL', 'reason': f'{type(error).__name__}: {error}'}
    RESULTS.append(result)
    print(json.dumps(result), flush=True)


def onlyhaven():
    name, source = 'onlyhaven', CONFIG['onlyhaven']
    matches = {}
    for query in ('nolantwink', 'Nolan'):
        def search(query=query):
            found = rows(fetch(route(name, 'creatorSearchApi', query=query, limit=20, offset=0), name, ajax=True))
            require(bool(found), f'{query}: creator search returned no creators')
            wanted = re.sub('[^a-z0-9]', '', query.lower())
            confirmed = [row for row in found if any(wanted in re.sub('[^a-z0-9]', '', str(row.get(key, '')).lower())
                         for key in ('name', 'displayName', 'display_name', 'username', 'id'))]
            require(bool(confirmed), f'{query}: API did not return a matching creator')
            for row in confirmed:
                creator(row)
            matches[query] = confirmed
        run(name, f'creator-search/{query}/canonical-metadata', search)
    def html_search():
        address = route(name, 'creatorSearch', query='Nolan')
        body = fetch(address, name)
        links = canonical_links(body, address, r'/creators/[a-zA-Z0-9_-]+/[a-zA-Z0-9_-]+/?')
        # An app shell may contain no server-rendered creators. It must expose
        # actual JS assets and the independently tested API, not arbitrary text.
        require(bool(links) or bool(re.search(r'<script[^>]+src=[\"\'][^\"\']+\.js', body)), 'HTML search has neither creator links nor frontend JS contract')
    run(name, 'html-cq-search', html_search)
    for category, token in source['categoryValues'].items():
        if category == 'non_binary':
            continue
        def category_list(token=token):
            found = rows(fetch(route(name, 'creatorCategoryApi', category=token, limit=5, offset=0), name, ajax=True))
            require(bool(found), 'category has no creator metadata; needs review, never disables source')
            for row in found:
                creator(row)
                if row.get('gender'):
                    require(row['gender'] == token, 'provider returned a different gender')
        run(name, f'category/{category}', category_list)
    def male_search():
        found = rows(fetch(route(name, 'creatorCategorySearchApi', query='nolantwink', category=source['categoryValues']['men'], limit=20, offset=0), name, ajax=True))
        require(bool(found), 'nolantwink Men search returned no matching creators')
        broad = {creator(row) for row in matches.get('nolantwink', [])}
        identities = {creator(row) for row in found}
        require(bool(broad & identities), 'Men search did not return the canonical broad-search creator')
        for row in found:
            if row.get('gender'):
                require(row['gender'] == source['categoryValues']['men'], 'Men search returned non-male metadata')
    run(name, 'nolantwink/men-membership', male_search)
    def profile_posts():
        require(bool(matches), 'creator search unavailable; cannot inspect profile')
        row = next(iter(matches.values()))[0]
        address = creator(row)
        body = fetch(address, name)
        require(bool(Metadata(body, address).images) or bool(re.search(r'<script[^>]+src=', body)), 'profile metadata/frontend missing')
        found = rows(fetch(route(name, 'creatorPostsApi', service=row['service'], id=row['id'], page=1, limit=2, offset=0), name, ajax=True))
        require(bool(found), 'profile posts API returned no posts')
        media = json.dumps(found)
        require(bool(re.search(r'"(?:path|url|name|file|attachments|files|media)"', media)), 'posts lack media metadata')
        image = source['imageBaseUrl'] + f"creator/{row['service']}/{row['id']}/avatar.webp"
        require(fetch(image, name, head=True).lower().startswith('image/'), 'canonical avatar HEAD is not an image')
    run(name, 'profile/posts/avatar', profile_posts)


def fapello():
    source, base = 'fapello', CONFIG['fapello']['baseUrl']
    def parse(body, address):
        parser = FapelloCreatorParser(base)
        parser.feed(body)
        return {url.urljoin(base, slug + '/') for _, slug in parser.creators}
    discovered = set()
    def discover():
        discovered.update(pages(source, route(source, 'listingNewFirst', page=1), route(source, 'listingNewPage', page=2), parse))
    run(source, 'discover/pagination', discover)
    def search():
        addresses = []
        for offset in (0, 5):
            body = fetch(route(source, 'search', query='anna', limit=5, offset=offset), source, ajax=True)
            candidates = set()
            try:
                data = json.loads(body)
                def collect(value):
                    if isinstance(value, list):
                        for entry in value: collect(entry)
                    elif isinstance(value, dict):
                        address = next((value.get(key) for key in ('url', 'profile_url', 'profileUrl', 'href') if value.get(key)), '')
                        slug = fapello_model_slug(address, base) if address else ''
                        if slug and any(value.get(key) for key in ('name', 'title', 'username', 'model')):
                            candidates.add(url.urljoin(base, slug + '/'))
                        for key in ('results', 'models', 'items', 'data', 'suggestions'): collect(value.get(key))
                    elif isinstance(value, str) and '<' in value: candidates.update(parse(value, base))
                collect(data)
            except json.JSONDecodeError:
                candidates = parse(body, base)
            require(bool(candidates), f'search offset {offset}: no canonical creators')
            addresses.append(candidates)
        require(bool(addresses[1] - addresses[0]), 'search pagination repeated first batch')
        discovered.update(addresses[0])
    run(source, 'search/pagination', search)
    def profile():
        require(bool(discovered), 'no confirmed creator available')
        address = sorted(discovered)[0]
        body = fetch(address, source)
        artwork(body, address, source)
        slug = fapello_model_slug(address, base)
        gallery = fetch(route(source, 'creatorMedia', slug=slug, page=1), source, ajax=True)
        require(bool(re.search(r'/(?:video/(?:[^/]+/)?\d+|[^/]+/\d+)', gallery)) or bool(Metadata(gallery, address).media), 'creator gallery has no post/media links')
    run(source, 'creator/profile/artwork/gallery', profile)


def bunkr():
    source, base = 'bunkr', CONFIG['bunkr']['indexUrl']
    albums = set()
    parse = lambda body, address: {link for link in Metadata(body, address).links if re.search(r'/a/[a-zA-Z0-9_-]+', url.urlsplit(link).path)}
    def listing():
        albums.update(pages(source, base, base + '?page=2', parse))
    run(source, 'balbums/discover/pagination', listing)
    def album():
        require(bool(albums), 'no canonical album to inspect')
        address = sorted(albums)[0]
        body = fetch(address, source)
        require(bool(Metadata(body, address).images), 'album thumbnails missing')
        require(bool(re.search(r'/(?:v|i|f)/[a-zA-Z0-9_-]+', body)), 'album media-detail links missing')
    run(source, 'album/thumbnails/media-links', album)


def wiki(source):
    base = CONFIG[source]['baseUrl']
    profiles = set()
    def search():
        address = url.urljoin(base, CONFIG[source]['searchRoute'].replace('{query}', 'Anna'))
        body = fetch(address, source)
        profiles.update(address for address in canonical_links(body, base, r'/(?!search|photos|videos)[^/?#]+/?')
                        if 'anna' in url.unquote(url.urlsplit(address).path).lower())
        # WikiFeet also returns its search table in a JS array.
        for candidate in re.findall(r'"(?:url|href|path|slug|fetchname)"\s*:\s*"([^\"]+)"', body):
            canonical = url.urljoin(base, candidate if candidate.startswith('/') else '/' + candidate)
            if re.fullmatch(r'/[^/?#]+', url.urlsplit(canonical).path) and 'anna' in url.unquote(url.urlsplit(canonical).path).lower(): profiles.add(canonical)
        require(bool(profiles), 'search returned no canonical profile metadata')
    run(source, 'search/profile-lookup', search)
    def profile():
        require(bool(profiles), 'search unavailable; no profile to inspect')
        for address in sorted(profiles)[:3]:
            body = fetch(address, source)
            if re.search(r'\bcid\b|["\']cid["\']', body) and 'gallery' in body and 'cname' in body:
                picture = re.search(r'["\']?pid["\']?\s*:\s*([1-9]\d*)', body)
                require(bool(picture), 'profile gallery picture ids missing')
                # WikiFeet renders gallery <img> nodes in JavaScript. The app
                # derives the thumbnail URL from the provider's gallery pid.
                thumbnail = 'https://' + CONFIG[source]['thumbnailHost'] + '/' + picture.group(1) + '.jpg'
                require(fetch(thumbnail, source, head=True).lower().startswith('image/'), 'gallery thumbnail HEAD did not return an image')
                return
        raise ValueError('searched profiles lack cid/cname/gallery metadata')
    run(source, 'profile/gallery/artwork', profile)


def video_source(source):
    base = CONFIG.get(source, {}).get('baseUrl') or f'https://{source}.com/'
    if source == 'kaotic':
        first, second = route(source, 'feedFirst'), route(source, 'feedPage', page=2)
        pattern = r'/video/[^/?#]+'
    elif source == 'crazyshit':
        first, second, pattern = base, base + '2/', r'/(?:cnt/medias/\d+[^?#]*|video/[^/?#]+)'
    else:
        # EFukt's native repository exposes one latest page; use the site's
        # advertised pagination link if present, rather than inventing a route.
        first, second, pattern = base, None, r'/(?:[^/?#]+/)*\d+_[^/?#]+\.html'
    found = set()
    def listing():
        body = fetch(first, source)
        found.update(canonical_links(body, base, pattern))
        require(bool(found), 'listing lacks canonical video links')
        if second:
            pages(source, first, second, lambda text, address: canonical_links(text, base, pattern))
        else:
            links = Metadata(body, first).links
            next_page = next((address for address in links if url.urlsplit(address).hostname == url.urlsplit(base).hostname and re.search(r'(?:[?&]page=2|/page/2|/2/)', address)), None)
            if next_page:
                pages(source, first, next_page, lambda text, address: canonical_links(text, base, pattern))
    run(source, 'listing/pagination', listing)
    def playable():
        require(bool(found), 'no listing video to inspect')
        address = sorted(found)[0]
        body = fetch(address, source)
        detail(body, address)
        artwork(body, address, source)
    run(source, 'detail/playable-metadata/artwork', playable)


def baddiehub():
    source, base = 'baddiehub', CONFIG['baddiehub']['baseUrl']
    excluded = {'categories', 'category', 'tags', 'tag', 'login', 'register', 'privacy-policy', 'dmca', 'contact'}
    def parse(body, address):
        return {link for link in canonical_links(body, base, r'/[^/?#]+/') if url.urlsplit(link).path.strip('/') not in excluded and not url.urlsplit(link).path.strip('/').startswith('wp-')}
    found, categories = set(), set()
    def listing():
        found.update(pages(source, route(source, 'listingFirst'), route(source, 'listingPage', page=2), parse))
    run(source, 'listing/pagination', listing)
    def directory():
        categories.update(pages(source, route(source, 'categoriesFirst'), route(source, 'categoriesPage', page=2), lambda body, address: canonical_links(body, base, r'/category/[^/?#]+/')))
    run(source, 'categories/pagination', directory)
    def category():
        require(bool(categories), 'no category to inspect')
        slug = url.urlsplit(sorted(categories)[0]).path.strip('/').split('/')[-1]
        pages(source, route(source, 'categoryFirst', slug=slug, page=1), route(source, 'categoryPage', slug=slug, page=2), parse)
    run(source, 'category/listing/pagination', category)
    def playable():
        require(bool(found), 'no listing video to inspect')
        address = sorted(found)[0]
        body = fetch(address, source)
        frames = [frame for frame in Metadata(body, address).frames if 'player-x.php' in frame and 'q=' in frame]
        if frames:
            frame = frames[0]
            detail(fetch(frame, source), frame)
        else:
            detail(body, address)
        artwork(body, address, source)
    run(source, 'detail/player/playable-metadata/artwork', playable)


def main():
    checks = {'onlyhaven': onlyhaven, 'fapello': fapello, 'bunkr': bunkr,
              'wikifeet': lambda: wiki('wikifeet'), 'wikifeetx': lambda: wiki('wikifeetx'),
              'kaotic': lambda: video_source('kaotic'), 'baddiehub': baddiehub,
              'crazyshit': lambda: video_source('crazyshit'), 'efukt': lambda: video_source('efukt')}
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', choices=list(checks))
    parser.add_argument('--onlyhaven', action='store_true')
    parser.add_argument('--output', type=pathlib.Path)
    args = parser.parse_args()
    selected = [args.source or 'onlyhaven'] if args.source or args.onlyhaven else list(checks)
    for source in selected: checks[source]()
    if args.output: args.output.write_text(json.dumps(RESULTS, indent=2) + '\n')
    failures = [result for result in RESULTS if result['status'] != 'PASS']
    print(f'{len(RESULTS) - len(failures)}/{len(RESULTS)} contracts passed; no sources modified.')
    return bool(failures)


if __name__ == '__main__':
    sys.exit(main())
