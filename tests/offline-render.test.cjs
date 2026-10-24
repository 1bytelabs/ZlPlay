const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs/promises');
const path=require('node:path');
const {setTimeout:delay}=require('node:timers/promises');
const {NativeAudio}=require('../desktop/native-audio.cjs');
const {nativeExecutable}=require('../desktop/runtime-paths.cjs');

test('render offline nao antecipa a fila ou rampas entre comandos',{timeout:30000},async()=>{
  const rate=44100,frames=rate*4,value=4096;
  const bytes=Buffer.alloc(44+frames*4);
  bytes.write('RIFF');bytes.writeUInt32LE(bytes.length-8,4);bytes.write('WAVEfmt ',8);
  bytes.writeUInt32LE(16,16);bytes.writeUInt16LE(1,20);bytes.writeUInt16LE(2,22);
  bytes.writeUInt32LE(rate,24);bytes.writeUInt32LE(rate*4,28);bytes.writeUInt16LE(4,32);
  bytes.writeUInt16LE(16,34);bytes.write('data',36);bytes.writeUInt32LE(frames*4,40);
  for(let offset=44;offset<bytes.length;offset+=2)bytes.writeInt16LE(value,offset);
  await fs.mkdir('tools/native-tests',{recursive:true});
  const file=path.resolve('tools/native-tests/offline-clock-constant.wav');
  await fs.writeFile(file,bytes);
  const engine=new NativeAudio(nativeExecutable(path.resolve('native/bin')));
  try{
    const loaded=await engine.request({op:'load',offline:true,sampleRate:rate,duration:4,
      tracks:[{gain:1,pan:0,pitch:0,items:[{path:file,position:0,offset:0,length:4}]}]});
    await delay(80);
    assert.equal((await engine.request({op:'status'})).queuedFrames,loaded.queuedFrames,
      'a thread de tempo real nao deve preencher a fila offline enquanto o cliente espera');
    let from=1;
    // Different gaps deliberately let the background worker run. An eager
    // producer used to consume part of the 1.5 s ramp during these gaps.
    for(const gap of [0,10,40,80,10,0,80,40]){
      const target=1-from;
      const mix={op:'mix',master:1,mix:[{gain:target,pan:0,pitch:0}]};
      await engine.request({...mix,fadeSeconds:1.5});
      await delay(gap);
      await engine.request(mix);
      await delay(gap);
      const rendered=await engine.request({op:'renderTest',frames:88200,position:0,capture:true});
      for(const frame of [0,16537,33074,66149,80000]){
        const expected=from+(target-from)*Math.min(1,(frame+1)/66150);
        assert.ok(Math.abs(rendered.samples[frame*2]/(value/32768)-expected)<.00003,
          `rampa deve seguir samples, nao o intervalo de ${gap} ms: frame ${frame}, target ${target}`);
      }
      from=target;
    }
  }finally{engine.close();}
});
