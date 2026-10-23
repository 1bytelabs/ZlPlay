const { showAbout } = require('./about.cjs');
const { app, BrowserWindow, Menu, protocol, net, dialog, ipcMain, shell } = require('electron');
const { setupNativeAudio } = require('./native-audio.cjs');
const path = require('node:path');
const fs = require('node:fs/promises');
const { pathToFileURL } = require('node:url');
const { assetPath } = require('./paths.cjs');
const smoke = process.argv.includes('--smoke-test');
if (smoke) app.disableHardwareAcceleration();
app.setName('ZLPlay');
protocol.registerSchemesAsPrivileged([{ scheme: 'zlplay', privileges: {
  standard: true, secure: true, supportFetchAPI: true, stream: true
} }]);
function openProjectLink(url){try{const parsed=new URL(url);if(parsed.protocol!=='https:')return;const allowed=(parsed.hostname==='livepix.gg'&&parsed.pathname==='/1bytelabs')||(parsed.hostname==='1bytelabs.com.br'&&parsed.pathname==='/');if(allowed)shell.openExternal(parsed.href).catch(()=>{});}catch{}}
let window;
let pendingOpen=process.argv.find(a=>/\.(zlplay|zlset|zlplaylibrary)$/i.test(a)&&path.isAbsolute(a))||null;
app.on('open-file',(event,file)=>{event.preventDefault();if(/\.(zlplay|zlset|zlplaylibrary)$/i.test(file)){pendingOpen=file;window?.webContents.send('zlplay:open-request');}});
if (!app.requestSingleInstanceLock()) app.quit();
else {
  app.on('second-instance', (_event,argv) => { const file=argv.find(a=>/\.(zlplay|zlset|zlplaylibrary)$/i.test(a)&&path.isAbsolute(a));if(file){pendingOpen=file;window?.webContents.send('zlplay:open-request');} if (window) { if (window.isMinimized()) window.restore(); window.focus(); } });
  app.whenReady().then(async () => {
    const root = path.join(app.getAppPath(), 'app');
    protocol.handle('zlplay', request => {
      const file = assetPath(root, request.url);
      return file ? net.fetch(pathToFileURL(file).href) : new Response('Acesso negado', { status: 403 });
    });
    window = new BrowserWindow({ width: 1440, height: 960, minWidth: 1000, minHeight: 650,
      backgroundColor: '#07090d', title: 'ZLPlay', show: !smoke && !process.argv.includes('--integration-test'), icon: path.join(__dirname, 'icon.png'),
      webPreferences: { preload: path.join(__dirname, 'preload.cjs'), nodeIntegration: false, contextIsolation: true, sandbox: true, backgroundThrottling: false }
    });
    const nativeAudio = setupNativeAudio({ app, ipcMain, window });
    const locale=require('./locale.cjs').localeStore(app);await locale.read();
    const tr=(pt,en,es)=>locale.language==='pt-BR'?pt:locale.language==='es'?es:en;
    ipcMain.handle('zlplay:language',event=>{nativeAudio.authorize(event);return locale.read();});
    ipcMain.handle('zlplay:set-language',(event,value)=>{nativeAudio.authorize(event);return locale.write(value);});
    Menu.setApplicationMenu(null);window.setMenuBarVisibility(false);
    const {collectSelection}=require('./file-open.cjs');
    ipcMain.handle('zlplay:about',event=>{nativeAudio.authorize(event);showAbout(window,app.getVersion(),openProjectLink,locale.language);});
    const selection=async file=>collectSelection(file,nativeAudio.register);
    ipcMain.handle('zlplay:pending-open',async event=>{nativeAudio.authorize(event);if(!pendingOpen)return null;const file=pendingOpen;pendingOpen=null;return selection(file);});
    ipcMain.handle('zlplay:open-selection',async(event,mode)=>{nativeAudio.authorize(event);if(!['project','library','folder','library-folder'].includes(mode))throw Error('Seleção inválida.');const folder=mode.includes('folder');const result=await dialog.showOpenDialog(window,{title:folder?tr('Importar pasta','Import folder','Importar carpeta'):mode==='library'?tr('Abrir repertório','Open setlist','Abrir repertorio'):tr('Abrir projeto','Open project','Abrir proyecto'),properties:[folder?'openDirectory':'openFile'],filters:folder?undefined:[{name:mode==='library'?'Repertório ZLPlay':'Projeto ZLPlay / REAPER',extensions:mode==='library'?['zlset','zlplaylibrary']:['zlplay','rpp']}]});return result.canceled?null:selection(result.filePaths[0]);});
    window.webContents.on('before-input-event',(event,input)=>{if(input.type==='keyDown'&&input.key==='F11'){event.preventDefault();if(!input.isAutoRepeat)window.webContents.executeJavaScript("document.fullscreenElement?document.exitFullscreen():document.querySelector('#zosma-vs-study').requestFullscreen()",true).catch(()=>{});}});
    window.webContents.setWindowOpenHandler(({url}) => {openProjectLink(url);return { action: 'deny' };});
    window.webContents.on('will-navigate', (event, url) => {
      if (url !== 'zlplay://app/index.html') event.preventDefault();
    });
    window.webContents.session.on('will-download', (_event, item) => {
      item.setSaveDialogOptions({ title: tr('Salvar arquivo do ZLPlay','Save ZLPlay file','Guardar archivo de ZLPlay'), defaultPath: path.join(app.getPath('documents'), path.basename(item.getFilename())) });
    });
    window.on('close', event => {
      if (smoke) return;
      const choice = dialog.showMessageBoxSync(window, { type: 'question', buttons: [tr('Continuar no ZLPlay','Continue in ZLPlay','Continuar en ZLPlay'),tr('Sair','Exit','Salir')], defaultId: 0, cancelId: 0, title: tr('Fechar ZLPlay','Close ZLPlay','Cerrar ZLPlay'), message: tr('Deseja fechar o ZLPlay?','Close ZLPlay?','¿Cerrar ZLPlay?'), detail: tr('Salve o projeto e o repertório antes de sair. A reprodução será encerrada.','Save your project and setlist before exiting. Playback will stop.','Guarde el proyecto y el repertorio antes de salir. La reproducción se detendrá.') });
      if (choice === 0) event.preventDefault();
    });
    if (smoke) {
      const errors = [];
      window.webContents.on('console-message', event => { if (event.level === 'error') errors.push(event.message); });
      window.webContents.on('did-finish-load', async () => {
        try {
          const result = await window.webContents.executeJavaScript(`(async () => {
            const ctx = new AudioContext();
            const res = await fetch('./Meuclick.wav');
            const audio = await ctx.decodeAudioData(await res.arrayBuffer());
            await ctx.audioWorklet.addModule('./assets/soundtouch-processor-C1MAJdwL.js');
            const node = new AudioWorkletNode(ctx, 'soundtouch-processor');
            localStorage.setItem('zlplay-desktop-check', 'ok');
            const result = { title: document.title, controls: document.querySelectorAll('button').length, secure: isSecureContext, clickSeconds: audio.duration, worklet: !!node, storage: localStorage.getItem('zlplay-desktop-check'), savePicker: typeof showSaveFilePicker, text: document.body.innerText.slice(0,300) };
            localStorage.removeItem('zlplay-desktop-check'); await ctx.close(); return result;
          })()`);
          const diagnostic = path.join(path.dirname(nativeAudio.engine.executable), 'Meuclick.wav');
          const [metadata] = await nativeAudio.engine.request({ op: 'probe', files: [{ token: 'diagnostic', path: diagnostic }] });
          await nativeAudio.engine.request({ op: 'load', offline: true, sampleRate: metadata.sampleRate, duration: metadata.duration, tracks: [{ gain: 1, pan: 0, pitch: 0, items: [{ path: diagnostic, position: 0, offset: 0, length: metadata.duration }] }] });
          result.nativeAudio = await nativeAudio.engine.request({ op: 'renderTest', position: 0, frames: Math.floor(metadata.sampleRate * metadata.duration) });
          await fs.mkdir(path.join(app.getPath('temp'), 'zlplay-check'), { recursive: true });
          try {
            const screenshot = await window.webContents.capturePage();
            await fs.writeFile(path.join(app.getPath('temp'), 'zlplay-check', 'preview.png'), screenshot.toPNG());
          } catch (error) { console.log('Captura indisponível: ' + error.message); }
          console.log(JSON.stringify({ ...result, errors }));
          nativeAudio.engine.close();nativeAudio.peaks.close();
          app.exit(result.controls > 10 && result.secure && result.worklet && result.clickSeconds > 0 && result.nativeAudio.rms > 0 && errors.length === 0 ? 0 : 1);
        } catch (error) { console.error(error); app.exit(1); }
      });
    }
    await window.loadURL('zlplay://app/index.html');
  }).catch(error => { console.error(error); app.exit(1); });
  app.on('window-all-closed', () => app.quit());
}

