const fs = require('node:fs/promises');
const path = require('node:path');
const NOTES = ['C','C#','D','D#','E','F','F#','G','G#','A','A#','B'];
async function inspectPadFolder(folder) {
  const result = { folder: folder || '', available: [], missing: [...NOTES], paths: Array(12).fill(''), warning: '' };
  if (!folder) return result;
  let entries;
  try { entries = await fs.readdir(folder, { withFileTypes: true }); }
  catch { result.warning = 'A pasta de pads não está disponível. Selecione a pasta novamente em Config.'; return result; }
  for (let i = 0; i < NOTES.length; i++) {
    const names = [NOTES[i] + '.wav', NOTES[i].replace('#', 's') + '.wav'];
    const entry = entries.find(e => e.isFile() && names.some(n => n.toLowerCase() === e.name.toLowerCase()));
    if (entry) { result.paths[i] = path.join(folder, entry.name); result.available.push(NOTES[i]); }
  }
  result.missing = NOTES.filter(n => !result.available.includes(n));
  if (result.missing.length) result.warning = 'Pads ausentes: ' + result.missing.join(', ') + '. Confira os arquivos na pasta configurada.';
  return result;
}
module.exports = { inspectPadFolder, NOTES };
