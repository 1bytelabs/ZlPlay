import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {createRequire} from 'node:module';
import {fileURLToPath} from 'node:url';
const project=fileURLToPath(new URL('../',import.meta.url));
const args=process.argv.slice(2),bundle=path.resolve(args[0]||''),arch=args[args.indexOf('--arch')+1];
if(!args[0]||!['arm64','x64'].includes(arch))throw Error('Usage: node scripts/verify-macos-package.mjs APP --arch arm64|x64');
const root=path.join(bundle,'Contents'),expected=arch==='x64'?'x86_64':'arm64';
const plist=key=>execFileSync('/usr/libexec/PlistBuddy',['-c','Print :'+key,path.join(root,'Info.plist')],{encoding:'utf8'}).trim();
if(plist('LSMinimumSystemVersion')!=='15.0')throw Error('Expected macOS 15 minimum.');
if(plist('CFBundleIdentifier')!=='com.1bytelabs.zlplay')throw Error('Unexpected application identity.');
execFileSync('/usr/bin/codesign',['--verify','--deep','--strict',bundle],{stdio:'pipe'});
for(const relative of ['MacOS/ZLPlay','Resources/native/zlplay-audio','Resources/native/zlplay-file-types','Resources/native/zlplay-document-icon','Resources/speech/whisper-cli']){
  const file=path.join(root,relative),actual=execFileSync('/usr/bin/lipo',['-archs',file],{encoding:'utf8'}).trim();
  if(actual!==expected)throw Error('Wrong architecture: '+relative+' ('+actual+')');
  if(relative!=='MacOS/ZLPlay'){
    const version=execFileSync('/usr/bin/vtool',['-show-build',file],{encoding:'utf8'});
    if(!/minos 15\.0\b/.test(version))throw Error('Wrong deployment target: '+relative);
    const libraries=execFileSync('/usr/bin/otool',['-L',file],{encoding:'utf8'}).split('\n').slice(1).join('\n');
    if(/\/Users\/|\/opt\/homebrew\/|\/usr\/local\/|\.cache\//.test(libraries))throw Error('Local library dependency: '+relative);
  }
}
const model=path.join(root,'Resources/speech/ggml-base-q5_1.bin');
if(crypto.createHash('sha256').update(fs.readFileSync(model)).digest('hex')!=='422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898')throw Error('Model checksum mismatch.');
if(!fs.existsSync(path.join(root,'Resources',plist('CFBundleIconFile')))||!fs.existsSync(path.join(root,'Resources/zlplay-document.icns')))throw Error('Missing application/document icons.');
const require=createRequire(import.meta.url),asar=require('@electron/asar'),archive=path.join(root,'Resources/app.asar');
const metadata=JSON.parse(asar.extractFile(archive,'package.json').toString());
const head=execFileSync('git',['rev-parse','HEAD'],{cwd:project,encoding:'utf8'}).trim();
if(metadata.zlplayBuild?.commit!==head||metadata.zlplayBuild?.arch!==arch||metadata.zlplayBuild?.dirty!==false)throw Error('Build provenance does not match clean source commit.');
if(plist('CFBundleShortVersionString')!==metadata.version)throw Error('Bundle version mismatch.');
for(const license of ['ZLPlay-AGPL-3.0.txt','THIRD_PARTY_NOTICES.md','QRCode-MIT.txt','SOURCE_CODE.txt'])if(!fs.existsSync(path.join(root,'Resources/licenses',license)))throw Error('Missing license: '+license);
const sourceNotice=fs.readFileSync(path.join(root,'Resources/licenses/SOURCE_CODE.txt'),'utf8');
if(!sourceNotice.includes(metadata.zlplayBuild.sourceCommit)||!sourceNotice.includes(head))throw Error('License source reference mismatch.');
const files=execFileSync('git',['ls-files','-z','--','app','desktop'],{cwd:project,encoding:'utf8'}).split('\0').filter(Boolean);
for(const file of files){
  // electron-builder excludes explicit extraResources from app.asar.
  const packaged=file.startsWith('desktop/macos-locales/')?fs.readFileSync(path.join(root,'Resources',file.slice('desktop/macos-locales/'.length))):file==='desktop/zlplay-document.icns'?fs.readFileSync(path.join(root,'Resources/zlplay-document.icns')):asar.extractFile(archive,file);
  if(!packaged.equals(fs.readFileSync(path.join(project,file))))throw Error('Packaged source mismatch: '+file);
}
console.log(JSON.stringify({version:metadata.version,arch,minimumMacOS:'15.0',sourceCommit:head,sourceFilesVerified:files.length,offlineModel:'verified',signatureIntegrity:'verified',runtimeTested:arch===process.arch?'see test report':false},null,2));
