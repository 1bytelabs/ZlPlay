const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs/promises');
const os=require('node:os');
const path=require('node:path');
const {inspectPadFolder}=require('../desktop/pad-folder.cjs');
test('pasta de pads: sem configuração, nomes sustenidos e exclusão de arquivos/pasta',async()=>{
  assert.equal((await inspectPadFolder('')).warning,'');
  const folder=await fs.mkdtemp(path.join(os.tmpdir(),'zlplay-pads-'));
  try {
    await fs.writeFile(path.join(folder,'C.wav'),'test');
    await fs.writeFile(path.join(folder,'Cs.WAV'),'test');
    await fs.writeFile(path.join(folder,'D#.wav'),'test');
    let state=await inspectPadFolder(folder);
    assert.deepEqual(state.available,['C','C#','D#']);assert.ok(state.missing.includes('B'));
    await fs.unlink(path.join(folder,'C.wav'));
    state=await inspectPadFolder(folder);assert.ok(state.missing.includes('C'));assert.match(state.warning,/ausentes/);
  } finally { await fs.rm(folder,{recursive:true,force:true}); }
  assert.match((await inspectPadFolder(folder)).warning,/pasta.*não está disponível/);
});
