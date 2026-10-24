const path=require('node:path');
const {execFile}=require('node:child_process');
async function applyDocumentIcon(app,file){
  if(process.platform!=='darwin'||!app?.getAppPath)return;
  const resources=app.isPackaged?process.resourcesPath:app.getAppPath();
  const helper=path.join(resources,app.isPackaged?'native':'native/bin','zlplay-document-icon');
  const icon=path.join(resources,app.isPackaged?'zlplay-document.icns':'desktop/zlplay-document.icns');
  await new Promise(resolve=>execFile(helper,[icon,file],{timeout:5000},error=>{
    if(error)console.warn('ZLPlay: não foi possível aplicar o ícone do documento.');
    resolve();
  }));
}
module.exports={applyDocumentIcon};
