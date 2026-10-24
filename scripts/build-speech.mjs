import {macosTarget} from './macos-target.mjs';
import {spawnSync} from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
const target=macosTarget();
const source=path.resolve('.cache/whisper.cpp'),build=path.join(source,target?'build-macos-'+target.arch:'build'),cmake=process.env.CMAKE||'cmake',revision='9386f239401074690479731c1e41683fbbeac557';
function run(exe,args,cwd=process.cwd()){const r=spawnSync(exe,args,{cwd,stdio:'inherit'});if(r.error||r.status!==0)throw Error('Command failed: '+exe);}
if(!fs.existsSync(path.join(source,'.git'))){fs.mkdirSync(source,{recursive:true});run('git',['init',source]);run('git',['remote','add','origin','https://github.com/ggml-org/whisper.cpp.git'],source);run('git',['fetch','--depth','1','origin',revision],source);run('git',['checkout','--detach','FETCH_HEAD'],source);}
const head=spawnSync('git',['rev-parse','HEAD'],{cwd:source,encoding:'utf8'});if(head.status!==0||head.stdout.trim()!==revision)throw Error('Unexpected whisper.cpp revision. Use a clean .cache/whisper.cpp directory.');
const common=path.join(source,'examples/common.cpp');let text=fs.readFileSync(common,'utf8');if(!text.includes('#include <algorithm>'))fs.writeFileSync(common,'#include <algorithm>\n'+text);
run(cmake,['-S',source,'-B',build,'-DCMAKE_BUILD_TYPE=Release','-DBUILD_SHARED_LIBS=OFF','-DGGML_STATIC=ON','-DGGML_OPENMP=OFF','-DGGML_NATIVE=OFF','-DGGML_METAL=OFF','-DGGML_BLAS=OFF','-DGGML_AVX=OFF','-DGGML_AVX2=OFF','-DGGML_FMA=OFF','-DGGML_F16C=OFF','-DGGML_BMI2=OFF','-DWHISPER_BUILD_TESTS=OFF',...(target?.cmakeArgs||(process.platform==='darwin'?['-DCMAKE_OSX_DEPLOYMENT_TARGET=15.0']:[]))]);
run(cmake,['--build',build,'--config','Release','--target','whisper-cli','--parallel','4']);
const name=process.platform==='win32'?'whisper-cli.exe':'whisper-cli',from=[path.join(build,'bin/Release',name),path.join(build,'bin',name)].find(p=>fs.existsSync(p));if(!from)throw Error('whisper-cli output was not found.');const output=target?.speechDirectory||'native/speech';fs.mkdirSync(output,{recursive:true});const dest=path.join(output,name);fs.copyFileSync(from,dest);if(process.platform!=='win32')fs.chmodSync(dest,0o755);fs.copyFileSync(path.join(source,'LICENSE'),path.join(output,'WHISPER-LICENSE.txt'));
