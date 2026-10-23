const { test } = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const { assetPath } = require('../desktop/paths.cjs');
const root = path.resolve('app');
test('resolve página, recursos e parâmetros de versão', () => {
  assert.equal(assetPath(root, 'zlplay://app/'), path.join(root, 'index.html'));
  assert.equal(assetPath(root, 'zlplay://app/pads/C.wav?v=1'), path.join(root, 'pads/C.wav'));
});
test('bloqueia origem externa e caminhos fora do pacote', () => {
  for (const url of ['https://app/index.html', 'zlplay://other/index.html', 'zlplay://app/..%2fsecret', 'zlplay://app/%5c..%5csecret', 'zlplay://app/%00', 'zlplay://app/%ZZ']) assert.equal(assetPath(root, url), null, url);
});
