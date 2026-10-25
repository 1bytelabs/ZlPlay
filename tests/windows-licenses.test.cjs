const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const os=require('node:os');
const path=require('node:path');
const hook=require('../scripts/prepare-windows-licenses.cjs');
test('Windows package carries ASIO GPL choice, complete licenses and exact source commit',()=>{
 const root=fs.mkdtempSync(path.join(os.tmpdir(),'zlplay-licenses-'));
 try {
  for(const p of ['LICENSE','THIRD_PARTY_NOTICES.md','docs/licenses/GPL-3.0.txt','docs/licenses/ASIO-LICENSE.txt','docs/LICENSING.md']) {
   fs.mkdirSync(path.dirname(path.join(root,p)),{recursive:true});fs.copyFileSync(path.resolve(p),path.join(root,p));
  }
  const revision='a'.repeat(40),out=hook.prepareWindowsLicenses(root,revision);
  assert.equal(fs.readdirSync(out).length,6);
  assert.match(fs.readFileSync(path.join(out,'ASIO-LICENSE.txt'),'utf8'),/alternatively[\s\S]*General Public License/);
  assert.match(fs.readFileSync(path.join(out,'GPL-3.0.txt'),'utf8'),/Version 3, 29 June 2007/);
  const source=fs.readFileSync(path.join(out,'SOURCE_CODE.txt'),'utf8');
  assert.ok(source.includes('/archive/'+revision+'.zip'));
  assert.ok(source.includes('3fb5cbc0340758596dac39f7c8de47d562b4896e'));
  assert.ok(!source.includes(root));
  assert.throws(()=>hook.prepareWindowsLicenses(root,'main'));
 } finally {fs.rmSync(root,{recursive:true,force:true});}
});
test('license packaging leaves macOS untouched',async()=>{
 await hook({electronPlatformName:'darwin'});
 const p=require('../package.json');
 assert.ok(p.build.win.extraResources.some(r=>r.to==='licenses'));
 assert.equal(p.build.extraResources.some(r=>r.to==='licenses'),false);
});
