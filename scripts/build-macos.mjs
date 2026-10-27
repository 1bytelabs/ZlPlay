import fs from 'node:fs';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {createRequire} from 'node:module';
import {fileURLToPath} from 'node:url';
if(process.platform!=='darwin')throw Error('Build macOS on a Mac.');
process.chdir(fileURLToPath(new URL('../',import.meta.url)));
const args=process.argv.slice(2),arch=args.includes('--arch')?args[args.indexOf('--arch')+1]:'arm64';
if(!['arm64','x64'].includes(arch))throw Error('Use --arch arm64 or --arch x64.');
const env={...process.env,ZLPLAY_MAC_ARCH:arch};
function run(exe,argv){const r=spawnSync(exe,argv,{env,stdio:'inherit'});if(r.error)throw r.error;if(r.status!==0)throw Error('Failed: '+exe);}
run(process.execPath,['scripts/build-native.mjs']);
run(process.execPath,['scripts/build-speech.mjs']);
const speech=path.join('native','speech-'+arch);
// The model is architecture-independent. Reuse it; the downloader verifies its hash.
fs.mkdirSync(speech,{recursive:true});
const model='ggml-base-q5_1.bin';
if(!fs.existsSync(path.join(speech,model))&&fs.existsSync(path.join('native/speech',model)))fs.copyFileSync(path.join('native/speech',model),path.join(speech,model));
run(process.execPath,['scripts/download-model.mjs']);
fs.copyFileSync('native/speech/MODEL-LICENSE.txt',path.join(speech,'MODEL-LICENSE.txt'));
for(const file of ['zlplay-audio','zlplay-file-types','zlplay-document-icon'])run('/usr/bin/lipo',[path.join('native','bin-'+arch,file),'-verify_arch',arch==='x64'?'x86_64':'arm64']);
run('/usr/bin/lipo',[path.join(speech,'whisper-cli'),'-verify_arch',arch==='x64'?'x86_64':'arm64']);
if(args.includes('--native-only')){console.log('Native '+arch+' resources built. Runtime validation requires matching hardware.');process.exit(0);}
const metadata=JSON.parse(fs.readFileSync('package.json','utf8'));
const electronDist=path.resolve('.cache','electron-dist-'+arch),version=metadata.devDependencies.electron;
if(!fs.existsSync(path.join(electronDist,'Electron.app/Contents/MacOS/Electron'))){
  const {downloadArtifact}=await import('@electron/get');
  const archive=await downloadArtifact({version,arch,platform:'darwin',artifactName:'electron',cacheRoot:path.resolve('.cache/electron-downloads')});
  fs.mkdirSync(electronDist,{recursive:true});
  run('/usr/bin/ditto',['-x','-k',archive,electronDist]);
}
run('/usr/bin/lipo',[path.join(electronDist,'Electron.app/Contents/MacOS/Electron'),'-verify_arch',arch==='x64'?'x86_64':'arm64']);
const require=createRequire(import.meta.url),{build,Platform,Arch}=require('electron-builder');
const {prepareMacLicenses}=require('./prepare-macos-licenses.cjs');
const revision=spawnSync('git',['rev-parse','HEAD'],{encoding:'utf8'});
if(revision.status!==0)throw Error('Cannot identify source commit.');
const dirty=spawnSync('git',['status','--porcelain','--untracked-files=no'],{encoding:'utf8'});
if(dirty.status!==0)throw Error('Cannot identify source status.');
const sourceCommit=process.env.ZLPLAY_SOURCE_COMMIT||revision.stdout.trim();
const licenses=prepareMacLicenses(process.cwd(),path.resolve('.cache','macos-licenses-'+arch),{commit:revision.stdout.trim(),sourceCommit});
await build({targets:Platform.MAC.createTarget(args.includes('--dir')?['dir']:['dmg','zip'],arch==='arm64'?Arch.arm64:Arch.x64),publish:'never',config:{
  directories:{output:'dist/macos-'+arch},electronDist,
  extraMetadata:{zlplayBuild:{commit:revision.stdout.trim(),arch,dirty:!!dirty.stdout.trim(),minimumMacOS:'15.0',sourceCommit}},
  // electron-builder concatenates config arrays; replace the inherited Intel
  // resources before it creates file matchers, rather than appending to them.
  beforePack:context=>{
    context.packager.config.extraResources=[{from:'native/bin-'+arch,to:'native'},{from:speech,to:'speech'},{from:licenses,to:'licenses'}];
  },
  mac:{minimumSystemVersion:'15.0',identity:process.env.ZLPLAY_MAC_IDENTITY||'-',artifactName:'ZLPlay-${version}-macOS-'+arch+'.${ext}'},
}});
const bundle=path.join('dist','macos-'+arch,arch==='arm64'?'mac-arm64':'mac',metadata.productName+'.app','Contents');
for(const file of ['MacOS/'+metadata.productName,'Resources/native/zlplay-audio','Resources/native/zlplay-file-types','Resources/native/zlplay-document-icon','Resources/speech/whisper-cli'])run('/usr/bin/lipo',[path.join(bundle,file),'-verify_arch',arch==='x64'?'x86_64':'arm64']);
if(!fs.readFileSync(path.join(bundle,'Resources/speech',model)).equals(fs.readFileSync(path.join(speech,model))))throw Error('Packaged speech model differs from the verified model.');
console.log('Packaged '+arch+' architecture and offline model verified.');
