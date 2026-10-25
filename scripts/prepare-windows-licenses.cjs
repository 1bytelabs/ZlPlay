const fs = require('node:fs');
const path = require('node:path');
const {execFileSync} = require('node:child_process');

function prepareWindowsLicenses(root, revision) {
  if (!/^[a-f0-9]{40}$/.test(revision)) throw Error('A full source commit is required');
  const out = path.join(root, '.cache', 'windows-licenses');
  fs.mkdirSync(out, {recursive: true});
  for (const [source, name] of [
    ['LICENSE', 'ZLPlay-AGPL-3.0.txt'],
    ['THIRD_PARTY_NOTICES.md', 'THIRD_PARTY_NOTICES.md'],
    ['docs/licenses/GPL-3.0.txt', 'GPL-3.0.txt'],
    ['docs/licenses/ASIO-LICENSE.txt', 'ASIO-LICENSE.txt'],
    ['docs/LICENSING.md', 'LICENSING.md']
  ]) fs.copyFileSync(path.join(root, source), path.join(out, name));
  const base = 'https://github.com/1bytelabs/ZlPlay';
  fs.writeFileSync(path.join(out, 'SOURCE_CODE.txt'), [
    'ZLPlay source / Codigo-fonte / Codigo fuente',
    'Commit: ' + revision,
    base + '/tree/' + revision,
    base + '/archive/' + revision + '.zip',
    'Build instructions: ' + base + '/blob/' + revision + '/README.md',
    'Dependency sources and licensing: ' + base + '/blob/' + revision + '/docs/LICENSING.md',
    'JUCE (including ASIO headers): https://github.com/juce-framework/JUCE/tree/3fb5cbc0340758596dac39f7c8de47d562b4896e',
    'JUCE archive: https://codeload.github.com/juce-framework/JUCE/tar.gz/3fb5cbc0340758596dac39f7c8de47d562b4896e',
    'whisper.cpp: https://github.com/ggml-org/whisper.cpp/tree/9386f239401074690479731c1e41683fbbeac557',
    'Electron 44.9.2: https://github.com/electron/electron/tree/v44.9.2',
    'The release tag may precede this build. Use the commit above, not the automatic tag source archives.',
    'A tag da release pode ser anterior ao build. Use o commit acima.',
    'La etiqueta de la release puede ser anterior al build. Use el commit indicado.',
    ''
  ].join('\n'));
  return out;
}
module.exports = async function beforePack(context) {
  if (context.electronPlatformName !== 'win32') return;
  const root = context.packager.projectDir;
  const revision = execFileSync('git', ['rev-parse', 'HEAD'], {cwd: root, encoding: 'utf8'}).trim();
  prepareWindowsLicenses(root, revision);
};
module.exports.prepareWindowsLicenses = prepareWindowsLicenses;
