const {RemoteHost}=require('./remote-host.cjs');

// The protocol is shared; audio, native dialogs and permissions stay in the Mac adapters.
function setupRemote({app,ipcMain,window,authorize,platform=process.platform}){
  const supported=['darwin','win32'].includes(platform),requests=new Map();let serial=0;
  const host=new RemoteHost({command:command=>new Promise((resolve,reject)=>{
    const id=++serial,timer=setTimeout(()=>{requests.delete(id);reject(Error('Remote timeout'));},8000);
    requests.set(id,{resolve,reject,timer});window.webContents.send('zlplay:remote-command',{id,command});
  })});
  ipcMain.handle('zlplay:remote-config',async(event,action)=>{
    authorize(event);
    if(!['status','start','stop'].includes(action))throw Error('Invalid remote action');
    if(!supported)return {enabled:false,supported:false};
    if(action==='start'){
      try{
        await host.start();const invitation=host.invite();
        const qr=await require('qrcode').toDataURL(invitation,{errorCorrectionLevel:'M',margin:4,width:420});
        return {...host.status(),supported:true,qr};
      }catch(error){await host.stop();throw error;}
    }
    if(action==='stop')await host.stop();
    return {...host.status(),supported:true};
  });
  ipcMain.on('zlplay:remote-state',(event,state)=>{try{authorize(event);host.publish(state);}catch{}});
  ipcMain.on('zlplay:remote-result',(event,result)=>{
    authorize(event);const pending=requests.get(result?.id);if(!pending)return;
    requests.delete(result.id);clearTimeout(pending.timer);
    if(result.error)pending.reject(Error('Command rejected'));else pending.resolve(result.state);
  });
  app.on('before-quit',()=>{
    host.stop().catch(()=>{});
    for(const pending of requests.values()){clearTimeout(pending.timer);pending.reject(Error('Closed'));}
    requests.clear();
  });
  return host;
}
module.exports={setupRemote};
