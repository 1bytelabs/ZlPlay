function allowedProjectLink(url) {
  try {
    const p = new URL(url);
    if (p.protocol !== 'https:' || p.username || p.password || p.port || p.search || p.hash) return false;
    return (p.hostname === 'livepix.gg' && p.pathname === '/1bytelabs') ||
      (p.hostname === '1bytelabs.com.br' && p.pathname === '/') ||
      (p.hostname === 'github.com' && /^\/1bytelabs\/ZlPlay(?:\/tree\/[a-f0-9]{40})?\/?$/.test(p.pathname));
  } catch { return false; }
}
module.exports = {allowedProjectLink};
