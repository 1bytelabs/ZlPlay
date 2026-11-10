const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const os = require('node:os');
const {collectSelection} = require('../desktop/file-open.cjs');
const {setupSetlists} = require('../desktop/setlists.cjs');

test('saved setlists survive a restart and removing shortcuts preserves files', async () => {
 const directory = await fs.mkdtemp(path.join(os.tmpdir(),'zlplay-setlists-'));
 try {
  const root = path.join(directory,'Week');
  const profile = path.join(directory,'profile');
  const song = path.join(root,'Song');
  await fs.mkdir(song,{recursive:true});
  await fs.writeFile(path.join(song,'song.wav'),'fixture');
  const thursday = path.join(root,'Thursday.zlset');
  const sunday = path.join(root,'Sunday.zlset');
  const payload = {type:'zlplay-library',order:['Song'],songs:[{name:'Song'}]};
  const picker = {folder:song,file:thursday};
  function start() {
   const handlers = new Map();
   const selection = setupSetlists({
    app:{getPath:()=>profile},window:null,authorize(){},tr:(_,en)=>en,
    ipcMain:{handle:(name,callback)=>handlers.set(name,callback)},
    dialog:{
     showOpenDialog:async()=>({filePaths:[picker.folder]}),
     showSaveDialog:async()=>({filePath:picker.file})
    },
    collect:file=>collectSelection(file,file=>file)
   });
   const call = (action,...args) => handlers.get('zlplay:setlists:'+action)(null,...args);
   return {selection,call};
  }
  let {selection,call} = start();
  const first = await selection(root);
  assert.deepEqual((await call('add-song',first.id)).map(entry=>entry.relativePath),['Week/Song/song.wav']);
  picker.folder=directory;
  await assert.rejects(call('add-song',first.id),/inside the setlist root/);
  assert.equal(await call('save',first.id,payload),'Thursday.zlset');
  const second = await selection(root);
  picker.file=sunday;
  await call('save',second.id,payload);
  ({call}=start());
  const index = await call('list');
  assert.deepEqual(index.entries.map(entry=>entry.name),['Thursday','Sunday']);
  const reopened = await call('open',index.last);
  assert.equal(reopened.selected,'Week/Sunday.zlset');
  assert.equal(reopened.entries.some(entry=>entry.name==='Thursday.zlset'),false);
  await call('remove',index.last);
  assert.equal((await call('list')).last,null);
  await call('clear');
  assert.deepEqual(await call('list'),{entries:[],last:null});
  for (const file of [thursday,sunday]) assert.deepEqual(JSON.parse(await fs.readFile(file,'utf8')),payload);
 } finally {
  await fs.rm(directory,{recursive:true,force:true});
 }
});
