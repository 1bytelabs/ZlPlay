const fs=require('node:fs/promises');
const path=require('node:path');
const {randomUUID}=require('node:crypto');
const extensions=/\.(zlplay|zlset|zlplaylibrary|rpp|wav|mp3|flac|ogg|aiff?|opus|m4a|aac)$/i;
async function collectSelection(selected,register){
  const stat=await fs.stat(selected),directory=stat.isDirectory()?selected:path.dirname(selected),base=path.basename(directory),entries=[];
  async function walk(folder,depth){
    if(depth>5)return;
    for(const entry of await fs.readdir(folder,{withFileTypes:true})){
      if(entry.isSymbolicLink()||/^(\.git|node_modules|Peaks|Backups)$/i.test(entry.name))continue;
      const full=path.join(folder,entry.name);
      if(entry.isDirectory()){await walk(full,depth+1);continue;}
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
  return {id:randomUUID(),selected:stat.isFile()?base+'/'+path.basename(selected):null,library:/\.(zlset|zlplaylibrary)$/i.test(selected),entries};
}
module.exports={collectSelection};
