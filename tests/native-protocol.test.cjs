const {test}=require('node:test');
const assert=require('node:assert/strict');
const {spawn}=require('node:child_process');
const path=require('node:path');
const {NativeAudio}=require('../desktop/native-audio.cjs');
const {nativeExecutable}=require('../desktop/runtime-paths.cjs');
const executable=nativeExecutable(path.resolve('native/bin'));

// No audio files or devices: isolate startup/message dispatch from decoding,
// rendering and hardware. A nil NSApp used to leave every request pending.
test('motor responde a comandos sem arquivos ou dispositivo de audio',{timeout:15000},async()=>{
  const engine=new NativeAudio(executable);
  try {
    const features=await engine.request({op:'cpuFeatures'},5000);
    assert.equal(typeof features.avx2,'boolean');
    const [first,second]=await Promise.all([
      engine.request({op:'status'},5000),
      engine.request({op:'status'},5000)
    ]);
    assert.equal(first.tracks,0);
    assert.equal(second.playing,false);
    await assert.rejects(engine.request({op:'unknownProtocolTestCommand'},5000),/Comando de áudio desconhecido/);
    assert.equal((await engine.request({op:'status'},5000)).tracks,0);
  } finally {engine.close();}
});

test('motor entrega resposta pendente e encerra ao fechar stdin',{timeout:10000},async()=>{
  const child=spawn(executable,[],{stdio:['pipe','pipe','pipe'],windowsHide:true});
  let stdout='',stderr='';
  child.stdout.on('data',data=>{stdout+=data;});
  child.stderr.on('data',data=>{stderr+=data;});
  const finished=new Promise((resolve,reject)=>{
    const timer=setTimeout(()=>{child.kill();reject(new Error(`Motor nao encerrou ao receber EOF. ${stderr}`));},5000);
    child.once('error',error=>{clearTimeout(timer);reject(error);});
    child.once('close',(code,signal)=>{clearTimeout(timer);resolve({code,signal});});
  });
  child.stdin.on('error',()=>{}); // Exit details are reported by finished.
  child.stdin.end(JSON.stringify({id:42,op:'cpuFeatures'})+'\n');
  try {
    const result=await finished;
    assert.equal(result.code,0,stderr);
    assert.equal(result.signal,null,stderr);
    const replies=stdout.trim().split(/\r?\n/).filter(Boolean).map(line=>JSON.parse(line));
    assert.equal(replies.length,1);
    assert.equal(replies[0].id,42);
    assert.equal(replies[0].ok,true);
    assert.equal(typeof replies[0].result.avx2,'boolean');
  } finally {if(child.exitCode===null)child.kill();}
});
