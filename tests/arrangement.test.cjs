const {test}=require('node:test');const assert=require('node:assert/strict');
test('arranjo: ordem de criação, consumo ao vivo, ensaio e reinício sem alterar o salvo',async()=>{const {Arrangement}=await import('../app/arrangement.mjs');const region=(start)=>({name:String(start),start,end:start+1});const data={rules:[{id:'1',from:region(8),to:region(12)},{id:'2',from:region(12),to:region(0)},{id:'3',from:region(0),to:region(4)}]};const saved=JSON.stringify(data);const a=new Arrangement(data);assert.equal(a.next.id,'1');assert.equal(a.complete('3'),false);assert.equal(a.rules.length,3);a.complete('1');assert.equal(a.next.id,'2');assert.equal(a.rules.length,3);assert.equal(JSON.stringify(data),saved);a.seek(0);assert.equal(a.next.id,'2');a.setMode('rehearsal',12);assert.equal(a.next.id,'2');a.complete('2');a.complete('3');assert.equal(a.next,null);assert.equal(a.rules.length,3);a.setMode('live',0);assert.equal(a.next.id,'1');a.complete('1');a.restart();assert.equal(a.next.id,'1');const b=new Arrangement(JSON.parse(saved));assert.equal(b.rules.length,3);});

test('arrangement snapshots stay independent of edits and live playback', async () => {
 const {Arrangement}=await import('../app/arrangement.mjs');
 const from={name:'Intro',start:0,end:2},to={name:'Final',start:2,end:4};
 const arrangement=new Arrangement({rules:[{id:'jump',from,to}]});
 const snapshot=arrangement.toJSON();
 arrangement.rules[0].from.name='Edited';
 arrangement.complete('jump');
 assert.deepEqual(snapshot.rules,[{id:'jump',from,to}]);
 assert.equal(arrangement.toJSON().rules.length,1);
});

test('accepting editor changes restores shared controls before closing', async () => {
 const {editorExit}=await import('../app/editor-session.js');
 const previousDocument=global.document;
 const modal=new EventTarget(),calls=[];
 const close={onclick:()=>calls.push('close'),before(){}};
 const accept={dataset:{}};
 const actions={append(){}};
 modal.querySelector=selector=>selector==='[data-close]'?close:null;
 global.document={createElement:tag=>tag==='button'?accept:actions};
 try{
  editorExit(modal,{},null);
  modal.addEventListener('zl-editor-exit',()=>calls.push('restore-controls'));
  accept.onclick();
  assert.deepEqual(calls,['restore-controls','close']);
 }finally{
  if(previousDocument===undefined)delete global.document;
  else global.document=previousDocument;
 }
});

test('moving the playhead selects the later jump without consuming earlier ones',async()=>{
 const {Arrangement}=await import('../app/arrangement.mjs');
 const region=(start,end)=>({name:String(start),start,end});
 const a=new Arrangement({mode:'rehearsal',rules:[
  {id:'intro',from:region(0,4),to:region(8,12)},
  {id:'pre',from:region(12,16),to:region(32,36)}
 ]});
 a.seek(10);
 assert.equal(a.next.id,'pre');
 assert.equal(a.complete('pre'),true);
 a.seek(13);
 assert.equal(a.next.id,'pre');
 a.seek(0);
 assert.equal(a.next.id,'intro');
 a.mode='live';
 a.seek(12);a.complete('pre');a.seek(0);
 assert.deepEqual(a.toJSON().rules.map(r=>r.id),['intro','pre']);
 assert.equal(a.next.id,'intro');a.complete('intro');assert.equal(a.next,null);
});
