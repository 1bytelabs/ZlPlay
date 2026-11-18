const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '..');
test('desktop and Android share the Click/Guide naming fixtures', () => {
  const source = fs.readFileSync(path.join(root, 'app/zlplay.js'), 'utf8');
  const methods = ['isClickTrack', 'isGuideTrack'].map(name => {
    const found = source.match(new RegExp(`^  ${name}\\(t\\)\\{.*\\}$`, 'm'));
    assert.ok(found, `Missing desktop classifier ${name}`);
    return found[0].trim();
  });
  const desktop = vm.runInNewContext(`({${methods.join(',')}})`);
  const rows = fs.readFileSync(path.join(root, 'tests/fixtures/track-role-names.tsv'), 'utf8').trimEnd().split(/\r?\n/);
  assert.ok(rows.length >= 30, 'Cover aliases, separators, accents and similar musical names');
  for (const row of rows) {
    const split = row.indexOf('\t');
    const expected = row.slice(0, split), name = row.slice(split + 1);
    const actual = `${Number(desktop.isClickTrack({name}))}${Number(desktop.isGuideTrack({name}))}`;
    assert.equal(actual, expected, name);
  }
});
