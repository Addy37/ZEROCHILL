"""Offline regressions for the active Bunkr audit path; never contact providers."""
import unittest
from unittest.mock import patch
import audit_source_contracts as audit


class ActiveBunkrAuditTest(unittest.TestCase):
    def setUp(self):
        audit.RESULTS.clear()
        audit.CACHE.clear()
        audit.HTTP_EVIDENCE.clear()

    def fetch(self, address, source='', **kwargs):
        if address.startswith('https://bunkrsearch.link/api/search'):
            page = int(audit.url.parse_qs(audit.url.urlsplit(address).query)['page'][0])
            return audit.json.dumps({'page': page, 'results': [
                {'album_slug': f'album_{page}', 'file_url': f'https://bunkr.cr/f/file_{page}'}]})
        if 'balbums' in address:
            raise audit.urllib.error.HTTPError(address, 403, 'checkpoint', {}, None)
        if '/a/' in address:
            return '<img src="https://static.scdn.st/thumb.png"><a href="https://bunkr.cr/v/file_1">file</a>'
        return '<video src="https://cdn.example/video.mp4"></video>'

    def test_active_index_is_checked_and_legacy_failures_remain_visible(self):
        with patch.object(audit, 'fetch', side_effect=self.fetch):
            audit.bunkr()
        results = {r['contract']: r['status'] for r in audit.RESULTS}
        self.assertEqual('PASS', results['bunkr-index/active-search/pages-1-2'])
        self.assertEqual('FAIL', results['balbums/discover/pagination'])
        self.assertEqual('FAIL', results['balbums/search'])
        self.assertEqual('PASS', results['album/thumbnails/media-links'])
        self.assertEqual('PASS', results['media-detail/signed-or-direct-resolution'])

    def test_index_malformed_response_is_failure(self):
        with patch.object(audit, 'fetch', return_value='{"error":"unavailable"}'):
            audit.bunkr()
        self.assertEqual('FAIL', audit.RESULTS[0]['status'])

    def test_index_repeated_file_page_is_failure(self):
        with patch.object(audit, 'fetch', side_effect=lambda address, *args, **kw:
                '{"page":' + str(2 if 'page=2' in address else 1)
                + ',"results":[{"album_slug":"album_1"}]}'):
            audit.bunkr()
        self.assertEqual('FAIL', audit.RESULTS[0]['status'])
        self.assertIn('repeated first-page', audit.RESULTS[0]['reason'])


if __name__ == '__main__':
    unittest.main()
