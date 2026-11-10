import {editorExit} from './editor-session.js';
import {alignTracks} from './track-alignment.js';
import {cycleSelect} from './choice-controls.js';
import {historyButtons} from './edit-history.js';
import {openChannelEffects} from './eq-editor.js';
export function openTrackEditor(s){
 if(document.querySelector('.zl-track-editor,.zl-region-editor'))return;if(!s.tracks.length)return s.status('Abra uma música para editar tracks.',true);
 const priorPad={playing:s.padPlaying,note:s.padNote,project:s.projectId};s._trackEditing=true;s.padRequestId++;s.padPlaying=false;s.syncNativeAux({padEnabled:false,padImmediateStop:true}).catch(e=>s.status(e.message,true));s.updatePadUi();
 const session=s.captureEditSession();const modal=document.createElement('div');modal.className='zl-audio-modal zl-track-editor';modal.innerHTML='<section class="zl-track-box"><header><div><small>ARRANJO / EDIÇÃO</small><h2>Editor de tracks</h2></div><button data-close>Concluir</button></header><nav><button data-align>Alinhar pelo click</button><button data-select-all>Selecionar todas</button><button data-undo>Desfazer</button><button data-redo>Refazer</button><button data-snap aria-label="Ferradura magnética">∪ Grade ON</button><button data-zoom-out>−</button><button data-zoom-in>+</button><label><select data-editor-click-source><option value="native">Click ZLPlay</option><option value="project">Click do projeto</option></select></label></nav><p>Arraste o corpo para mover; as bordas para aparar. Ctrl+clique seleciona vários itens. A grade usa as batidas do projeto.</p><div class="zl-track-scroll"><div class="zl-track-content"></div></div><p data-message role="status"></p><footer><button data-editor-play>▶ Play</button><button data-editor-pause>❚❚ Pause</button><button data-editor-stop>■ Stop</button><output data-cursor></output><span>Recortes não alteram os arquivos. Salve o projeto para guardar.</span></footer></section>';s.host.append(modal);
 let zoom=1,selected=new Set(),snap=s.snapEnabled!==false,drag=false,closed=false,rangeTimer;const detail=new WeakMap();const content=modal.querySelector('.zl-track-content'),scroll=modal.querySelector('.zl-track-scroll');
 const all=()=>s.tracks.flatMap(t=>t.items.map(it=>({t,it}))),message=t=>modal.querySelector('[data-message]').textContent=t;
 const grid=t=>{if(!snap)return t;const point=[...(s.tempo.points||[{time:0,bpm:s.projectBpm()}])].reverse().find(p=>p.time<=t)||{time:0,bpm:s.projectBpm()};const step=60/point.bpm;return point.time+Math.round((t-point.time)/step)*step;};
 const tLabel=text=>window.__ZL_I18N__?.t(text)||text;
 let applying=false;
 const tools=modal.querySelector('nav');
 function tool(label,action,key){
  const button=document.createElement('button');
  button.type='button';
  button.textContent=label;
  if(key)button.setAttribute('aria-keyshortcuts',key);
  button.onclick=action;
  tools.append(button);
  return button;
 }
 const editable=()=>all().filter(({t,it})=>!s.isGuideTrack(t)&&selected.has(it));
 const clipLength=it=>it.length??(it.buffer.duration-(it.offset||0))/(it.playbackRate||1);
 async function changeClips(change){
  if(applying||closed)return;
  applying=true;
  const was=s.playing,at=s.position();
  s.beginEdit();
  s.halt();
  try{
   change();
   s.offset=at;
   s.drawTracks();
   await s.prepareNative();
   if(was)await s.play(true);
   s.save();
   s.commitEdit();
  }catch(error){message(error.message);}
  finally{applying=false;paint();}
 }
 const split=tool('Dividir no cursor',()=>{
  const at=s.position();
  const hits=editable().filter(({it})=>at>(it.position||0)+.005&&at<(it.position||0)+clipLength(it)-.005);
  if(!hits.length)return;
  changeClips(()=>{
   selected.clear();
   for(const {t,it} of hits){
    const left=at-(it.position||0);
    const right={...it,position:at,offset:(it.offset||0)+left*(it.playbackRate||1),length:clipLength(it)-left};
    it.length=left;
    t.items.splice(t.items.indexOf(it)+1,0,right);
    selected.add(right);
   }
  });
 },'S');
 const remove=tool('Excluir trecho',()=>{
  const hits=editable();
  if(applying||!hits.length||hits.length===all().length)return;
  const names=[...new Set(hits.map(({t})=>t.name))].join(', ');
  if(!confirm(tLabel('Excluir os trechos de áudio selecionados?')+'\n\n'+names))return;
  changeClips(()=>{
   for(const {t,it} of hits)t.items.splice(t.items.indexOf(it),1);
   s.tracks=s.tracks.filter(track=>track.items.length);
   selected.clear();
  });
 },'Delete');
 const mute=tool('Silenciar trecho',()=>{
  const hits=editable(),muted=hits.some(({it})=>!it.muted);
  if(hits.length)changeClips(()=>{for(const {it} of hits)it.muted=muted;});
 });
 const fx=tool('EQ/Ganho do trecho',()=>{
  const [target]=editable();
  if(!target||applying)return;
  const {t,it}=target;
  openChannelEffects.call(s,t.name,{
   effects:it.effects??{enabled:true},
   apply:effects=>{
    if(t.items.includes(it))changeClips(()=>{it.effects=effects;});
   }
  });
 });
 function shortcuts(event){
  if(event.altKey||event.ctrlKey||event.metaKey||event.shiftKey||
     event.target.closest('input,textarea,select,[contenteditable]')||
     document.querySelector('.zl-fx-modal,.zl-original-key,.zl-select-popup'))return;
  const button=event.key.toLowerCase()==='s'?split:event.key==='Delete'?remove:null;
  if(!button)return;
  event.preventDefault();
  event.stopImmediatePropagation();
  if(!event.repeat)button.click();
 }
 document.addEventListener('keydown',shortcuts,true);
 function trackControls(track){
  const panel=document.createElement('div');
  panel.className='zl-track-name';
  const title=document.createElement('b');
  title.textContent=title.title=track.name;
  title.dataset.noI18n='';
  const controls=document.createElement('div');
  controls.className='zl-track-monitor';
  const apply=()=>{s.updateGains(.04);s.save();};
  for(const [key,name,letter] of [['mute','Silenciar track','M'],['solo','Solar track','S']]){
   const button=document.createElement('button');
   button.type='button';
   button.textContent=letter;
   button.title=button.ariaLabel=tLabel(name);
   const sync=()=>button.setAttribute('aria-pressed',String(!!track[key]));
   sync();
   button.onclick=()=>{
    s.beginEdit();
    track[key]=!track[key];
    sync();
    apply();
    s.commitEdit();
   };
   controls.append(button);
  }
  const volume=document.createElement('input');
  volume.type='range';
  volume.min='0';
  volume.max=String(Math.max(1.5,track.volume??1));
  volume.step='0.01';
  volume.value=String(track.volume??1);
  volume.ariaLabel=tLabel('Volume da track');
  const showValue=()=>{
   volume.title=`${Math.round(Number(volume.value)*100)}%`;
   volume.setAttribute('aria-valuetext',volume.title);
  };
  showValue();
  volume.oninput=()=>{
   s.beginEdit();
   track.volume=Number(volume.value);
   showValue();
   apply();
  };
  volume.onchange=()=>s.commitEdit();
  controls.append(volume);
  panel.append(title,controls);
  return panel;
 }
 function paint(){if(drag||content.querySelector('.zl-track-monitor input:active'))return;const clips=editable();split.disabled=mute.disabled=!clips.length;remove.disabled=!clips.length||clips.length===all().length;fx.disabled=clips.length!==1;mute.textContent=tLabel(clips.length&&clips.every(({it})=>it.muted)?'Reativar trecho':'Silenciar trecho');const top=scroll.scrollTop,left=scroll.scrollLeft;content.style.minHeight=(30+s.tracks.length*86)+'px';content.innerHTML='';content.style.width=Math.max(scroll.clientWidth,scroll.clientWidth*zoom)+'px';const width=content.clientWidth-170,scale=width/Math.max(.1,s.duration),ruler=document.createElement('div');ruler.className='zl-track-ruler';ruler.style.marginLeft='170px';const step=60/s.projectBpm();for(let t=s.tempo.points?.[0]?.time||0;t<=s.duration;t+=step){if(Math.round((t-(s.tempo.points?.[0]?.time||0))/step)%Math.max(1,Math.ceil(45/(step*scale))))continue;const b=document.createElement('button');b.style.left=t*scale+'px';b.textContent=((t-(s.tempo.points?.[0]?.time||0))/step+1).toFixed(0);b.onclick=()=>s.seek(t);ruler.append(b);}content.append(ruler);
 for(const t of s.tracks){const row=document.createElement('div');row.className='zl-track-row';row.append(trackControls(t));const lane=document.createElement('div');lane.className='zl-track-lane';lane.style.backgroundPosition=((s.tempo.points?.[0]?.time||0)*scale)+'px 0';lane.style.backgroundSize=Math.max(1,step*scale)+'px 100%';lane.onclick=e=>{if(e.target===lane)s.seek((e.clientX-lane.getBoundingClientRect().left)/scale);};if(s.isGuideTrack(t)&&t.items.length)drawGuide(t,lane,scale);else for(const it of t.items){const clip=document.createElement('div');clip.className='zl-audio-clip'+(selected.has(it)?' selected':'')+(it.muted?' muted':'')+(it.effects?.enabled?' effected':'');clip.style.left=(it.position||0)*scale+'px';clip.style.width=Math.max(5,(it.length??it.buffer.duration)*scale)+'px';clip.innerHTML='<i data-edge="start"></i><canvas></canvas><i data-edge="end"></i>';const canvas=clip.querySelector('canvas');const clipWidth=parseFloat(clip.style.width),visibleStart=Math.max(0,scroll.scrollLeft-170-(it.position||0)*scale-50),visibleWidth=Math.max(4,Math.min(clipWidth-visibleStart,scroll.clientWidth+100));canvas.width=Math.max(4,Math.round(visibleWidth*(devicePixelRatio||1)));canvas.height=65;canvas.style.position='absolute';canvas.style.left=visibleStart+'px';canvas.style.width=visibleWidth+'px';const c=canvas.getContext('2d'),peaks=it.buffer.peaks||it.buffer.getChannelData?.(0)||[];c.strokeStyle=selected.has(it)?'#c9adff':'#77b7cc';c.beginPath();for(let x=0;x<canvas.width;x++){const second=(it.offset||0)+(visibleStart+x/canvas.width*visibleWidth)/Math.max(1,clipWidth)*(it.length??it.buffer.duration)*(it.playbackRate||1),high=detail.get(it),data=high&&second>=high.start&&second<=high.end?high:null,index=data?Math.min(data.peaks.length-1,Math.floor((second-data.start)/(data.end-data.start)*data.peaks.length)):Math.floor(second/it.buffer.duration*peaks.length),amp=Math.min(1,Math.abs((data?.peaks||peaks)[index]||0))*30;c.moveTo(x,32-amp);c.lineTo(x,32+amp);}c.stroke();clip.onpointerdown=e=>begin(e,it,scale);lane.append(clip);}row.append(lane);content.append(row);}
 const playhead=document.createElement('i');playhead.className='zl-track-playhead';content.append(playhead);scroll.scrollTop=top;scroll.scrollLeft=left;tick();}
 function drawGuide(track,lane,scale){
  const guide=track.items, anchor=guide.reduce((left,item)=>item.position<left.position?item:left);
  const start=anchor.position||0;
  const length=item=>item.length??(item.buffer.duration-(item.offset||0))/(item.playbackRate||1);
  const end=Math.max(...guide.map(item=>(item.position||0)+length(item)));
  const clip=document.createElement('div');
  clip.className='zl-audio-clip'+(guide.some(item=>selected.has(item))?' selected':'');
  clip.style.left=start*scale+'px';
  clip.style.width=Math.max(5,(end-start)*scale)+'px';
  const canvas=document.createElement('canvas'),clipWidth=parseFloat(clip.style.width);
  const from=Math.max(0,scroll.scrollLeft-170-start*scale-50);
  const width=Math.max(4,Math.min(clipWidth-from,scroll.clientWidth+100));
  canvas.width=Math.ceil(width);
  canvas.height=65;
  Object.assign(canvas.style,{position:'absolute',left:from+'px',width:width+'px'});
  const ctx=canvas.getContext('2d');
  ctx.strokeStyle='#9d84d6';
  ctx.beginPath();
  for(const item of guide){
   const peaks=item.buffer.peaks||item.buffer.getChannelData?.(0);
   if(item.muted||!peaks?.length)continue;
   const left=(item.position-start)*scale-from,right=left+length(item)*scale;
   for(let x=Math.max(0,Math.ceil(left));x<Math.min(canvas.width,right);x++){
    const time=(item.offset||0)+(x-left)/scale*(item.playbackRate||1);
    const index=Math.floor(time/item.buffer.duration*peaks.length);
    const amplitude=Math.min(1,Math.abs(peaks[index]||0))*30;
    ctx.moveTo(x,32-amplitude);
    ctx.lineTo(x,32+amplitude);
   }
  }
  ctx.stroke();
  clip.append(canvas);
  clip.onpointerdown=e=>begin(e,anchor,scale,guide);
  lane.append(clip);
 }
 function begin(e,it,scale,items=[it]){if(e.button!==0||applying)return;e.preventDefault();if(e.ctrlKey||e.metaKey){const remove=items.every(item=>selected.has(item));for(const item of items)remove?selected.delete(item):selected.add(item);paint();return;}if(!items.every(item=>selected.has(item)))selected=new Set(items);s.beginEdit();const origin=e.clientX,edge=e.target.dataset.edge,states=[...selected].map(item=>({item,position:item.position||0,offset:item.offset||0,length:item.length??(item.buffer.duration-(item.offset||0))/(item.playbackRate||1),rate:item.playbackRate||1}));const anchor=states.find(v=>v.item===it);let delta=0;drag=true;
 const move=ev=>{const raw=(ev.clientX-origin)/scale,base=edge==='end'?anchor.position+anchor.length:anchor.position;delta=grid(base+raw)-base;let min=-Infinity,max=Infinity;for(const v of states){if(!edge)min=Math.max(min,-v.position);else if(edge==='start'){min=Math.max(min,-v.position,-v.offset/v.rate);max=Math.min(max,v.length-.005);}else{min=Math.max(min,-v.length+.005);max=Math.min(max,(v.item.buffer.duration-v.offset)/v.rate-v.length);}}delta=Math.max(min,Math.min(max,delta));for(const v of states){if(!edge)v.item.position=v.position+delta;else if(edge==='start'){v.item.position=v.position+delta;v.item.offset=v.offset+delta*v.rate;v.item.length=v.length-delta;}else v.item.length=v.length+delta;}message(`${edge?'Borda':'Posição'}: ${delta>=0?'+':''}${delta.toFixed(3)} s · ${states.length} item(ns)`);drag=false;paint();drag=true;};
 const up=async()=>{document.removeEventListener('pointermove',move);document.removeEventListener('pointerup',up);drag=false;const at=s.position(),was=s.playing;s.halt();s.offset=at;s.duration=Math.max(s._executionBase?.duration/(s.executionFactor||1)||0,...all().map(({it})=>(it.position||0)+(it.length??it.buffer.duration)));if(s._executionBase)s._executionBase.duration=s.duration*(s.executionFactor||1);s._nativeSignature=null;s._nativeConfigured=false;s.drawSections();s.refreshProjectMeta();s.$('[data-progress]').max=s.duration;await s.prepareNative();if(was)await s.play(true);s.save();s.commitEdit();paint();message('Alteração aplicada. Regiões e marcadores mantêm suas posições na timeline. Salve o projeto.');};document.addEventListener('pointermove',move);document.addEventListener('pointerup',up,{once:true});}
 function tick(){const p=content.querySelector('.zl-track-playhead');if(p)p.style.left=170+s.position()/Math.max(.1,s.duration)*(content.clientWidth-170)+'px';modal.querySelector('[data-cursor]').textContent=s.position().toFixed(2)+' s';s.refreshPerformanceStates();}
 async function requestDetail(){const scale=(content.clientWidth-170)/s.duration,start=Math.max(0,(scroll.scrollLeft-170)/scale),end=Math.min(s.duration,(scroll.scrollLeft+scroll.clientWidth)/scale),first=Math.max(0,Math.floor((scroll.scrollTop-30)/86)),last=Math.min(s.tracks.length,first+Math.ceil(scroll.clientHeight/86)+2);for(const t of s.tracks.slice(first,last))for(const it of t.items){if(closed)return;if(!it.buffer.nativeRef)continue;const a=Math.max(it.offset||0,(it.offset||0)+(start-(it.position||0))*(it.playbackRate||1)),b=Math.min(it.buffer.duration,(it.offset||0)+(end-(it.position||0))*(it.playbackRate||1));if(b<=a)continue;try{const peaks=await window.zlplayNative.peaks(it.buffer.nativeRef,{start:a,end:b,bins:Math.min(4000,Math.ceil(scroll.clientWidth*devicePixelRatio))});if(closed)return;detail.set(it,{start:a,end:b,peaks});}catch{}}if(!closed)paint();}const schedule=()=>{clearTimeout(rangeTimer);rangeTimer=setTimeout(requestDetail,200);};let lastLeft=0;scroll.addEventListener('scroll',()=>{if(!drag&&lastLeft!==scroll.scrollLeft){lastLeft=scroll.scrollLeft;paint();}schedule();});scroll.addEventListener('wheel',e=>{if(!e.shiftKey){if(e.target.closest('.zl-track-name'))return;e.preventDefault();scroll.scrollLeft+=(Math.abs(e.deltaX)>Math.abs(e.deltaY)?e.deltaX:e.deltaY)*(e.deltaMode===1?20:1);return;}e.preventDefault();const anchor=scroll.scrollLeft+e.clientX-scroll.getBoundingClientRect().left,ratio=anchor/content.clientWidth;zoom=Math.max(1,Math.min(80,zoom*(e.deltaY<0?1.2:1/1.2)));paint();scroll.scrollLeft=ratio*content.clientWidth-(e.clientX-scroll.getBoundingClientRect().left);schedule();},{passive:false});schedule();
 const snapButton=modal.querySelector('[data-snap]');snapButton.textContent='∪ Grade '+(snap?'ON':'OFF');snapButton.setAttribute('aria-pressed',snap);
 const timer=setInterval(tick,80),cleanup=historyButtons(modal,s);const resize=new ResizeObserver(()=>paint());resize.observe(scroll);document.addEventListener('zl-edit-restored',paint);
 modal.querySelector('[data-select-all]').onclick=()=>{selected=new Set(all().map(v=>v.it));paint();};modal.querySelector('[data-snap]').onclick=e=>{snap=!snap;s.toggleSnap(snap);e.target.textContent='∪ Grade '+(snap?'ON':'OFF');e.target.setAttribute('aria-pressed',snap);};for(const [q,d] of [['in',.5],['out',-.5]])modal.querySelector('[data-zoom-'+q+']').onclick=()=>{zoom=Math.max(1,Math.min(80,d>0?zoom*1.5:zoom/1.5));paint();schedule();};
 modal.querySelector('[data-align]').onclick=async e=>{e.target.disabled=true;try{await alignTracks(s);paint();schedule();message('Tracks alinhadas. Confira antes de aceitar.');}catch(error){message(error.message);}finally{e.target.disabled=false;}};
 const source=modal.querySelector('[data-editor-click-source]');source.value=s.clickSource;source.onchange=()=>s.setClickSource(source.value);cycleSelect(source);
 modal.querySelector('[data-editor-play]').onclick=()=>s.play();modal.querySelector('[data-editor-pause]').onclick=()=>s.pause();modal.querySelector('[data-editor-stop]').onclick=()=>s.stop();modal.querySelector('[data-close]').onclick=()=>{closed=true;clearTimeout(rangeTimer);clearInterval(timer);resize.disconnect();cleanup();document.removeEventListener('zl-edit-restored',paint);document.removeEventListener('keydown',shortcuts,true);modal.remove();s._trackEditing=false;s.drawTracks();if(priorPad.playing&&priorPad.project===s.projectId)s.playPadNote(priorPad.note,false);};editorExit(modal,s,session);paint();
}

