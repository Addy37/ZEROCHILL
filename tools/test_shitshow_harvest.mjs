import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

const path = new URL('../app/src/main/java/com/webapp/crazyshit/ShitShowTapSource.java', import.meta.url);
const source = fs.readFileSync(path, 'utf8');

test('historical ShitShow source keeps the expected production bridge and bounded harvester', () => {
  assert.match(source, /addJavascriptInterface\(new Bridge\(\), "CSShitBridge"\)/);
  assert.match(source, /private static final String HARVEST_STORIES_JS/);
  assert.match(source, /CSShitBridge\.onStory/);
  assert.match(source, /for\(var i=0;i<a\.length&&i<80;i\+\+\)/);
  assert.match(source, /setJavaScriptCanOpenWindowsAutomatically\(false\)/);
  assert.match(source, /setSupportMultipleWindows\(false\)/);
  assert.match(source, /blockExternalMainFrame/);
  assert.match(source, /removeJavascriptInterface\("CSShitBridge"\)/);
});
