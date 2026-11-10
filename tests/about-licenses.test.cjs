const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {licensePanel} = require('../desktop/license-panel.cjs');
const {allowedProjectLink} = require('../desktop/project-links.cjs');

test('macOS About exposes local original license texts and translated explanations', () => {
  for (const [language, title] of [['pt-BR','Licenças e código-fonte'], ['en','Licenses and source code'], ['es','Licencias y código fuente']]) {
    const html = licensePanel({language, platform:'darwin'});
    assert.ok(html.includes(title));
    assert.match(html, /CoreAudio/);
    assert.match(html, /node-qrcode/);
    assert.match(html, /Supertonic/);
    assert.match(html, /GNU AFFERO GENERAL PUBLIC LICENSE/);
    assert.doesNotMatch(html, /Steinberg Media Technologies|<script/);
  }
  assert.equal(licensePanel({platform:'win32'}), '');
});

test('packaged About uses shipped provenance and escapes legal text', () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(),'zlplay-legal-'));
  try {
    fs.mkdirSync(path.join(root,'licenses'));
    const sha = 'a'.repeat(40);
    fs.writeFileSync(path.join(root,'licenses/SOURCE_CODE.txt'), 'Published equivalent source snapshot: '+sha+'\n<script>fixture</script>');
    const html = licensePanel({platform:'darwin', packaged:true, resources:root});
    assert.ok(html.includes('/tree/'+sha));
    assert.ok(html.includes('&lt;script&gt;fixture&lt;/script&gt;'));
    assert.ok(html.includes('indisponível'));
  } finally { fs.rmSync(root,{recursive:true}); }
});

test('About source links cannot open arbitrary websites, local files or credentials', () => {
  for (const url of ['https://github.com/1bytelabs/ZlPlay', 'https://github.com/1bytelabs/ZlPlay/tree/'+'a'.repeat(40), 'https://livepix.gg/1bytelabs', 'https://1bytelabs.com.br/']) assert.ok(allowedProjectLink(url));
  const credentials = new URL('https://github.com/1bytelabs/ZlPlay');credentials.username='fixture';credentials.password='fixture';assert.equal(allowedProjectLink(credentials.href),false);
  for (const url of ['file:///C:/test', 'javascript:alert(1)', 'https://github.com.evil.test/1bytelabs/ZlPlay', 'https://github.com/another/repo', 'https://github.com/1bytelabs/ZlPlay?x=1']) assert.equal(allowedProjectLink(url),false);
});
