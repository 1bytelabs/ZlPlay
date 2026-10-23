const {nativeExecutable}=require('./runtime-paths.cjs');
const { spawn } = require('node:child_process');
const { randomUUID } = require('node:crypto');
const fs = require('node:fs/promises');
const path = require('node:path');
const readline = require('node:readline');
const { inspectPadFolder } = require('./pad-folder.cjs');
class NativeAudio {
  constructor(executable) { this.executable = executable; this.pending = new Map(); this.sequence = 0; }
  start() {
    if (this.child) return;
    const child = spawn(this.executable, [], { windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] });
    this.child = child;
    readline.createInterface({ input: child.stdout }).on('line', line => {
      try {
        const reply = JSON.parse(line), request = this.pending.get(reply.id);
        if (!request) return;
        clearTimeout(request.timer); this.pending.delete(reply.id);
        reply.ok ? request.resolve(reply.result) : request.reject(new Error(reply.error));
      } catch { /* Non-protocol diagnostic output must never execute in the renderer. */ }
    });
    let diagnostics = '';
    child.stderr.on('data', data => { diagnostics = (diagnostics + data.toString()).slice(-2000); });
    const stopped = error => {
      if (this.child === child) this.child = null;
      for (const pending of this.pending.values()) { clearTimeout(pending.timer); pending.reject(error); }
      this.pending.clear();
    };
    child.on('error', stopped);
    child.on('exit', code => stopped(new Error(`O motor de áudio foi encerrado (código ${code}). ${diagnostics}`)));
  }
  request(data, timeout = 30000) {
    this.start(); const id = ++this.sequence;
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => { this.pending.delete(id); reject(new Error('O motor de áudio demorou para responder.')); }, timeout);
      this.pending.set(id, { resolve, reject, timer });
      this.child.stdin.write(JSON.stringify({ ...data, id }) + '\n', error => { if (error) { clearTimeout(timer); this.pending.delete(id); reject(error); } });
    });
  }
  close() { if (this.child) { this.child.stdin.end(); this.child.kill(); this.child = null; } }
}
function setupNativeAudio({ app, ipcMain, window }) {
  const executable = nativeExecutable(app.isPackaged?path.join(process.resourcesPath,'native'):path.join(app.getAppPath(),'native','bin'));
  const engine = new NativeAudio(executable), peaks = new NativeAudio(executable);
  const files = new Map(), byPath = new Map();
  let closing = false;
  let peaksQueue = Promise.resolve();
  function authorize(event) {
    if (window.isDestroyed() || event.sender !== window.webContents || event.senderFrame?.url !== 'zlplay://app/index.html') throw new Error('Origem não autorizada.');
  }
  function lookup(token) { const file = files.get(token); if (!file) throw new Error('Selecione o arquivo de áudio novamente.'); return file; }
  require('./speech.cjs').setupSpeech({app,ipcMain,authorize,lookup,NativeAudio,executable,window});
  require('./project-store.cjs').setupProjectStore({app,ipcMain,window,authorize,lookup,register:file=>{const token=randomUUID();files.set(token,file);return token;}});
  let analyzer=null;
  ipcMain.handle('zlplay:cancel-analysis',event=>{authorize(event);analyzer?.close();analyzer=null;return true;});
  ipcMain.handle('zlplay:analyze',async(event,message)=>{
    authorize(event);if(analyzer)throw Error('Uma análise já está em andamento.');const worker=new NativeAudio(executable);analyzer=worker;
    try{const data=structuredClone(message);if(!Array.isArray(data.tracks)||!data.tracks.length||data.tracks.length>256||!Number.isFinite(data.duration)||data.duration<=0||data.duration>21600)throw Error('Projeto inválido para análise.');
      for(const track of data.tracks){if(!Array.isArray(track.items)||track.items.length>4096||!Number.isFinite(track.pitch)||Math.abs(track.pitch)>24)throw Error('Track inválida.');track.outputLeft=0;track.outputRight=1;for(const item of track.items){item.path=lookup(item.nativeRef);delete item.nativeRef;}}
      data.op='load';data.offline=true;data.master=1;data.click=false;data.channelEffects={...data.channelEffects};delete data.channelEffects.master;
      await worker.request(data,60000);return await worker.request({op:'analyzeLoudness'},600000);
    }finally{worker.close();if(analyzer===worker)analyzer=null;}
  });
  ipcMain.handle('zlplay:save-leveled',async(event,token,data)=>{
    authorize(event);const source=lookup(token);if(!/\.(zlplay|rpp|wav|mp3|flac|ogg|aiff?|opus|m4a|aac)$/i.test(source))throw Error('Referência inválida.');
    if(data?.format!=='tom-certo-project'||!Array.isArray(data.tracks)||data.tracks.length>256)throw Error('Projeto inválido.');const text=JSON.stringify(data,null,2);if(text.length>16*1024*1024)throw Error('Projeto grande demais.');
    let target=/\.zlplay$/i.test(source)?source:path.join(path.dirname(source),path.basename(source,path.extname(source))+'-nivelado.zlplay');
    if(target!==source){let index=1;const base=target;while(await fs.stat(target).catch(()=>null))target=base.replace(/\.zlplay$/i,'-'+index+++'.zlplay');}
    const backup=await fs.readFile(target,'utf8').catch(()=>null),temporary=target+'.'+randomUUID()+'.tmp';
    try{await fs.writeFile(temporary,text,{flag:'wx'});if(backup!==null)await fs.writeFile(target+'.bak',backup);await fs.rename(temporary,target);}finally{await fs.unlink(temporary).catch(()=>{});}
    const registered=randomUUID();files.set(registered,target);return {token:registered,name:path.basename(target)};
  });
  const padConfig = path.join(app.getPath('userData'), 'pad-folder.json');
  let padFolder;
  const sounds=require('./sound-bank.cjs').createSoundBank({root:app.getPath('userData'),probe:paths=>engine.request({op:'probe',files:paths.map((file,i)=>({token:String(i),path:file}))})});
  ipcMain.handle('zlplay:sounds',async(event,message={})=>{authorize(event);const {dialog,shell}=require('electron');const action=message.action||'status';if(action==='open'){await fs.mkdir(sounds.folder,{recursive:true});await shell.openPath(sounds.folder);}else if(action==='reset'){await sounds.resetClick();await engine.request({op:'clickSounds',...(await sounds.get()),normal:(await sounds.get()).normal||'',accent:(await sounds.get()).accent||''});}else if(['pads','normal','accent'].includes(action)){const language=message.language||'en',title=language==='pt-BR'?'Importar sons':language==='es'?'Importar sonidos':'Import sounds';const selected=await dialog.showOpenDialog(window,{title,properties:action==='pads'?['openFile','multiSelections']:['openFile'],filters:[{name:'Audio',extensions:['wav','flac','mp3','ogg','aif','aiff']}]});if(!selected.canceled){await sounds.importFiles(action,selected.filePaths);const bank=await sounds.get();await engine.request({op:'clickSounds',normal:bank.normal||'',accent:bank.accent||''});}}else if(action==='preview'){const bank=await sounds.get();await engine.request({op:'clickSounds',normal:bank.normal||'',accent:bank.accent||''});await engine.request({op:'previewClick',accent:message.accent===true});}else if(action!=='status')throw Error('Ação inválida.');return sounds.status();});
  async function pads() {
    const bank=await sounds.get();if(bank.pads?.length===12){const available=[],missing=[];for(let i=0;i<12;i++)(await fs.stat(bank.pads[i]).catch(()=>null))?available.push(i):missing.push(i);return {folder:path.dirname(bank.pads[0]),paths:bank.pads,available,missing,warning:missing.length?'Pads importados ausentes. Importe os arquivos novamente.':''};}
    if (padFolder === undefined) { try { padFolder = JSON.parse(await fs.readFile(padConfig, 'utf8')).folder || ''; } catch { padFolder = ''; } }
    return inspectPadFolder(padFolder);
  }
  ipcMain.handle('zlplay:pad-folder', async (event, action) => {
    authorize(event);
    if (action === 'choose') {
      const { dialog } = require('electron');
      const selection = await dialog.showOpenDialog(window, { title: 'Escolha sua pasta de pads', properties: ['openDirectory'] });
      if (!selection.canceled) { padFolder = selection.filePaths[0]; await fs.mkdir(path.dirname(padConfig), { recursive: true }); await fs.writeFile(padConfig, JSON.stringify({ folder: padFolder })); }
    } else if (action !== undefined && action !== 'status') throw new Error('Ação inválida.');
    const result = await pads();
    const { paths, ...status } = result; return status;
  });
  async function describe(filename) {
    if (typeof filename !== 'string' || !path.isAbsolute(filename) || !/\.(wav|mp3|flac|ogg|aiff?|opus|m4a|aac)$/i.test(filename)) throw new Error('Arquivo de áudio inválido.');
    const full = await fs.realpath(filename), stat = await fs.stat(full);
    if (!stat.isFile()) throw new Error('Selecione um arquivo.');
    const key = `${full}|${stat.size}|${stat.mtimeMs}`;
    if (byPath.has(key)) return byPath.get(key);
    const token = randomUUID();
    const [metadata] = await engine.request({ op: 'probe', files: [{ token, path: full }] });
    const result = { ...metadata, nativeRef: token };
    files.set(token, full); byPath.set(key, result); return result;
  }
  ipcMain.handle('zlplay:describe',async(event,filename)=>{authorize(event);return describe(filename);});
  ipcMain.handle('zlplay:describe-token',async(event,token)=>{authorize(event);return describe(lookup(token));});
  ipcMain.handle('zlplay:audio', async (event, message) => {
    if(closing)return {};
    if(message.op==='clickEnvelope'){authorize(event);return peaks.request({...message,path:lookup(message.nativeRef)},60000);}
    authorize(event);
    if (!message || typeof message !== 'object') throw new Error('Comando inválido.');
    const allowed = new Set(['load', 'play', 'pause', 'stop', 'seek', 'mix', 'status', 'master', 'devices', 'configureDevice', 'controlPanel', 'aux', 'meters', 'spectrum']);
    if (!allowed.has(message.op)) throw new Error('Comando não autorizado.');
    const data = structuredClone(message);
    if(['load','aux'].includes(data.op)){const bank=await sounds.get();await engine.request({op:'clickSounds',normal:bank.normal||'',accent:bank.accent||''});}
    if (data.op === 'aux' && data.padEnabled && Number.isInteger(data.note)) {
      const status = await pads();
      const filename = status.paths[data.note];
      if (!filename) throw new Error(status.warning || 'Escolha sua pasta de pads em Config.');
      const stat = await fs.stat(filename).catch(() => null);
      if (!stat?.isFile()) throw new Error('O arquivo do pad foi removido. Confira a pasta de pads em Config.');
      data.padPath = filename;
      data.padStamp = `${stat.size}:${stat.mtimeMs}`;
    } else delete data.padPath;
    if (data.op === 'load') {
      if (!Array.isArray(data.tracks) || data.tracks.length > 256) throw new Error('Quantidade de tracks inválida.');
      for (const track of data.tracks) {
        if (!Array.isArray(track.items) || track.items.length > 4096 || !Number.isFinite(track.pitch) || Math.abs(track.pitch) > 24) throw new Error('Track inválida.');
        for (const item of track.items) { item.path = lookup(item.nativeRef); delete item.nativeRef; }
      }
    }
    return engine.request(data, data.op === 'controlPanel' ? 600000 : 30000);
  });
  ipcMain.handle('zlplay:peaks', (event, token,range) => {
    authorize(event); const filename = lookup(token);
    const options=range?{start:Math.max(0,Math.min(21600,Number(range.start)||0)),end:Math.max(0,Math.min(21600,Number(range.end)||0)),bins:Math.max(64,Math.min(4000,Number(range.bins)||1600))}:{};
    const next = peaksQueue.catch(() => {}).then(() => closing ? [] : peaks.request({ op: 'peaks', path: filename, bins: 1600,...options }, 60000)).catch(error => { if (closing) return []; throw error; });
    peaksQueue = next; return next;
  });
  const cleanup = () => { closing = true; engine.close(); peaks.close(); analyzer?.close(); };
  app.on('before-quit', cleanup);
  window.on('closed', cleanup);
  return { engine, peaks, files, authorize, register:filename=>{const token=randomUUID();files.set(token,filename);return token;} };
}
module.exports = { NativeAudio, setupNativeAudio };

