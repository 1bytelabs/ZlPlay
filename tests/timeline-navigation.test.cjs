const {test}=require('node:test');const assert=require('node:assert/strict');
const model=import('../app/timeline-model.mjs');
test('playback pages zoomed timeline forward and back after a loop or seek',async()=>{
 const {playbackScroll:f}=await model;
 assert.equal(f(10,100,4000,1000,0),0);
 assert.equal(f(25,100,4000,1000,0),800);
 assert.equal(f(5,100,4000,1000,2000),0);
 assert.equal(f(100,100,4000,1000,0),3000);
 assert.equal(f(25,100,1000,1000,0),0);
 assert.equal(f(25,0,4000,1000,0),0);
});
test('normal resizing keeps neighbors connected; Shift leaves them in place and opens a gap',async()=>{
 const {regionResizePlan:f}=await model;
 const a={start:0,end:10},b={start:10,end:20},c={start:20,end:30},regions=[a,b,c];
 const linked=f(regions,b,12,18);assert.deepEqual(linked.map(x=>[x.start,x.end]),[[12,18],[0,12],[18,30]]);
 const independent=f(regions,b,12,18,false);assert.deepEqual(independent,[{region:b,start:12,end:18}]);
 assert.deepEqual(regions,[{start:0,end:10},{start:10,end:20},{start:20,end:30}]);
 assert.equal(f(regions,b,9,18,false),null);assert.equal(f(regions,b,12,21,false),null);
 assert.equal(f(regions,b,0,18),null);assert.equal(f(regions,b,12,30),null);
});
test('region separators include gaps and same-colour neighbors without duplicate edges',async()=>{
 const {regionBoundaries:f}=await model;
 assert.deepEqual(f([{start:0,end:10},{start:10,end:20},{start:25,end:30}],30),[0,10,20,25,30]);
 assert.deepEqual(f([],0),[]);
});
test('new region instructions and validation are translated',async()=>{
 const {translate}=await import('../app/locales.mjs');
 for(const key of ['Arraste na waveform para selecionar. Shift + arrastar uma borda ajusta apenas esta região.','O ajuste não pode apagar ou sobrepor a região vizinha.'])for(const lang of ['en','es'])assert.notEqual(translate(key,lang),key);
});
