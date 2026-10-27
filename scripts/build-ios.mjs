import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawnSync} from 'node:child_process';
const root=fileURLToPath(new URL('../',import.meta.url));
if(process.platform!=='darwin')throw Error('Build iOS on a Mac with full Xcode installed.');
const xcode=process.env.DEVELOPER_DIR||'/Applications/Xcode.app/Contents/Developer';
if(!fs.existsSync(path.join(xcode,'usr/bin/xcodebuild')))throw Error('Full Xcode is missing. Install the compatible official Universal Xcode and open it once to complete setup/license.');
const device=process.argv.includes('--device'),sdk=device?'iphoneos':'iphonesimulator';
const dir=path.join(root,'.cache',`ios-${sdk}`),env={...process.env,DEVELOPER_DIR:xcode};
function run(exe,args){const r=spawnSync(exe,args,{cwd:root,env,stdio:'inherit'});if(r.error)throw r.error;if(r.status)throw Error(`${exe} failed (${r.status})`);}
run('xcrun',['--sdk',sdk,'--show-sdk-path']);
const cmake=['-S','ios','-B',dir,'-G','Xcode','-DCMAKE_SYSTEM_NAME=iOS','-DCMAKE_XCODE_GENERATE_SCHEME=ON','-DCMAKE_OSX_DEPLOYMENT_TARGET=15.0',`-DCMAKE_OSX_SYSROOT=${sdk}`,`-DCMAKE_OSX_ARCHITECTURES=${device?'arm64':process.arch==='arm64'?'arm64':'x86_64'}`];
if(process.env.ZLPLAY_JUCE_SOURCE)cmake.push(`-DZLPLAY_JUCE_SOURCE=${process.env.ZLPLAY_JUCE_SOURCE}`);
run(process.env.CMAKE||'cmake',cmake);
if(process.argv.includes('--configure-only')){console.log('Xcode project: '+path.join(dir,'ZLPlayMobile.xcodeproj'));process.exit(0);}
// A simulator build needs no signing identity. A device archive requires the user's team.
const args=['-project',path.join(dir,'ZLPlayMobile.xcodeproj'),'-scheme','ZLPlayMobile','-configuration','Debug','-sdk',sdk,'-derivedDataPath',path.join(dir,'DerivedData')];
if(device){if(!process.env.ZLPLAY_APPLE_TEAM)throw Error('Set ZLPLAY_APPLE_TEAM to your authorized Apple development team for device signing.');args.push(`DEVELOPMENT_TEAM=${process.env.ZLPLAY_APPLE_TEAM}`);}else args.push('CODE_SIGNING_ALLOWED=NO');
run('xcodebuild',args.concat('build'));
