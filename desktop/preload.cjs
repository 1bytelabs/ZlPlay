const { contextBridge, ipcRenderer, webUtils } = require('electron');
contextBridge.exposeInMainWorld('zlplayNative', {
  remoteConfig: action => ipcRenderer.invoke('zlplay:remote-config',action),
  remoteState: state => ipcRenderer.send('zlplay:remote-state',state),
  remoteResult: result => ipcRenderer.send('zlplay:remote-result',result),
  onRemoteCommand: callback => {const listener=(_event,message)=>callback(message);ipcRenderer.on('zlplay:remote-command',listener);return ()=>ipcRenderer.removeListener('zlplay:remote-command',listener);},
  describe: file => {
    if(file._zlToken)return ipcRenderer.invoke('zlplay:describe-token',file._zlToken);
    const filename = webUtils.getPathForFile(file);
    return filename ? ipcRenderer.invoke('zlplay:describe', filename) : Promise.resolve(null);
  },
  command: message => ipcRenderer.invoke('zlplay:audio', message),
  bindProject: token => ipcRenderer.invoke('zlplay:bind-project',token),
  saveLibrary: process.platform==='darwin'?message => ipcRenderer.invoke('zlplay:save-library',message):undefined,
  saveProject: message => ipcRenderer.invoke('zlplay:save-project',message),
  onGuideProgress: callback => {const listener=(_e,progress)=>callback(progress);ipcRenderer.on('zlplay:guide-progress',listener);return ()=>ipcRenderer.removeListener('zlplay:guide-progress',listener);},
  recognizeGuide: message => ipcRenderer.invoke('zlplay:recognize-guide',message),
  cancelGuide: () => ipcRenderer.invoke('zlplay:cancel-guide'),
  analyze: message => ipcRenderer.invoke('zlplay:analyze',message),
  cancelAnalysis: () => ipcRenderer.invoke('zlplay:cancel-analysis'),
  saveLeveledProject: (token,data) => ipcRenderer.invoke('zlplay:save-leveled',token,data),
  describeToken: token => ipcRenderer.invoke('zlplay:describe-token',token),
  openSelection: mode => ipcRenderer.invoke('zlplay:open-selection',mode),
  takePendingOpen: () => ipcRenderer.invoke('zlplay:pending-open'),
  onOpenRequest: callback => { const listener=()=>callback();ipcRenderer.on('zlplay:open-request',listener);return ()=>ipcRenderer.removeListener('zlplay:open-request',listener); },
  language: () => ipcRenderer.invoke('zlplay:language'),
  setLanguage: language => ipcRenderer.invoke('zlplay:set-language',language),
  about: () => ipcRenderer.invoke('zlplay:about'),
  padFolder: action => ipcRenderer.invoke('zlplay:pad-folder', action),
  sounds: message => ipcRenderer.invoke('zlplay:sounds',message),
  peaks: (token,range) => ipcRenderer.invoke('zlplay:peaks', token,range)
});
