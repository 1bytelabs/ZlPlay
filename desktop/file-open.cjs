const fs=require('node:fs/promises');
const path=require('node:path');
const {randomUUID}=require('node:crypto');
const extensions=/\.(zlplay|zlset|zlplaylibrary|rpp|wav|mp3|flac|ogg|aiff?|opus|m4a|aac)$/i;
async function collectSelection(selected,register){
  const stat=await fs.stat(selected),isLibrary=stat.isFile()&&/\.(zlset|zlplaylibrary)$/i.test(selected);
  let directory=stat.isDirectory()?selected:path.dirname(selected),libraryText,libraryData;
  if(isLibrary){
    if(stat.size>16*1024*1024)throw Error('Arquivo de repertório grande demais.');
    libraryText=await fs.readFile(selected,'utf8');
    const data=JSON.parse(libraryText);
    libraryData=data;
    if(data.type!=='zlplay-library')throw Error('Repertório inválido.');

  }
  const base=path.basename(directory),entries=[],songFolders=new Set((libraryData?.songs||[]).map(song=>song.folder||song.name));
  async function walk(folder,depth){
    if(depth>5)return;
    for(const entry of await fs.readdir(folder,{withFileTypes:true})){
      if(entry.isSymbolicLink()||/^(\.git|node_modules|Peaks|Backups)$/i.test(entry.name))continue;
      const full=path.join(folder,entry.name);
      if(entry.isDirectory()){if(isLibrary&&depth===0&&!songFolders.has(entry.name))continue;await walk(full,depth+1);continue;}
      if(!extensions.test(entry.name))continue;
      if(entries.length>=12000)throw Error('A pasta contém arquivos demais. Escolha uma pasta de músicas menor.');
      const token=register(full),relativePath=base+'/'+path.relative(directory,full).replace(/\\/g,'/');
      const item={token,name:entry.name,relativePath};
      if(/\.(zlplay|zlset|zlplaylibrary|rpp)$/i.test(entry.name)){
        if((await fs.stat(full)).size>16*1024*1024)throw Error('Arquivo de projeto grande demais.');
        item.text=await fs.readFile(full,'utf8');
      }
      if(!(stat.isFile()&&/\.(zlset|zlplaylibrary)$/i.test(selected)&&folder===directory&&/\.(zlset|zlplaylibrary)$/i.test(entry.name)&&full!==selected))entries.push(item);
    }
  }
  await walk(directory,0);
  if(isLibrary){
    const selectedPath=base+'/'+path.basename(selected);
    // Keep only the selected setlist as the root manifest.
    for(let i=entries.length-1;i>=0;i--)if(/\.(zlset|zlplaylibrary)$/i.test(entries[i].name)&&entries[i].relativePath.split('/').length===2)entries.splice(i,1);
    entries.push({token:register(selected),name:path.basename(selected),relativePath:selectedPath,text:libraryText});
  }
  // A selected project may deliberately reference audio outside its own folder.
  // Only audio references are followed; they are never executed as commands.
  for(const project of [...entries].filter(e=>/\.zlplay$/i.test(e.name))){
    let data;try{data=JSON.parse(project.text);}catch{continue;}
    if(data.format!=='tom-certo-project'||!Array.isArray(data.tracks))continue;
    const projectRelative=project.relativePath.slice(base.length+1),projectDirectory=path.dirname(path.join(directory,projectRelative));
    for(const track of data.tracks.slice(0,256))for(const item of (Array.isArray(track.items)?track.items:[]).slice(0,4096)){
      if(typeof item.file!=='string'||! /\.(wav|mp3|flac|ogg|aiff?|opus|m4a|aac)$/i.test(item.file))continue;
      const relativePath=path.posix.dirname(project.relativePath)+'/'+item.file.replace(/\\/g,'/');
      if(entries.some(e=>e.relativePath===relativePath))continue;
      const full=path.resolve(projectDirectory,item.file);try{if(!(await fs.stat(full)).isFile())continue;}catch{continue;}
      if(entries.length>=12000)throw Error('O projeto referencia arquivos demais.');
      entries.push({token:register(full),name:path.basename(full),relativePath});
    }
  }
  const folders=(libraryData?.songs||[]).map(song=>song.folder||song.name);
  const available=new Set(entries.map(entry=>entry.relativePath.split('/')[1]));
  const needsLibraryRoot=isLibrary&&folders.length>0&&!folders.some(folder=>available.has(folder));
  return {id:randomUUID(),selected:stat.isFile()?base+'/'+path.basename(selected):null,library:isLibrary,needsLibraryRoot,entries};
}
module.exports={collectSelection};
