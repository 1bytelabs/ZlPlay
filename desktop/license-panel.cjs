const fs = require('node:fs');
const path = require('node:path');
const escape = value => String(value).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));

function licensePanel({language = 'pt-BR', platform = process.platform, resources = process.resourcesPath, root = path.join(__dirname, '..'), packaged = false} = {}) {
  if (platform !== 'darwin') return '';
  const tr = (pt, en, es) => language === 'pt-BR' ? pt : language === 'es' ? es : en;
  const legal = packaged ? path.join(resources, 'licenses') : root;
  const native = packaged ? path.join(resources, 'native') : path.join(root, 'native', 'bin');
  const entries = [
    ['ZLPlay · AGPLv3', packaged ? path.join(legal, 'ZLPlay-AGPL-3.0.txt') : path.join(root, 'LICENSE')],
    [tr('Avisos de terceiros', 'Third-party notices', 'Avisos de terceros'), path.join(legal, 'THIRD_PARTY_NOTICES.md')],
    ['node-qrcode · MIT', packaged ? path.join(legal, 'QRCode-MIT.txt') : path.join(root, 'docs/licenses/QRCode-MIT.txt')],
    [tr('Guias de voz integradas', 'Built-in voice guides', 'Guías de voz integradas'), path.join(root, 'app/guide-banks/NOTICE.md')]
  ];
  if (packaged) {
    const speech = path.join(resources, 'speech');
    entries.push(
      ['whisper.cpp · MIT', path.join(speech, 'WHISPER-LICENSE.txt')],
      [tr('Modelo de reconhecimento', 'Speech recognition model', 'Modelo de reconocimiento'), path.join(speech, 'MODEL-LICENSE.txt')],
      [tr('Fontes deste build', 'Sources for this build', 'Fuentes de esta compilación'), path.join(legal, 'SOURCE_CODE.txt')]
    );
  }
  for (const [label, name] of [['JUCE', 'JUCE-LICENSE.md'], ['Signalsmith Stretch', 'Signalsmith-Stretch-LICENSE.txt'], ['Signalsmith Linear', 'Signalsmith-Linear-LICENSE.txt'], ['libebur128', 'libebur128-LICENSE.txt']]) entries.push([label, path.join(native, name)]);
  const read = file => { try { return fs.readFileSync(file, 'utf8'); } catch { return tr('Texto indisponível nesta instalação.', 'Text unavailable in this installation.', 'Texto no disponible en esta instalación.'); } };
  if (packaged) entries.push(['Electron · MIT', path.join(legal, 'LICENSE.electron.txt')]);
  const provenance = packaged ? read(path.join(legal, 'SOURCE_CODE.txt')) : '';
  const revision = provenance.match(/^Published equivalent source snapshot: ([a-f0-9]{40})$/m)?.[1];
  const source = 'https://github.com/1bytelabs/ZlPlay' + (revision ? '/tree/' + revision : '');
  return `<details class="legal" data-license-panel><summary>${tr('Licenças e código-fonte', 'Licenses and source code', 'Licencias y código fuente')}</summary><p>${tr('ZLPlay e JUCE: AGPLv3. O áudio usa CoreAudio.', 'ZLPlay and JUCE: AGPLv3. Audio uses CoreAudio.', 'ZLPlay y JUCE: AGPLv3. El audio usa CoreAudio.')}</p><p>${tr('Textos legais disponíveis no idioma original.', 'Legal texts are available in their original language.', 'Textos legales disponibles en su idioma original.')}</p><a target="_blank" rel="noopener noreferrer" href="${source}">${tr('Acessar código-fonte', 'View source code', 'Ver código fuente')}</a>${entries.map(([label, file]) => `<details class="license-text"><summary>${escape(label)}</summary><pre>${escape(read(file))}</pre></details>`).join('')}</details>`;
}
module.exports = {licensePanel};
