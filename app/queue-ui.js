export function installQueueUi(Player){
 const p=Player.prototype,old={};
 for(const k of ['projectPayload','loadProjectManifest','finishLoadedProject','compareMusical','sortGroupNames','makeTrackStrip','makeBusStrip','renameGroup','drawTracks','drawSections'])old[k]=p[k];
 p.drawSections=function(...args){const label=this.host.querySelector('[data-zoom-label]')||document.createElement('span');label.dataset.zoomLabel='';label.className='zl-wave-zoom';label.textContent=Math.round((this.timelineZoom||1)*100)+'%';const result=old.drawSections.apply(this,args);this.$('.vs-regions-head')?.append(label);return result;};
 p.drawTracks=function(...args){const result=old.drawTracks.apply(this,args);this._masterSizeObserver?.disconnect();const bank=this.$('.vs-special-bank'),master=this.$('.vs-master-strip');if(bank&&master){const sync=()=>{const height=bank.getBoundingClientRect().height;if(height>0&&Math.abs(parseFloat(master.style.height||'0')-height)>.1)master.style.height=height+'px';};this._masterSizeObserver=new ResizeObserver(()=>requestAnimationFrame(sync));this._masterSizeObserver.observe(bank);requestAnimationFrame(sync);}return result;};
 p.projectPayload=function(){const data=old.projectPayload.call(this);data.settings.groupNamingVersion=2;data.settings.mixerOrder={tracks:this.trackOrder||[],groups:this.groupOrder||[]};return data;};
 p.loadProjectManifest=async function(files,manifest,...args){
  if(!manifest.settings?.groupNamingVersion){manifest=JSON.parse(JSON.stringify(manifest));for(const t of manifest.tracks||[])if(['Outros','GRUPO'].includes(t.group))t.group='VS';for(const key of ['groupStates','groupPans'])if(manifest.mixer?.[key])manifest.mixer[key]=manifest.mixer[key].filter(([name])=>!['Outros','GRUPO'].includes(name));}
  this._mixerOrder=manifest.settings?.mixerOrder;try{return await old.loadProjectManifest.call(this,files,manifest,...args);}finally{this._mixerOrder=null;}};
 p.finishLoadedProject=function(...args){this.trackOrder=[...(this._mixerOrder?.tracks||[])];this.groupOrder=[...(this._mixerOrder?.groups||[])];return old.finishLoadedProject.apply(this,args);};
 const rank=(names,a,b,fallback)=>{const ai=names.indexOf(a),bi=names.indexOf(b);return ai<0&&bi<0?fallback():ai<0?1:bi<0?-1:ai-bi;};
 p.compareMusical=function(a,b){return rank(this.trackOrder||[],a.name,b.name,()=>old.compareMusical.call(this,a,b));};
 p.sortGroupNames=function(names,tracks=[]){const defaults=old.sortGroupNames.call(this,names,tracks);return defaults.sort((a,b)=>rank(this.groupOrder||[],a,b,()=>0));};
 p.reorderMixer=function(kind,source,target,after=false){
  if(source===target)return false;
  let names;
  if(kind==='track'){const a=this.tracks.find(t=>t.name===source),b=this.tracks.find(t=>t.name===target);if(!a||!b||this.isSpecialTrack(a)||this.isSpecialTrack(b)||(a.group||'VS')!==(b.group||'VS'))return false;names=this.tracks.filter(t=>!this.isSpecialTrack(t)).sort((a,b)=>this.compareMusical(a,b)).map(t=>t.name);}
  else if(kind==='group'){names=this.allGroupNames();if(!names.includes(source)||!names.includes(target))return false;}
  else return false;
  this.beginEdit?.();names=names.filter(n=>n!==source);names.splice(names.indexOf(target)+(after?1:0),0,source);this[kind==='track'?'trackOrder':'groupOrder']=names;
  const scroll=this.$('.vs-mixer-scroll')?.scrollLeft||0;this.drawTracks();const el=this.$('.vs-mixer-scroll');if(el)el.scrollLeft=scroll;this.save();this.commitEdit?.();return true;
 };
 function draggable(player,strip,kind,name){
  const handle=strip.querySelector('.vs-channel-name');if(!handle)return strip;handle.draggable=true;handle.tabIndex=0;handle.classList.add('zl-order-handle');handle.title='Arraste para ordenar · Alt + ←/→ para mover';handle.setAttribute('aria-label',name+' · ordenar');
  handle.addEventListener('dragstart',e=>{player._mixerDrag={kind,name};e.dataTransfer.effectAllowed='move';e.dataTransfer.setData('text/plain',name);});
  handle.addEventListener('dragend',()=>{player._mixerDrag=null;player.host.querySelectorAll('.zl-drop-before,.zl-drop-after').forEach(el=>el.classList.remove('zl-drop-before','zl-drop-after'));});
  strip.addEventListener('dragover',e=>{const drag=player._mixerDrag;if(!drag||drag.kind!==kind||drag.name===name)return;if(kind==='track'){const a=player.tracks.find(t=>t.name===drag.name),b=player.tracks.find(t=>t.name===name);if((a?.group||'VS')!==(b?.group||'VS'))return;}e.preventDefault();const after=e.clientX>strip.getBoundingClientRect().left+strip.offsetWidth/2;strip.classList.toggle('zl-drop-after',after);strip.classList.toggle('zl-drop-before',!after);const scroller=player.$('.vs-mixer-scroll'),rect=scroller.getBoundingClientRect();if(e.clientX>rect.right-55)scroller.scrollLeft+=24;else if(e.clientX<rect.left+55)scroller.scrollLeft-=24;});
  strip.addEventListener('dragleave',()=>strip.classList.remove('zl-drop-before','zl-drop-after'));
  strip.addEventListener('drop',e=>{const drag=player._mixerDrag;if(!drag)return;e.preventDefault();const after=strip.classList.contains('zl-drop-after');strip.classList.remove('zl-drop-before','zl-drop-after');player._mixerDrag=null;player.reorderMixer(kind,drag.name,name,after);});
  handle.addEventListener('keydown',e=>{if(!e.altKey||!['ArrowLeft','ArrowRight'].includes(e.key))return;e.preventDefault();const names=kind==='group'?player.allGroupNames():player.tracks.filter(t=>!player.isSpecialTrack(t)&&(t.group||'VS')===(player.tracks.find(t=>t.name===name)?.group||'VS')).sort((a,b)=>player.compareMusical(a,b)).map(t=>t.name);const direction=e.key==='ArrowRight'?1:-1,target=names[names.indexOf(name)+direction];if(target){player.reorderMixer(kind,name,target,direction>0);[...player.host.querySelectorAll('.zl-order-handle')].find(el=>el.textContent===name)?.focus();}});
  return strip;
 }
 p.makeTrackStrip=function(t){const strip=old.makeTrackStrip.call(this,t);return this.isSpecialTrack(t)?strip:draggable(this,strip,'track',t.name);};
 p.makeBusStrip=function(name,state,type){const strip=old.makeBusStrip.call(this,name,state,type);return type==='vs'?strip:draggable(this,strip,'group',name);};
 p.createGroup=function(oldName=null){
  if(document.querySelector('.zl-group-dialog'))return;
  const modal=document.createElement('div');modal.className='zl-audio-modal zl-group-dialog';modal.innerHTML='<form class="zl-audio-box zl-small-dialog"><header><strong>Criar grupo</strong><button type="button" data-cancel aria-label="Fechar">×</button></header><label>Nome do grupo<input data-group-name maxlength="80" required autocomplete="off"></label><p data-error role="alert"></p><footer><button type="button" data-cancel>Cancelar</button><button type="submit">Criar grupo</button></footer></form>';
  (document.fullscreenElement||document.body).append(modal);const input=modal.querySelector('input'),close=()=>{modal.remove();this.$('[data-add-group]')?.focus();};modal.querySelectorAll('[data-cancel]').forEach(b=>b.onclick=close);modal.onkeydown=e=>{if(e.key==='Escape')close();};
  if(oldName!==null){modal.querySelector('strong').textContent='Renomear grupo';modal.querySelector('[type=submit]').textContent='Salvar nome';input.value=oldName;}
  modal.querySelector('form').onsubmit=e=>{e.preventDefault();const name=input.value.trim();let error='';if(!name)error='Digite um nome para o grupo.';else if(/^(click|guia|guide|pad|vs|master)$/i.test(name))error='Este nome identifica um canal do sistema. Escolha outro nome.';else if(this.allGroupNames().some(g=>g!==oldName&&g.toLocaleLowerCase('pt-BR')===name.toLocaleLowerCase('pt-BR')))error='Já existe um grupo com esse nome.';if(error){modal.querySelector('[data-error]').textContent=error;input.focus();return;}
   this.beginEdit?.();if(oldName!==null){if(!this.groupStates.has(oldName)){close();return;}const previousOrder=this.allGroupNames(),state=this.groupStates.get(oldName);this.groupStates.delete(oldName);this.groupStates.set(name,state);if(this.groupPans?.has(oldName)){const pan=this.groupPans.get(oldName);this.groupPans.delete(oldName);this.groupPans.set(name,pan);}for(const t of this.tracks)if(t.group===oldName)t.group=name;this.groupOrder=previousOrder.map(n=>n===oldName?name:n);if(this.channelEffects?.['group:'+oldName]){const fx=this.channelEffects['group:'+oldName];delete this.channelEffects['group:'+oldName];this.channelEffects['group:'+name]=fx;}}
   else{this.ensureGroupState(name);this.groupOrder=this.allGroupNames();}
   this.drawTracks();this.updateGains();this.updatePans();this.nativeSync();this.save();this.commitEdit?.();close();this.status(oldName!==null?`Grupo ${oldName} renomeado para ${name}.`:`Grupo ${name} criado. Use o menu ⋮ das tracks para movê-las.`);
  };input.focus();if(oldName!==null)input.select();
 };
 p.renameGroup=function(name){if(name&&name!=='VS')this.createGroup(name);};
 p.openChoiceDialog=function(kind,pick){
  document.querySelector('.zl-choice-dialog')?.remove();const modal=document.createElement('div');modal.className='zl-audio-modal zl-choice-dialog';
  const isSave=kind==='save',project=this.tracks.length>0,library=!!window.__ZL_LIBRARY_SCAN__?.songs?.length;
  const choices=isSave?[
   ['save-project','Salvar projeto',project?'Guarda as alterações da música: tom, BPM, mix, grupos e efeitos.':'Abra ou crie um projeto para salvar.',!project],
   ['save-library','Salvar repertório',library?'Guarda a lista, a ordem das músicas e os projetos associados. Não salva as alterações da música.':'Abra ou crie um repertório para salvar.',!library]
  ]:kind==='project'?[
   ['project','Abrir projeto salvo','Selecione um arquivo .zlplay ou um projeto REAPER .rpp.'],
   ['folder','Criar novo projeto','Importe uma pasta com os áudios da música para editar e salvar como .zlplay.']
  ]:[
   ['library','Abrir repertório salvo','Selecione um arquivo .zlset ou .zlplaylibrary.'],
   ['library-folder','Criar novo repertório','Selecione a pasta que contém as pastas das músicas.']
  ];
  modal.innerHTML='<section class="zl-audio-box zl-small-dialog"><header><strong></strong><button data-close aria-label="Fechar">×</button></header><div class="zl-choice-list"></div></section>';
  modal.querySelector('strong').textContent=isSave?'Salvar':kind==='project'?'Abrir projeto':'Repertório';const list=modal.querySelector('.zl-choice-list');
  const opener=document.activeElement,close=()=>{modal.remove();opener?.focus();};
  if(kind==='save')choices.splice(1,0,['save-project-as','Salvar como','Escolher outro nome e local para o projeto.',!this.tracks.length]);for(const [mode,title,description,disabled] of choices){const button=document.createElement('button');button.type='button';button.dataset.choice=mode;button.disabled=!!disabled;const strong=document.createElement('strong'),detail=document.createElement('span');strong.textContent=title;detail.textContent=description;button.append(strong,detail);button.onclick=async()=>{close();if(mode==='save-project')await this.saveProjectFile();else if(mode==='save-project-as')await this.saveProjectAs();else if(mode==='save-library'){try{await window.__ZL_SAVE_LIBRARY__?.();}catch(e){this.status(e.message,true);}}else await pick(mode);};list.append(button);}
  modal.querySelector('[data-close]').onclick=close;modal.onkeydown=e=>{if(e.key==='Escape'){e.preventDefault();close();}if(e.key==='Tab'){const all=[...modal.querySelectorAll('button:not(:disabled)')],first=all[0],last=all.at(-1);if(e.shiftKey&&document.activeElement===first){e.preventDefault();last.focus();}else if(!e.shiftKey&&document.activeElement===last){e.preventDefault();first.focus();}}};
  (document.fullscreenElement||document.body).append(modal);modal.querySelector('button:not(:disabled)').focus();
 };
 p.installQueuedControls=function(pick){
  const save=this.$('[data-save-project]');save.textContent='Salvar';save.disabled=false;save.classList.add('zl-toolbar-save');save.onclick=()=>this.openChoiceDialog('save',pick);this.$('.vs-actions').append(save);const regions=this.$('[data-map-regions]');if(regions)this.$('.vs-head-actions').prepend(regions);const liveSave=document.createElement('button');liveSave.className='vs-mode zl-save-fullscreen';liveSave.type='button';liveSave.textContent='Salvar';liveSave.onclick=()=>this.openChoiceDialog('save',pick);this.$('.vs-library-navigation')?.append(liveSave);
  this.host.querySelector('[data-pick-zip]')?.remove();this.host.querySelector('[data-zip]')?.remove();
  const label=this.host.querySelector('[data-zoom-label]'),head=this.$('.vs-regions-head');if(label&&head){label.classList.add('zl-wave-zoom');head.append(label);this.host.querySelector('.vs-zoom-controls')?.remove();}
 };
}


