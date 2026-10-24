const fs=require('node:fs/promises');
const path=require('node:path');
const {randomUUID}=require('node:crypto');
function setupLibraryStore({app,ipcMain,window,authorize,lookup,dialog:injectedDialog}){
  ipcMain.handle('zlplay:save-library',async(event,message)=>{
    authorize(event);
    const data=structuredClone(message?.data),entries=message?.entries;
    if(data?.type!=='zlplay-library'||!Array.isArray(data.songs)||!Array.isArray(entries)||!entries.length||entries.length>12000)throw Error('Repertório inválido.');
    let root;
    for(const entry of entries){
      const parts=String(entry.relativePath||'').replace(/\\/g,'/').split('/');
      if(parts.length<3||parts.some(p=>!p||p==='.'||p==='..'))throw Error('Referência de repertório inválida.');
      const full=lookup(entry.token),relative=parts.slice(1).join('/');
      let candidate=full;for(let i=1;i<parts.length;i++)candidate=path.dirname(candidate);
      if((root&&root!==candidate)||path.resolve(candidate,relative)!==full)throw Error('As músicas devem pertencer à mesma pasta principal.');
      root=candidate;
    }
    const dialog=injectedDialog||require('electron').dialog;
    const result=await dialog.showSaveDialog(window,{title:message.language==='en'?'Save setlist':message.language==='es'?'Guardar repertorio':'Salvar repertório',defaultPath:path.join(root,'Repertório.zlset'),filters:[{name:'Repertório ZLPlay',extensions:['zlset']}]});
    if(result.canceled)return null;
    const file=/\.zlset$/i.test(result.filePath)?result.filePath:result.filePath+'.zlset';
    if(await fs.realpath(path.dirname(file))!==await fs.realpath(root))throw Error(message.language==='en'?'Save the setlist inside the main music folder.':message.language==='es'?'Guarda el repertorio dentro de la carpeta principal de canciones.':'Salve o repertório dentro da pasta principal das músicas.');
    delete data.sourceRoot;
    const text=JSON.stringify(data,null,2);
    if(Buffer.byteLength(text)>16*1024*1024)throw Error('Repertório grande demais.');
    const temporary=file+'.'+randomUUID()+'.tmp';
    try{await fs.writeFile(temporary,text,{flag:'wx'});await fs.rename(temporary,file);}finally{await fs.unlink(temporary).catch(()=>{});}
    await require('./document-icon.cjs').applyDocumentIcon(app,file);
    return {name:path.basename(file)};
  });
}
module.exports={setupLibraryStore};
