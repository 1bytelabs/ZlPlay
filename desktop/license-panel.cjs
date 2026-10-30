const fs = require('node:fs');
const path = require('node:path');
const escape = value => String(value).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));

function licensePanel({language = 'pt-BR', platform = process.platform, resources = process.resourcesPath, root = path.join(__dirname, '..'), packaged = false} = {}) {
  // This panel documents the Windows distribution; the Mac keeps its own About flow.
  if (platform !== 'win32') return '';
  const tr = (pt, en, es) => language === 'pt-BR' ? pt : language === 'es' ? es : en;
  const legal = packaged ? path.join(resources, 'licenses') : root;
  const native = packaged ? path.join(resources, 'native') : path.join(root, 'native', 'bin');
  const entries = packaged ? [
    ['ZLPlay · AGPLv3', path.join(legal, 'ZLPlay-AGPL-3.0.txt')],
    ['GNU GPLv3', path.join(legal, 'GPL-3.0.txt')],
    ['Steinberg ASIO · GPLv3', path.join(legal, 'ASIO-LICENSE.txt')],
    [tr('Modelo de licença', 'Licensing model', 'Modelo de licencia'), path.join(legal, 'LICENSING.md')],
    [tr('Avisos de terceiros', 'Third-party notices', 'Avisos de terceros'), path.join(legal, 'THIRD_PARTY_NOTICES.md')],
    [tr('Fontes deste build', 'Sources for this build', 'Fuentes de esta compilación'), path.join(legal, 'SOURCE_CODE.txt')]
  ] : [
    ['ZLPlay · AGPLv3', path.join(root, 'LICENSE')],
    ['GNU GPLv3', path.join(root, 'docs/licenses/GPL-3.0.txt')],
    ['Steinberg ASIO · GPLv3', path.join(root, 'docs/licenses/ASIO-LICENSE.txt')],
    [tr('Modelo de licença', 'Licensing model', 'Modelo de licencia'), path.join(root, 'docs/LICENSING.md')],
    [tr('Avisos de terceiros', 'Third-party notices', 'Avisos de terceros'), path.join(root, 'THIRD_PARTY_NOTICES.md')]
  ];
  for (const [label, name] of [['JUCE', 'JUCE-LICENSE.md'], ['Signalsmith Stretch', 'Signalsmith-Stretch-LICENSE.txt'], ['Signalsmith Linear', 'Signalsmith-Linear-LICENSE.txt'], ['libebur128', 'libebur128-LICENSE.txt'], ['libebur128 · queue', 'libebur128-queue-LICENSE.h']]) entries.push([label, path.join(native, name)]);
  const read = file => { try { return fs.readFileSync(file, 'utf8'); } catch { return tr('Texto indisponível nesta instalação.', 'Text unavailable in this installation.', 'Texto no disponible en esta instalación.'); } };
  if (packaged) entries.push(['Electron · MIT', path.join(resources, '../LICENSE.electron.txt')]);
  const provenance = packaged ? read(path.join(legal, 'SOURCE_CODE.txt')) : '';
  const revision = provenance.match(/^Commit: ([a-f0-9]{40})$/m)?.[1];
  const source = 'https://github.com/1bytelabs/ZlPlay' + (revision ? '/tree/' + revision : '');
  return `<details class="legal" data-license-panel><summary>${tr('Licenças e código-fonte', 'Licenses and source code', 'Licencias y código fuente')}</summary><p>${tr('ZLPlay e JUCE: AGPLv3. No Windows, ASIO utiliza a alternativa GPLv3. Os drivers são instalados separadamente.', 'ZLPlay and JUCE: AGPLv3. On Windows, ASIO uses the GPLv3 option. Drivers are installed separately.', 'ZLPlay y JUCE: AGPLv3. En Windows, ASIO utiliza la alternativa GPLv3. Los drivers se instalan por separado.')}</p><p>ASIO is a registered trademark of Steinberg Media Technologies GmbH.</p><p>${tr('Textos legais disponíveis no idioma original.', 'Legal texts are available in their original language.', 'Textos legales disponibles en su idioma original.')}</p><a target="_blank" rel="noopener noreferrer" href="${source}">${tr('Acessar código-fonte', 'View source code', 'Ver código fuente')}</a>${entries.map(([label, file]) => `<details class="license-text"><summary>${escape(label)}</summary><pre>${escape(read(file))}</pre></details>`).join('')}</details>`;
}
module.exports = {licensePanel};
