const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const read = file => fs.readFileSync(path.join(root, file), 'utf8');
function array(xml, name) {
  const body = xml.match(new RegExp(`<string-array name="${name}"[^>]*>([\\s\\S]*?)</string-array>`));
  assert.ok(body, `Missing ${name}`);
  return [...body[1].matchAll(/<item>(.*?)<\/item>/g)].map(match => match[1]);
}
test('Mobile region dropdown matches desktop presets, ordering, colors and translations', async () => {
  const { regionLabel } = await import('../app/region-locales.mjs');
  const desktop = vm.runInNewContext(read('app/zlplay.js').match(/const REGION_PRESETS=(\[[\s\S]*?\n\]);/)[1]);
  const presets = Array.from(desktop.flatMap(group => group.items));
  const metadata = read('android/app/src/main/res/values/region-presets.xml');
  assert.deepEqual(array(metadata, 'editor_region_ids'), presets.map(item => item.name));
  assert.deepEqual(array(metadata, 'editor_region_colors'), presets.map(item => item.color));
  for (const [folder, language] of [['values','en'], ['values-pt-rBR','pt-BR'], ['values-es','es']]) {
    assert.deepEqual(array(read(`android/app/src/main/res/${folder}/strings.xml`), 'editor_region_names'), presets.map(item => regionLabel(item.name, language)));
  }
});
