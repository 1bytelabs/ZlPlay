const fs=require('node:fs'),path=require('node:path');
function prepareMacLicenses(root,output,{commit,sourceCommit=commit}){
  if(!/^[a-f0-9]{40}$/.test(commit)||!/^[a-f0-9]{40}$/.test(sourceCommit))throw Error('A full source revision is required.');
  fs.mkdirSync(output,{recursive:true});
  for(const [source,target]of [['LICENSE','ZLPlay-AGPL-3.0.txt'],['THIRD_PARTY_NOTICES.md','THIRD_PARTY_NOTICES.md'],['docs/licenses/QRCode-MIT.txt','QRCode-MIT.txt']])fs.copyFileSync(path.join(root,source),path.join(output,target));
  fs.writeFileSync(path.join(output,'SOURCE_CODE.txt'),
    `ZLPlay macOS — CoreAudio\nLocal build commit: ${commit}\nPublished equivalent source snapshot: ${sourceCommit}\nhttps://github.com/1bytelabs/ZlPlay/archive/${sourceCommit}.zip\nBuild instructions: scripts/build-macos.mjs and docs/macos.md in that revision.\nThe release tag may refer to a different platform snapshot; use this source revision.\nA tag da release pode apontar para outra plataforma; use esta revisão dos fontes.\nLa etiqueta de la versión puede apuntar a otra plataforma; use esta revisión de los fuentes.\n`);
  return output;
}
module.exports={prepareMacLicenses};
