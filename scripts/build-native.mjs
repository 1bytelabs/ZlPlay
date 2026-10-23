import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
const root=fileURLToPath(new URL('../',import.meta.url)),cmake=process.env.CMAKE||'cmake';
const args=['-S','native','-B','native/build','-DCMAKE_BUILD_TYPE=Release'];
if(process.env.JUCE_SOURCE_DIR)args.push('-DFETCHCONTENT_SOURCE_DIR_JUCE='+path.resolve(process.env.JUCE_SOURCE_DIR));
for(const command of [args,['--build','native/build','--config','Release','--parallel','4']]){const result=spawnSync(cmake,command,{cwd:root,stdio:'inherit'});if(result.error){console.error('CMake unavailable. Install CMake and the platform C++ build tools.');process.exit(1);}if(result.status!==0)process.exit(result.status||1);}
