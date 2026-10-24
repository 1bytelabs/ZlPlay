const {test}=require('node:test');const assert=require('node:assert/strict');const fs=require('node:fs/promises');const path=require('node:path');const os=require('node:os');const {collectSelection}=require('../desktop/file-open.cjs');
test('repertório .zlset abre com conteúdo e exclui outro manifesto legado da raiz',async()=>{const folder=await fs.mkdtemp(path.join(os.tmpdir(),'zlset-'));try{await fs.writeFile(path.join(folder,'Antigo.zlplaylibrary'),'{}');const chosen=path.join(folder,'Culto.zlset');await fs.writeFile(chosen,JSON.stringify({type:'zlplay-library',songs:[{name:'Música',tonalKey:'D'}]}));const result=await collectSelection(chosen,x=>x);assert.equal(result.library,true);assert.equal(result.entries.length,1);assert.equal(JSON.parse(result.entries[0].text).songs[0].tonalKey,'D');}finally{await fs.rm(folder,{recursive:true,force:true});}});
test('seleção nativa mantém caminhos relativos, biblioteca escolhida e tokens',{timeout:10000},async()=>{const folder=await fs.mkdtemp(path.join(os.tmpdir(),'zlplay-open-'));try{await fs.mkdir(path.join(folder,'Música com acento'));await fs.writeFile(path.join(folder,'Música com acento','Track.wav'),'test');await fs.writeFile(path.join(folder,'Biblioteca.zlplaylibrary'),'{}');const chosen=path.join(folder,'Culto.zlplaylibrary');await fs.writeFile(chosen,JSON.stringify({type:'zlplay-library',songs:[{folder:'Música com acento'}]}));const allowed=[];const result=await collectSelection(chosen,f=>{allowed.push(f);return String(allowed.length);});assert.equal(result.library,true);assert.equal(result.entries.filter(e=>e.name.endsWith('.zlplaylibrary')).length,1);assert.ok(result.entries.find(e=>e.relativePath.includes('Música com acento/Track.wav')));assert.ok(result.entries.every(e=>e.token));}finally{await fs.rm(folder,{recursive:true,force:true});}});

test('repertório deve ser salvo na pasta das músicas e abre só as músicas registradas',async()=>{
  const {setupLibraryStore}=require('../desktop/library-store.cjs');
  const root=await fs.mkdtemp(path.join(os.tmpdir(),'zlset-location-'));
  try{
    const music=path.join(root,'Músicas'),outside=path.join(root,'Documentos','Culto.zlset'),saved=path.join(music,'Culto.zlset');
    await fs.mkdir(path.dirname(outside),{recursive:true});
    const tokens=new Map(),register=file=>{const token=String(tokens.size);tokens.set(token,file);return token;};
    for(const name of ['Primeira','Segunda','Outra música']){
      await fs.mkdir(path.join(music,name),{recursive:true});
      await fs.writeFile(path.join(music,name,'Projeto.zlplay'),JSON.stringify({format:'tom-certo-project',tracks:[]}));
      await fs.writeFile(path.join(music,name,'Guia.wav'),'synthetic fixture');
    }
    const picked=await collectSelection(music,register),handlers=new Map();let destination=outside,canceled=false;
    setupLibraryStore({app:{},ipcMain:{handle:(name,handler)=>handlers.set(name,handler)},window:{},authorize(){},lookup:token=>tokens.get(token),dialog:{showSaveDialog:async(_window,options)=>{assert.equal(options.defaultPath,path.join(music,'Repertório.zlset'));return {canceled,filePath:destination};}}});
    const data={type:'zlplay-library',sourceRoot:'../obsolete',order:['Segunda','Primeira'],songs:[{name:'Primeira',folder:'Primeira',originalKey:'C',tonalKey:'D'},{name:'Segunda',folder:'Segunda',originalKey:'E',tonalKey:'F#'}]};
    const save=()=>handlers.get('zlplay:save-library')({}, {data,entries:picked.entries});
    await assert.rejects(save,/pasta principal/);
    await assert.rejects(fs.access(outside),{code:'ENOENT'});
    destination=saved;await save();
    const reopened=await collectSelection(saved,register);
    assert.equal(reopened.library,true);assert.equal(reopened.needsLibraryRoot,false);
    assert.ok(reopened.entries.some(entry=>entry.relativePath==='Músicas/Primeira/Projeto.zlplay'));
    assert.ok(reopened.entries.some(entry=>entry.relativePath==='Músicas/Segunda/Guia.wav'));
    assert.ok(!reopened.entries.some(entry=>entry.relativePath.includes('/Outra música/')));
    const manifest=JSON.parse(reopened.entries.find(entry=>entry.relativePath===reopened.selected).text);
    assert.equal(manifest.sourceRoot,undefined);
    assert.deepEqual(manifest.order,data.order);assert.deepEqual(manifest.songs,data.songs);
    await fs.writeFile(outside,JSON.stringify(data));
    assert.equal((await collectSelection(outside,register)).needsLibraryRoot,true);
    const before=await fs.readFile(saved,'utf8');canceled=true;
    assert.equal(await save(),null);assert.equal(await fs.readFile(saved,'utf8'),before);
  }finally{await fs.rm(root,{recursive:true,force:true});}
});
