import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';

// Diagnostic-branch compatibility test for the pre-Smart-ShitTok-v2 ShitShow harvester.
// This file is test-only and does not ship in the APK.
const java = fs.readFileSync(new URL('../app/src/main/java/com/webapp/crazyshit/ShitShowTapSource.java', import.meta.url), 'utf8');
const script = java.split('private static final String HARVEST_STORIES_JS =')[1]
  .split('\n').map(line => line.match(/^\s*("(?:[^"\\]|\\.)*")\s*[+;]?\s*$/))
  .filter(Boolean).map(match => JSON.parse(match[1])).join('');
assert.ok(script.includes('CSShitBridge.onStory'));

function harvest(setup = '') {
  const emitted = [], errors = [];
  const context = vm.createContext({
    URL,
    window: {},
    location: { href: 'https://crazyshit.com/shitshow/' },
    document: { scripts: [] },
    CSShitBridge: {
      onStory: value => emitted.push(JSON.parse(value)),
      onProbeError: value => errors.push(value),
    },
  });
  vm.runInContext(setup, context);
  vm.runInContext(script, context, { timeout: 1000 });
  assert.deepEqual(errors, []);
  return emitted;
}

test('pre-v2 harvester reads an existing window story array', () => {
  const story = {
    id: 17,
    permalink: '/shitshow/fixture',
    title: 'Fixture',
    width: 720,
    height: 1280,
    video: 'https://fixture.invalid/video.mp4'
  };
  const emitted = harvest(`window.stories = ${JSON.stringify([story])};`);
  assert.equal(emitted.length, 1);
  assert.equal(emitted[0].page, 'https://crazyshit.com/shitshow/fixture');
  assert.equal(emitted[0].media, story.video);
  assert.equal(emitted[0].width, 720);
  assert.equal(emitted[0].height, 1280);
});

test('pre-v2 harvester ignores an empty story array without errors', () => {
  const emitted = harvest('window.stories = [];');
  assert.equal(emitted.length, 0);
});
