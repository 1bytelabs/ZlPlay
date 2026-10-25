const {test}=require('node:test'),assert=require('node:assert/strict');
test('RPP reconhece bemóis e sustenidos sem privilegiar a palavra Original',async()=>{
 const {rppKey}=await import('../app/tonal-versions.js');
 assert.equal(rppKey('É Ele - Original (Bb).rpp'),'A#');assert.equal(rppKey('É Ele (C).rpp'),'C');assert.equal(rppKey('Pasta/Música (Db).RPP'),'C#');assert.equal(rppKey('Original.rpp'),null);
});
test('Tap reinicia destaque inclusive em acionamentos rápidos mantendo BPM',async()=>{
 const {installExecutionTempo}=await import('../app/execution-tempo.js');class Player{};for(const n of ['stop','pause','saveProjectFile','finishLoadedProject','loadProjectManifest','projectPayload','nativeMix','applyManualBpm','restoreProjectTempo'])Player.prototype[n]=()=>{};
 installExecutionTempo(Player);let flashes=0,cancels=0,now=0,last;
 const old=global.performance;global.performance={now:()=>now};global.getComputedStyle=()=>({backgroundColor:'black',color:'white'});
 const s=new Player();s.tapTimes=[];s.$=()=>({animate:()=>{flashes++;return {cancel:()=>cancels++}}});s.updateTapTempoUi=()=>{};s.applyManualBpm=v=>last=v;
 try{for(const t of [0,500,1000,1010]){now=t;s.tapTempo();}assert.equal(flashes,4);assert.equal(cancels,3);assert.equal(last,Math.round(60000/(1010/3)));}finally{global.performance=old;delete global.getComputedStyle;}
});
test('RPP único inicia Original e Tocar em no tom do nome, sem pedir confirmação',async()=>{
 const {installTonalVersions}=await import('../app/tonal-versions.js');const oldWindow=global.window;global.window={};
 const s={tracks:[],currentProjectFiles:[{name:'É Ele - Original (Bb).rpp'}],tempoSource:'rpp',projectKey:'A#',liveTranspose:0,tonalVersions:[],prepareNative:async()=>{},nativeSync(){},pitchEligible:()=>false};
 for(const n of ['load','loadProjectManifest','finishLoadedProject','projectPayload','drawKeyControl','saveProjectFile'])s[n]=()=>{};
 const option={parentElement:{firstChild:{}},disabled:false};const box={querySelector:sel=>sel==='[data-project-key]'?option:sel==='[data-live-key]'?{value:''}:null,append(){}};
 global.document={createElement:()=>({dataset:{},append(){},setAttribute(){}})};global.Option=class{constructor(text,value){this.text=text;this.value=value;}};s.$=()=>box;
 try{installTonalVersions(s);await s.ensureOriginalKey();assert.equal(s.projectKey,'A#');assert.equal(s.liveTranspose,0);assert.equal(s._originalConfirmed,true);}finally{global.window=oldWindow;delete global.document;delete global.Option;}
});
