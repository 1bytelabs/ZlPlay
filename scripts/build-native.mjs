import {macosTarget} from './macos-target.mjs';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
const root=fileURLToPath(new URL('../',import.meta.url)),cmake=process.env.CMAKE||'cmake';
const target=macosTarget(),build=target?'.cache/native-macos-'+target.arch:'native/build';
const args=['-S','native','-B',build,'-DCMAKE_BUILD_TYPE=Release'];
if(target)args.push(...target.cmakeArgs,'-DZLPLAY_NATIVE_OUTPUT_DIR='+target.nativeDirectory);
else if(process.platform==='darwin')args.push('-DCMAKE_OSX_DEPLOYMENT_TARGET=15.0');
if(process.env.JUCE_SOURCE_DIR)args.push('-DFETCHCONTENT_SOURCE_DIR_JUCE='+path.resolve(process.env.JUCE_SOURCE_DIR));
for(const command of [args,['--build',build,'--config','Release','--parallel','4']]){const result=spawnSync(cmake,command,{cwd:root,stdio:'inherit'});if(result.error){console.error('CMake unavailable. Install CMake and the platform C++ build tools.');process.exit(1);}if(result.status!==0)process.exit(result.status||1);}
