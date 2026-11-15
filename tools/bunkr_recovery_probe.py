#!/usr/bin/env python3
"""Read-only Bunkr recovery evidence. No challenge solving, retries or media downloads.

Uses bundled routes/headers; records redacted redirects/status and at most 32 media bytes.
A denial stops that request chain. This is HTTP evidence, not an Android playback test.
"""
import datetime
import html
import json
import pathlib
import re
import urllib.error
import urllib.parse as url
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
CONFIG = json.loads((ROOT / 'app/src/main/assets/source_config_defaults.json').read_text())['sources']['bunkr']
REPORT = {'timestamp': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'requests': []}
CAP = 2 * 1024 * 1024


def redacted(address):
    parsed = url.urlsplit(address)
    return url.urlunsplit((parsed.scheme, parsed.netloc, parsed.path,
                          url.urlencode([(key, '<redacted>' if key in ('token', 'ex') else value)
                                         for key, value in url.parse_qsl(parsed.query)]), ''))


class Redirects(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, response, code, message, headers, target):
        REPORT['requests'].append({'redirect': code, 'from': redacted(request.full_url), 'to': redacted(target)})
        if url.urlsplit(target).scheme != 'https':
            raise ValueError('Non-HTTPS redirect refused')
        return super().redirect_request(request, response, code, message, headers, target)


def fetch(address, referer, extra=None, limit=CAP):
    headers = {'User-Agent': CONFIG['userAgent'], **CONFIG['requestHeaders'],
               'Referer': CONFIG.get('refererOverride') or referer, **(extra or {})}
    record = {'url': redacted(address)}
    REPORT['requests'].append(record)
    try:
        try:
            response = urllib.request.build_opener(Redirects()).open(
                urllib.request.Request(address, headers=headers), timeout=12)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            record.update(status=response.status, final_url=redacted(response.url),
                          headers={key: response.headers[key] for key in (
                              'Content-Type', 'Server', 'Retry-After', 'Content-Length',
                              'Content-Range', 'Accept-Ranges') if key in response.headers})
            # If Range is ignored, don't read an entire video response.
            if limit == 32 and response.status != 206:
                record['media_range_pass'] = False
                return None
            body = response.read(limit if limit == 32 else limit + 1)
        if len(body) > limit:
            record['error'] = 'Metadata size cap exceeded'
            return None
        if limit != 32 and 'html' in response.headers.get('Content-Type', '').lower():
            text = body.decode('utf8', 'replace')
            title = re.search(r'<title[^>]*>(.*?)</title>', text, re.I | re.S)
            record['html_title'] = html.unescape(title[1]).strip()[:256] if title else ''
            record['script_sources'] = [redacted(url.urljoin(response.url, html.unescape(source)))
                                        for source in re.findall(r'''<script[^>]*src=["']([^"']+)''', text, re.I)[:12]]
        if response.status not in (200, 206):
            record['verification_detected'] = bool(re.search(
                b'checkpoint sentinel|verify you are human|checking your browser|access denied', body, re.I))
            return None
        if limit == 32:
            record['media_range_pass'] = len(body) == 32 and body[4:8] == b'ftyp'
        else:
            record['verification_detected'] = bool(re.search(
                b'checkpoint sentinel|verify you are human|checking your browser|access denied', body, re.I))
            if record['verification_detected']:
                return None
        return body
    except Exception as error:
        # Exception strings can contain signed request URLs: record type only.
        record['error'] = type(error).__name__
        return None


def main():
    index = CONFIG['indexUrl']
    search = index + '?' + url.urlencode({'search': 'sample', 'mode': 'broad', 'page': 1})
    for address in (index, search):
        record_index = len(REPORT['requests'])
        body = fetch(address, index)
        if body:
            links = re.findall(r'''href=["']([^"']*/a/[^"']+)["']''', body.decode('utf8', 'replace'), re.I)
            record = REPORT['requests'][record_index]
            record['album_link_count'] = len(links)
            record['album_hosts'] = sorted({url.urlsplit(url.urljoin(record['final_url'], html.unescape(link))).hostname for link in links})
    # Independent, previously recorded file/album: search denial does not imply page denial.
    page = CONFIG['pageOrigins'][0] + '/f/Fuyd9dOX3ZbhP'
    record_index = len(REPORT['requests'])
    body = fetch(page, CONFIG['pageOrigins'][0] + '/')
    if body:
        page = REPORT['requests'][record_index]['final_url']
        page_origin = url.urlunsplit((*url.urlsplit(page)[:2], '', '', ''))
        text = body.decode('utf8', 'replace')
        cdn = re.search(r'''\bjsCDN\s*=\s*["']([^"']+)''', text)
        signer = re.search(r'''\bsignUrl\s*=\s*["']([^"']+)''', text)
        REPORT['file_metadata'] = {'has_jsCDN': bool(cdn), 'has_signUrl': bool(signer),
                                   'has_legacy_id': bool(re.search('data-file-id', text))}
        if cdn and signer:
            media = html.unescape(cdn[1]).replace('\\/', '/')
            sign = signer[1].replace('\\/', '/')
            media_host = url.urlsplit(media).hostname or ''
            REPORT['file_metadata'].update(cdn_host=media_host, signer=redacted(sign))
            if sign == CONFIG['signUrl'] and any(media_host == host or media_host.endswith('.' + host) for host in CONFIG['cdnHosts']):
                signed = fetch(sign + '?' + url.urlencode({'path': url.unquote(url.urlsplit(media).path)}), page,
                               {'Origin': page_origin, 'Accept': 'application/json'})
                if signed:
                    payload = json.loads(signed)
                    REPORT['signing'] = {'token_present': bool(payload.get('token')), 'expiry_present': bool(payload.get('ex'))}
                    if payload.get('token') and payload.get('ex'):
                        parts = url.urlsplit(media)
                        params = [(key, value) for key, value in url.parse_qsl(parts.query) if key not in ('token', 'ex')]
                        address = url.urlunsplit(parts._replace(query=url.urlencode(params + [('token', payload['token']), ('ex', payload['ex'])])))
                        fetch(address, page, {'Origin': page_origin, 'Range': 'bytes=0-31'}, 32)
    album = CONFIG['pageOrigins'][0] + '/a/WlA5Bbyp?page=1'
    body = fetch(album, CONFIG['pageOrigins'][0] + '/')
    if body:
        REPORT['album_metadata'] = {'has_albumFiles': b'window.albumFiles' in body,
                                    'file_link_count': len(re.findall(rb'href=["\'][^"\']*/[fvid]/', body))}


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        REPORT['probe_error'] = type(error).__name__
    print(json.dumps(REPORT, indent=2))
