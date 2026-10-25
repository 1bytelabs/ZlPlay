const copy=x=>JSON.parse(JSON.stringify(x));
export function installExecutionTempo(Player){
 const p=Player.prototype,old={};for(const name of ['stop','pause','saveProjectFile','finishLoadedProject','loadProjectManifest','projectPayload','nativeMix','applyManualBpm','restoreProjectTempo'])old[name]=p[name];
 for(const name of ['stop','pause'])p[name]=function(...args){const result=old[name].apply(this,args);this.refreshExecutionBpm();return result;};
 p.saveProjectFile=async function(...args){const previous=this.freeTempoOverride;this.freeTempoOverride=true;try{return await old.saveProjectFile.apply(this,args);}finally{this.freeTempoOverride=previous;}};
 p.loadProjectManifest=async function(files,manifest,...args){this._executionManifest=manifest;try{return await old.loadProjectManifest.call(this,files,manifest,...args)}finally{this._executionManifest=null;}};
 p.finishLoadedProject=function(...args){
  const manifest=this._executionManifest;this.executionFactor=1;this.channelEffects=copy(manifest?.settings?.channelEffects||{});
  for(const t of this.tracks){const saved=manifest?.tracks?.find(s=>s.name===t.name);t.pitchProfile=saved?.pitchProfile||(/\b(vocal|vocals|vox|bgv|bgvs|vozes|voz)\b/i.test(t.name)&&!this.isGuideTrack(t)?'voice':'instrument');t.items.forEach((it,i)=>it.playbackRate=Number(saved?.items?.[i]?.playbackRate)||it.playbackRate||1);}
  if(manifest?.settings?.originalTempo){this.tempo=copy(manifest.settings.originalTempo);this.projectTempo=copy(this.tempo);}
  const result=old.finishLoadedProject.apply(this,args);
  this._executionBase={duration:this.duration,tempo:copy(this.projectTempo||this.tempo)};
  const factor=Number(manifest?.settings?.executionFactor)||1;if(factor!==1)this.scaleExecution(factor);
  this._tempoDefinitionEditable=manifest?.settings?.tempoDefinitionEditable??(this.tempoSource==='editable'||this.tempoSource!=='rpp'&&!this.currentProjectFiles.some(f=>/\.rpp$/i.test(f.name)));this.standaloneBpm=this.projectBpm();this.refreshExecutionBpm();return result;
 };
 p.scaleExecution=function(factor){
  const previous=this.executionFactor||1,ratio=previous/factor;
  const scaleRef=r=>{if(r&&Number.isFinite(r.start)){r.start*=ratio;r.end*=ratio;}};
  this.duration*=ratio;
  for(const t of this.tracks)for(const it of t.items){it.position=(it.position||0)*ratio;it.length=(it.length??Math.max(0,(it.buffer.duration-(it.offset||0))/(it.playbackRate||1)))*ratio;it.playbackRate=(it.playbackRate||1)/ratio;}
  this.sections=this.sections.map(s=>({...s,start:s.start*ratio,end:s.end*ratio}));
  this.rawSections={regions:this.sections.map(s=>({...s})),markers:[]};
  for(const r of this.arrangement?.rules||[]){scaleRef(r.from);scaleRef(r.to);}
  this.regionMarks?.forEach(r=>r.start*=ratio);this.offset=(this.offset||0)*ratio;this.pendingSection=null;this.currentLoop=false;this.currentLoopSection=null;this.loop=false;this.loopSection=this.sections[0]||null;
  this.executionFactor=factor;const source=this._executionBase?.tempo||this.tempo;
  this.tempo={...copy(source),bpm:source.bpm*factor,points:(source.points||[{time:0,bpm:source.bpm}]).map(t=>({...t,time:t.time/factor,bpm:t.bpm*factor}))};
  this.projectTempo=copy(this.tempo);this.savedProjectTempo=copy(this.tempo);this._nativeSignature=null;this._nativeConfigured=false;
  this.drawSections();this.refreshProjectMeta();this.refreshArrangementUi?.();this.$('[data-progress]').max=this.duration;
 };
 p.setExecutionBpm=function(value){const project=this.projectId;const run=async()=>{
  if(project!==this.projectId)return;
  if(!this.tracks.length)return this.status('Abra uma música para aplicar o BPM.',true);
  const bpm=Math.max(20,Math.min(400,Math.round(Number(value)||120))),base=this._executionBase?.tempo?.bpm||this.projectBpm(),factor=bpm/base;
  if(factor<.5||factor>2)return this.status('Use um andamento entre metade e o dobro do original.',true);
  const was=this.playing,position=this.position();this.halt();this.offset=position;this.scaleExecution(factor);this.standaloneBpm=bpm;this.refreshExecutionBpm();this.save();await this.prepareNative();if(was)await this.play(true);else if(this.standaloneClickActive)await this.restartStandaloneClick();this.status(`BPM da música: ${bpm}. Salve o projeto para guardar o andamento.`);
 };return this._tempoChange=(this._tempoChange||Promise.resolve()).catch(()=>{}).then(run);};
 p.tapTempo=function(){const now=performance.now();this._tapFeedback?.cancel();this._tapFeedback=this.$('[data-tap-tempo]')?.animate?.([{backgroundColor:'#8b5cf6',borderColor:'#d4bdff',color:'#ffffff',boxShadow:'0 0 0 3px #a78bfa55'},{}],{duration:160,easing:'ease-out'});if(!this.tapTimes.length||now-this.tapTimes.at(-1)>2200)this.tapTimes=[];this.tapTimes.push(now);if(this.tapTimes.length>6)this.tapTimes.shift();if(this.tapTimes.length>=2){const average=(this.tapTimes.at(-1)-this.tapTimes[0])/(this.tapTimes.length-1);this.applyManualBpm(Math.round(60000/average));}else this.updateTapTempoUi();};
 p.refreshExecutionBpm=function(){const input=this.$('[data-bpm-input]');if(input)input.value=String(Math.round(this.playing?this.projectBpm():this.standaloneBpm||this.projectBpm()));this.updateTapTempoUi();};
 p.applyManualBpm=function(value){this.standaloneBpm=Math.max(20,Math.min(400,Math.round(Number(value)||120)));this.$('[data-bpm-input]').value=this.standaloneBpm;this.updateTapTempoUi();if(this.tracks.length&&this._tempoDefinitionEditable&&(this.executionFactor||1)===1){const bpm=this.standaloneBpm;this.tempo={...this.tempo,bpm,points:[{time:0,bpm}]};this.projectTempo=copy(this.tempo);this.savedProjectTempo=copy(this.tempo);this._executionBase={duration:this.duration*(this.executionFactor||1),tempo:copy(this.tempo)};this.nativeSync();this.drawSections();this.save();}else if(this.playing)return this.status('Clique em Aplicar para alterar o andamento da música.');if(this.standaloneClickActive)this.restartStandaloneClick();};
 p.restoreProjectTempo=function(){if(this._executionBase){this.tempo=copy(this.projectTempo);this.$('[data-bpm-input]').value=String(Math.round(this.tempo.bpm));return this.tempo.bpm;}return old.restoreProjectTempo.apply(this,arguments);};
 p.nativeMix=function(){const data=old.nativeMix.call(this),factor=this.executionFactor||1;data.channelEffects=this.channelEffects||{};data.mix.forEach((t,i)=>{t.pitch-=12*Math.log2(factor);t.pitchProfile=this.tracks[i].pitchProfile||'instrument';t.bus=this.isSpecialTrack(this.tracks[i])?'':this.tracks[i].group||'VS';t.special=this.isClickTrack(this.tracks[i])?'click':this.isGuideTrack(this.tracks[i])?'guide':'';t.effectId='track:'+this.tracks[i].name;});return data;};
 p.projectPayload=function(){const data=old.projectPayload.call(this),factor=this.executionFactor||1;data.appVersion='2.7.0';data.settings.tempoDefinitionEditable=!!this._tempoDefinitionEditable;data.settings.channelEffects=copy(this.channelEffects||{});data.settings.executionFactor=factor;data.settings.originalTempo=copy(this._executionBase?.tempo||this.projectTempo||this.tempo);data.song.bpm=data.settings.originalTempo.bpm;data.song.duration*=factor;
  data.tracks.forEach((t,i)=>{t.pitchProfile=this.tracks[i].pitchProfile||'instrument';t.items.forEach((it,j)=>{it.position*=factor;if(it.length!=null)it.length*=factor;it.playbackRate=(this.tracks[i].items[j].playbackRate||1)/factor;});});
  data.regions.forEach(r=>{r.start*=factor;r.end*=factor;});for(const r of data.arrangement?.rules||[])for(const ref of [r.from,r.to]){ref.start*=factor;ref.end*=factor;}return data;
 };
}
