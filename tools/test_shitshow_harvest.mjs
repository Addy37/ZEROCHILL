import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';

// Execute the exact production WebView script, rather than faking an onStory call.
const java = fs.readFileSync(new URL('../app/src/main/java/com/webapp/crazyshit/ShitShowTapSource.java', import.meta.url), 'utf8');
const script = java.split('private static final String HARVEST_STORIES_JS =')[1]
  .split('\n').map(line => line.match(/^\s*("(?:[^"\\]|\\.)*")\s*[+;]?\s*$/))
  .filter(Boolean).map(match => JSON.parse(match[1])).join('');
assert.ok(script.includes('CSShitBridge.onStory'));

const story = { id: 17, permalink: '/shitshow/fixture', title: 'Fixture [with brackets]', width: 720, height: 1280 };
function harvest({ setup = '', scripts = [] } = {}) {
  const emitted = [], errors = [], states = [];
  const context = vm.createContext({
    URL, window: {}, location: { href: 'https://crazyshit.com/shitshow/' },
    document: { scripts },
    CSShitBridge: {
      onStory: value => emitted.push(JSON.parse(value)),
      onProbeError: value => errors.push(value),
      onProbeState: value => states.push(JSON.parse(value)),
    },
  });
  vm.runInContext(setup, context);
  vm.runInContext(script, context, { timeout: 1000 });
  assert.deepEqual(errors, []);
  return { emitted, state: states.at(-1) };
}

test('global lexical story data is acquired despite not being a window property', () => {
  const result = harvest({ setup: `const _stories = ${JSON.stringify([story])};` });
  assert.equal(result.emitted.length, 1);
  assert.equal(result.emitted[0].page, 'https://crazyshit.com/shitshow/fixture');
  assert.equal(result.emitted[0].width / result.emitted[0].height, 720 / 1280);
});

test('embedded closure data is parsed as JSON without executing its enclosing script', () => {
  const textContent = `(function(){ const _stories = ${JSON.stringify([story])}; throw Error('must not execute'); })();`;
  const result = harvest({ scripts: [{ textContent }] });
  assert.equal(result.emitted.length, 1);
  assert.equal(result.state.inline, 1);
});

test('existing window arrays and direct media continue to work', () => {
  const direct = { ...story, video: 'https://fixture.invalid/video.mp4' };
  const result = harvest({ setup: `window.stories = ${JSON.stringify([direct])};` });
  assert.equal(result.emitted.length, 1);
  assert.equal(result.emitted[0].media, direct.video);
});

test('malformed embedded data is observable and never evaluated as code', () => {
  const result = harvest({ scripts: [{ textContent: 'const _stories = [runArbitraryCode()];' }] });
  assert.equal(result.emitted.length, 0);
  assert.equal(result.state.parseErrors, 1);
});

test('script scan remains bounded and ignores external script URLs', () => {
  const textContent = `const _stories = ${JSON.stringify([story])};`;
  const result = harvest({ scripts: [{ src: 'https://fixture.invalid/script.js', textContent },
    { textContent: ' '.repeat(524288) + textContent }] });
  assert.equal(result.emitted.length, 0);
});
