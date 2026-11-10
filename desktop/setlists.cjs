const fs = require('node:fs/promises');
const path = require('node:path');
const {showOpenDialog,showSaveDialog} = require('./native-dialogs.cjs');
const {applyDocumentIcon} = require('./document-icon.cjs');
const {randomUUID} = require('node:crypto');

const isSetlist = file => /\.(zlset|zlplaylibrary)$/i.test(file);

async function writeJson(file, value) {
  const temporary = file + '.' + randomUUID() + '.tmp';
  try {
    await fs.writeFile(temporary, JSON.stringify(value, null, 2), {flag:'wx'});
    await fs.rename(temporary, file);
  } finally {
    await fs.unlink(temporary).catch(() => {});
  }
}

function setupSetlists({app, ipcMain, window, dialog, authorize, collect, tr}) {
  const indexFile = path.join(app.getPath('userData'), 'setlists.json');
  const locations = new Map();
  let queue = Promise.resolve();
  async function readIndex() {
    try {
      const data = JSON.parse(await fs.readFile(indexFile, 'utf8'));
      return {entries:Array.isArray(data.entries) ? data.entries : [],last:data.last || null};
    } catch (error) {
      if (error.code === 'ENOENT') return {entries:[],last:null};
      throw error;
    }
  }
  function update(change) {
    const next = queue.catch(() => {}).then(async () => {
      const index = await readIndex();
      change(index);
      await fs.mkdir(path.dirname(indexFile), {recursive:true});
      await writeJson(indexFile, index);
    });
    queue = next;
    return next;
  }
  function remember(file) {
    return update(index => {
      let entry = index.entries.find(item => item.file === file);
      if (!entry) {
        entry = {id:randomUUID(),file};
        index.entries.push(entry);
      }
      index.last = entry.id;
    });
  }
  async function selection(file) {
    const result = await collect(file);
    locations.set(result.id, result.selected ? {root:path.dirname(file),file:result.library ? file : null} : {root:file,file:null});
    return result;
  }
  const handle = (action, callback) => ipcMain.handle('zlplay:setlists:'+action, (event, ...args) => {
    authorize(event);
    return callback(...args);
  });
  handle('list', async () => {
    await queue;
    const index = await readIndex();
    return {last:index.last,entries:index.entries.map(({id,file}) => ({id,name:path.parse(file).name}))};
  });
  handle('open', async id => {
    await queue;
    const index = await readIndex();
    const entry = index.entries.find(item => item.id === id);
    return entry ? selection(entry.file) : null;
  });
  handle('activate', id => {
    const file = locations.get(id)?.file;
    if (file) return remember(file);
  });
  handle('remove', id => update(index => {
    index.entries = index.entries.filter(item => item.id !== id);
    if (index.last === id) index.last = null;
  }));
  handle('clear', () => update(index => { index.entries = []; index.last = null; }));
  handle('add-song', async id => {
    const location = locations.get(id);
    if (!location) throw Error(tr('Abra o repertório novamente.','Reopen the setlist.','Vuelve a abrir el repertorio.'));
    const root = await fs.realpath(location.root);
    const result = await showOpenDialog({app,dialog,window},{
      title:tr('Adicionar música','Add song','Añadir canción'),
      defaultPath:root,properties:['openDirectory']
    });
    if (result.canceled) return null;
    const folder = await fs.realpath(result.filePaths[0]);
    if (path.dirname(folder) !== root) {
      throw Error(tr('Escolha uma pasta de música dentro da pasta principal do repertório.','Choose a song folder inside the setlist root.','Elige una carpeta de canción dentro de la carpeta principal del repertorio.'));
    }
    const selected = await collect(folder);
    return selected.entries.map(entry => ({...entry,relativePath:path.basename(location.root)+'/'+entry.relativePath}));
  });
  handle('save', async (id,data) => {
    const location = locations.get(id);
    if (!location || data?.type !== 'zlplay-library' || !Array.isArray(data.order) || !Array.isArray(data.songs)) throw Error('Repertório inválido.');
    let file = location.file;
    if (!file) {
      const result = await showSaveDialog({app,dialog,window},{
        title:tr('Salvar repertório','Save setlist','Guardar repertorio'),
        defaultPath:path.join(location.root,'Repertório.zlset'),
        filters:[{name:'ZLPlay',extensions:['zlset']}]
      });
      if (result.canceled || !result.filePath) return null;
      file = isSetlist(result.filePath) ? result.filePath : result.filePath+'.zlset';
      if (await fs.realpath(path.dirname(file)) !== await fs.realpath(location.root)) {
        throw Error(tr('Salve na pasta principal do repertório.','Save in the setlist root folder.','Guarda en la carpeta principal del repertorio.'));
      }
    }
    const manifest = {...data};
    delete manifest.sourceRoot;
    if (Buffer.byteLength(JSON.stringify(manifest, null, 2)) > 16*1024*1024) throw Error('Repertório grande demais.');
    await writeJson(file,manifest);
    await applyDocumentIcon(app,file);
    location.file = file;
    await remember(file);
    return path.basename(file);
  });
  return selection;
}

module.exports = {setupSetlists};
