const { test } = require('node:test');
const assert = require('node:assert/strict');
test('menor caminho nas 144 combinações de notas', async () => {
  const { shortestSemitoneDelta } = await import('../app/tonal-distance.mjs');
  for (let from = 0; from < 12; from++) for (let to = 0; to < 12; to++) {
    const delta = shortestSemitoneDelta(from, to);
    assert.ok(Math.abs(delta) <= 6);
    assert.equal(((from + delta) % 12 + 12) % 12, to);
  }
  assert.equal(shortestSemitoneDelta(9, 0), 3);
  assert.equal(shortestSemitoneDelta(0, 9), -3);
  assert.equal(shortestSemitoneDelta(11, 0), 1);
});
