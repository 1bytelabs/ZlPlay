const fs=require('node:fs/promises'),path=require('node:path'),crypto=require('node:crypto');
const hash=text=>crypto.createHash('sha256').update(text).digest('hex');
function setupProjectStore({app,ipcMain,window,authorize,lookup,register,dialog:injectedDialog}){const revisions=new Map();const tr=(pt,en,es,lang)=>lang==='en'?en:lang==='es'?es:pt;
ipcMain.handle('zlplay:guide-bank',async(e,language)=>{
 authorize(e);
 if(!['pt','en','es'].includes(language))return [];
 const root=app.isPackaged?path.join(app.getAppPath(),'app','guide-banks'):path.join(app.getAppPath(),'local-notes','guide-banks');
 const entries=[];
 for(const voice of ['1','2']){
  const folder=path.join(root,language,voice);
  for(const category of ['sections','cues']){
   const directory=path.join(folder,category);
   const files=await fs.readdir(directory,{withFileTypes:true}).catch(error=>{if(error.code==='ENOENT')return [];throw error;});
   for(const file of files)if(file.isFile()&&/\.(wav|mp3)$/i.test(file.name)){
    entries.push({voice,category,name:path.parse(file.name).name,token:register(path.join(directory,file.name))});
   }
  }
 }
 return entries;
});
ipcMain.handle('zlplay:import-guide',async(e,{anchor,language,sourceToken})=>{
 authorize(e);
 const directory=path.dirname(lookup(anchor)),dialog=injectedDialog||require('electron').dialog;
 const extensions=['wav','mp3','flac','ogg','aif','aiff'];
 const selection=sourceToken?null:await dialog.showOpenDialog(window,{
  title:tr('Adicionar guia externa','Add external guide','Añadir guía externa',language),
  properties:['openFile','multiSelections'],filters:[{name:'Audio',extensions}]
 });
 if(selection?.canceled)return [];
 const sources=sourceToken?[lookup(sourceToken)]:selection.filePaths;
 if(sources.some(source=>!extensions.includes(path.extname(source).slice(1).toLowerCase())))throw Error('Formato de áudio inválido.');
 const mediaDirectory=path.join(directory,'.zlplay-guide');
 await fs.mkdir(mediaDirectory,{recursive:true});
 if(!(await fs.lstat(mediaDirectory)).isDirectory())throw Error('Pasta de guias inválida.');
 const copied=[];
 try{
  for(const source of sources){
   const name='guia-'+crypto.randomUUID()+path.extname(source).toLowerCase();
   const destination=path.join(mediaDirectory,name);
   if(sourceToken)await fs.writeFile(destination,await fs.readFile(source),{flag:'wx'});
   else await fs.copyFile(source,destination,require('node:fs').constants.COPYFILE_EXCL);
   copied.push({destination,name,displayName:path.basename(source)});
  }
 }catch(error){
  await Promise.allSettled(copied.map(file=>fs.unlink(file.destination)));
  throw error;
 }
 return copied.map(({destination,name,displayName})=>({token:register(destination),name,relativePath:'.zlplay-guide/'+name,displayName}));
});
ipcMain.handle('zlplay:bind-project',async(e,token)=>{authorize(e);const file=lookup(token);if(!/\.zlplay$/i.test(file))return null;const revision=hash(await fs.readFile(file));revisions.set(token,revision);return {token,name:path.basename(file),revision};});
ipcMain.handle('zlplay:save-project',async(e,message)=>{authorize(e);const dialog=injectedDialog||require('electron').dialog,data=structuredClone(message.data),lang=message.language;if(data?.format!=='tom-certo-project'||!Array.isArray(data.tracks)||data.tracks.length>256)throw Error('Projeto inválido.');let file=message.token?lookup(message.token):null;if(file&&!/\.zlplay$/i.test(file))throw Error('Destino de projeto inválido.');const media=message.media;if(!Array.isArray(media)||media.length!==data.tracks.length)throw Error('Referências de áudio inválidas.');const audio=data.tracks.map((t,i)=>(t.items||[]).map((it,j)=>lookup(media[i]?.[j])));if(!file||message.saveAs){const safe=String(message.name||data.song.title||'Projeto').replace(/[\\/:*?"<>|]/g,'-').replace(/\.zlplay$/i,'');const directory=file?path.dirname(file):audio[0]?.[0]?path.dirname(audio[0][0]):app.getPath('documents');const selection=await dialog.showSaveDialog(window,{title:tr('Salvar projeto como','Save project as','Guardar proyecto como',lang),defaultPath:file||path.join(directory,safe+'.zlplay'),filters:[{name:'ZLPlay',extensions:['zlplay']}]});if(selection.canceled)return null;file=/\.zlplay$/i.test(selection.filePath)?selection.filePath:selection.filePath+'.zlplay';}else{const before=await fs.readFile(file).catch(error=>{if(error.code==='ENOENT')return null;throw error;});if(before&&hash(before)!==(message.revision||revisions.get(message.token))){const result=await dialog.showMessageBox(window,{type:'warning',title:'ZLPlay',message:tr('O arquivo foi alterado fora do ZLPlay. Deseja substituir?','The file changed outside ZLPlay. Replace it?','El archivo cambió fuera de ZLPlay. ¿Reemplazarlo?',lang),buttons:[tr('Cancelar','Cancel','Cancelar',lang),tr('Substituir','Replace','Reemplazar',lang)],cancelId:0,defaultId:0});if(result.response!==1)return null;}}
for(let i=0;i<data.tracks.length;i++)for(let j=0;j<data.tracks[i].items.length;j++)data.tracks[i].items[j].file=path.relative(path.dirname(file),audio[i][j]).replace(/\\/g,'/');const text=JSON.stringify(data,null,2);if(text.length>16*1024*1024)throw Error('Projeto grande demais.');const temporary=file+'.'+crypto.randomUUID()+'.tmp';try{await fs.writeFile(temporary,text,{flag:'wx'});await fs.rename(temporary,file);}finally{await fs.unlink(temporary).catch(()=>{});}const token=register(file),revision=hash(text);revisions.set(token,revision);return {token,revision,name:path.basename(file),directory:path.basename(path.dirname(file)),data};});}
module.exports={setupProjectStore};
