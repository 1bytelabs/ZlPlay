import {spawnSync} from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
// All fixture audio is generated; no recordings or user projects are required.
fs.mkdirSync('tools/native-tests',{recursive:true});
const rate=44100,frames=rate*4,b=Buffer.alloc(44+frames*4);
b.write('RIFF');b.writeUInt32LE(b.length-8,4);b.write('WAVEfmt ',8);b.writeUInt32LE(16,16);b.writeUInt16LE(1,20);b.writeUInt16LE(2,22);b.writeUInt32LE(rate,24);b.writeUInt32LE(rate*4,28);b.writeUInt16LE(4,32);b.writeUInt16LE(16,34);b.write('data',36);b.writeUInt32LE(b.length-44,40);
for(let i=0;i<frames;i++){const value=Math.round(Math.sin(2*Math.PI*440*i/rate)*3276);b.writeInt16LE(value,44+i*4);b.writeInt16LE(value,46+i*4);}
fs.writeFileSync('tools/native-tests/loudness.wav',b);
const files=fs.readdirSync('tests').filter(f=>f.endsWith('.test.cjs')).map(f=>path.join('tests',f));
const result=spawnSync(process.execPath,['--test',...files],{stdio:'inherit'});process.exit(result.status??1);
