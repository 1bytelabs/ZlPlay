const path = require('node:path');
function assetPath(root, address) {
  const url = new URL(address);
  if (url.protocol !== 'zlplay:' || url.host !== 'app') return null;
  let name;
  try { name = decodeURIComponent(url.pathname); } catch { return null; }
  if (name.includes('\0') || name.includes('\\')) return null;
  const file = path.resolve(root, '.' + (name === '/' ? '/index.html' : name));
  const relative = path.relative(root, file);
  if (relative.startsWith('..') || path.isAbsolute(relative)) return null;
  return file;
}
module.exports = { assetPath };
