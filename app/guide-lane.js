import {snapToBeat} from './beat-grid.mjs';

export function mountGuideLane(study, scroll, reportError) {
 const content = scroll.querySelector('.vs-timeline-content');
 const lane = document.createElement('section');
 lane.className = 'zl-guide-lane';
 lane.innerHTML = '<header><strong>Guia</strong><label>Idioma da guia <select data-guide-language><option value="pt">Português</option><option value="en">English</option><option value="es">Español</option></select></label><select data-guide-voice aria-label="Voz da guia"><option value="1">Voz masculina</option><option value="2">Voz feminina</option></select><select data-guide-preset aria-label="Guia"></select><button data-insert-guide>Adicionar guia</button><button data-import-guide>Adicionar guia externa</button><button data-duplicate-guide>Duplicar</button><button data-split>Dividir no cursor</button><button data-mute>Silenciar trecho</button><button data-delete-guide>Excluir trecho</button></header><div class="zl-guide-clips"></div>';
 const language = lane.querySelector('[data-guide-language]');
 const appLanguage = (window.__ZL_I18N__?.language || 'pt').slice(0,2);
 language.value = ['pt','en','es'].includes(appLanguage) ? appLanguage : 'pt';
 const clips = lane.querySelector('.zl-guide-clips');
 const grid = document.createElementNS('http://www.w3.org/2000/svg','svg');
 grid.classList.add('zl-guide-grid');
 grid.setAttribute('viewBox','0 0 1000 100');
 grid.setAttribute('preserveAspectRatio','none');
 grid.setAttribute('aria-hidden','true');
 const beats = document.createElementNS(grid.namespaceURI,'path');
 grid.append(beats);
 const cursor = document.createElement('i');
 cursor.className = 'zl-guide-cursor';
 content.append(lane);
 let selected = null, busy = false;
 let cursorDrag = null;
 let clipDrag;
 const lengthOf = item => item.length ?? Math.max(0, item.buffer.duration - (item.offset || 0)) / (item.playbackRate || 1);
 const pct = time => 100 * time / (study.duration || 1) + '%';
 const t = value => window.__ZL_I18N__?.t(value) || value;
 const editor = lane.closest('.zl-region-editor');
 const splitButton = lane.querySelector('[data-split]');
 const deleteButton = lane.querySelector('[data-delete-guide]');
 splitButton.setAttribute('aria-keyshortcuts','S');
 deleteButton.setAttribute('aria-keyshortcuts','Delete');
 function guideShortcut(event) {
  const button = event.key.toLowerCase() === 's' ? splitButton : event.key === 'Delete' ? deleteButton : null;
  if (!button || event.ctrlKey || event.metaKey || event.altKey || event.shiftKey ||
      event.target.closest('input,textarea,select,[contenteditable],[role=textbox]')) return;
  const dialogs = [...document.querySelectorAll('.zl-audio-modal,.zl-library-modal,.zl-select-popup')];
  const top = dialogs.filter(dialog => dialog.getClientRects().length)
   .sort((a,b) => (+getComputedStyle(a).zIndex||0) - (+getComputedStyle(b).zIndex||0)).at(-1);
  if (top !== editor) return;
  event.preventDefault();
  event.stopImmediatePropagation();
  if (!event.repeat) button.click();
 }
 function cursorTime(clientX) {
  const rect = clips.getBoundingClientRect();
  const time = study.duration * Math.max(0, Math.min(1, (clientX - rect.left) / rect.width));
  return study.snapEnabled ? snapToBeat(time, study.tempo, study.duration) : time;
 }
 function grabsCursor(event) {
  if (event.shiftKey && event.target.closest('.zl-guide-trim')) return false;
  const rect = cursor.getBoundingClientRect();
  return Math.abs(event.clientX - (rect.left + rect.width / 2)) <= 6;
 }
 clips.onpointerdown = event => {
  if (event.button !== 0 || busy || (event.target.closest('.zl-guide-clip') && !grabsCursor(event))) return;
  event.preventDefault();
  cursorDrag = {id:event.pointerId,time:cursorTime(event.clientX)};
  clips.setPointerCapture(event.pointerId);
 };
 clips.onpointermove = event => {
  if (cursorDrag?.id === event.pointerId) cursorDrag.time = cursorTime(event.clientX);
  clips.classList.toggle('zl-guide-cursor-hover', !!cursorDrag || grabsCursor(event));
 };
 clips.onpointerleave = () => clips.classList.remove('zl-guide-cursor-hover');
 clips.onpointerup = event => {
  if (cursorDrag?.id !== event.pointerId) return;
  cursorDrag = null;
  study.seek(cursorTime(event.clientX));
 };
 clips.onlostpointercapture = event => {
  if (cursorDrag?.id === event.pointerId) cursorDrag = null;
  clips.classList.remove('zl-guide-cursor-hover');
 };
 function render() {
  const points = study.tempo.points?.length ? study.tempo.points : [{time:0,bpm:study.projectBpm()}];
  const marks=[];
  for (let index=0;index<points.length;index++) {
   const point=points[index], end=points[index+1]?.time??study.duration;
   const step=60/point.bpm;
   if(!Number.isFinite(step)||step<=0)continue;
   const stride=Math.max(1,Math.ceil(study.duration/step/1800));
   for(let time=point.time;time<end;time+=step*stride){
    const x=time/(study.duration||1)*1000;
    marks.push('M'+x+' 0V100');
   }
  }
  beats.setAttribute('d',marks.join(''));
  clips.replaceChildren(grid,cursor);
  const guides = study.tracks.filter(track => study.isGuideTrack(track));
  if (!guides.some(track => track.items.includes(selected))) selected = null;
  for (const track of guides) for (const item of track.items) {
   const clip = document.createElement('button');
   clip.type = 'button';
   clip.className = 'zl-guide-clip';
   clip.dataset.noI18n='';
   clip.setAttribute('aria-pressed', String(item === selected));
   clip.classList.toggle('muted', !!item.muted);
   clip.style.left = pct(item.position || 0);
   clip.style.width = pct(lengthOf(item));
   clip.setAttribute('aria-label', item.displayName || item.file.split(/[\\/]/).at(-1));
   const peaks = item.buffer.peaks;
   if (peaks?.length) {
    const wave = document.createElementNS('http://www.w3.org/2000/svg','svg');
    wave.setAttribute('viewBox','0 0 240 48');
    wave.setAttribute('preserveAspectRatio','none');
    wave.setAttribute('aria-hidden','true');
    const path = document.createElementNS(wave.namespaceURI,'path');
    const offset = item.offset || 0, rate = item.playbackRate || 1;
    const segments = Array.from({length:240},(_,index)=>{
     const seconds = offset + index / 240 * lengthOf(item) * rate;
     const peak = peaks[Math.min(peaks.length-1,Math.floor(seconds/item.buffer.duration*peaks.length))] || 0;
     return 'M'+index+' '+(24-peak*22)+'V'+(24+peak*22);
    });
    path.setAttribute('d',segments.join(''));
    wave.append(path);
    clip.prepend(wave);
   }
   if (item === selected) {
    for (const edge of ['start','end']) {
     const handle = document.createElement('span');
     handle.className = 'zl-guide-trim';
     handle.dataset.edge = edge;
     handle.setAttribute('aria-hidden','true');
     clip.append(handle);
    }
   }
   let trimming = false;
   clip.onclick = event => {
    if (event.detail && trimming) return;
    if (event.detail) study.seek(cursorTime(event.clientX));
    selected = item;
    render();
   };
   clip.onpointerdown = event => {
    if (event.button !== 0 || busy || grabsCursor(event)) return;
    const mode = event.target.dataset.edge || 'move';
    trimming = mode !== 'move';
    if (trimming && !event.shiftKey) return;
    const first = event.clientX;
    const position = item.position || 0, length = lengthOf(item), offset = item.offset || 0;
    const rate = item.playbackRate || 1;
    const secondsPerPixel = study.duration / clips.getBoundingClientRect().width;
    const limit = (value, low, high) => Math.max(low, Math.min(high, value));
    let moved = false, next = {position,length,offset};
    clipDrag?.abort();
    const drag = clipDrag = new AbortController();
    const options = {signal:drag.signal};
    clip.setPointerCapture(event.pointerId);
    window.addEventListener('pointermove', move => {
     if (move.pointerId !== event.pointerId) return;
     if (trimming && !move.shiftKey) {
      drag.abort();
      render();
      return;
     }
     if (!moved && Math.abs(move.clientX - first) < 4) return;
     moved = true;
     const anchor = mode === 'end' ? position + length : position;
     let at = anchor + (move.clientX - first) * secondsPerPixel;
     if (study.snapEnabled && !trimming) at = snapToBeat(at,study.tempo,study.duration);
     if (mode === 'start') {
      at = limit(at,Math.max(0,position-offset/rate),position+length-.01);
      next = {position:at,length:position+length-at,offset:offset+(at-position)*rate};
     } else if (mode === 'end') {
      next = {position,offset,length:limit(at-position,.01,Math.min(study.duration-position,(item.buffer.duration-offset)/rate))};
     } else {
      next = {position:limit(at,0,study.duration-length),length,offset};
     }
     clip.style.left = pct(next.position);
     clip.style.width = pct(next.length);
    },options);
    window.addEventListener('pointercancel', cancel => {
     if (cancel.pointerId !== event.pointerId) return;
     drag.abort();
     render();
    },options);
    window.addEventListener('pointerup', up => {
     if (up.pointerId !== event.pointerId) return;
     drag.abort();
     if (!moved) return;
     if (trimming && !up.shiftKey) {
      render();
      return;
     }
     clip.onclick = null;
     selected = item;
     edit((_,item) => Object.assign(item,next));
    },options);
   };
   clips.append(clip);
  }
  lane.querySelectorAll('[data-duplicate-guide],[data-split],[data-mute],[data-delete-guide]').forEach(button => { button.disabled = !selected || busy; });
  if(study.tracks.length===1&&study.tracks[0].items.length===1)deleteButton.disabled=true;
  lane.querySelector('[data-mute]').textContent = t(selected?.muted ? 'Reativar trecho' : 'Silenciar trecho');
 }
 async function edit(change, requireClip = true) {
  if (busy || requireClip && !selected) return;
  const track = study.tracks.find(track => track.items.includes(selected));
  if (requireClip && !track) return;
  busy = true;
  const wasPlaying = study.playing, position = study.position();
  study.beginEdit();
  study.halt();
  change(track, selected);
  study.offset = position;
  study._nativeSignature = null;
  study._nativeConfigured = false;
  study.drawTracks();
  try {
   await study.prepareNative();
   if (wasPlaying) await study.play(true);
  } catch (error) {
   reportError(error.message);
  } finally {
   study.commitEdit();
   study.save();
   busy = false;
   render();
  }
 }
 const importButton = lane.querySelector('[data-import-guide]');
 const bridge = window.zlplayNative;
 importButton.hidden = !bridge?.importGuide;
 const voice = lane.querySelector('[data-guide-voice]');
 const preset = lane.querySelector('[data-guide-preset]');
 const insert = lane.querySelector('[data-insert-guide]');
 voice.hidden = preset.hidden = insert.hidden = !bridge?.guideBank;
 let bank = [], bankRequest = 0, importing = false;
 function renderBank() {
  const choice = preset.value;
  preset.replaceChildren();
  for (const category of ['sections','cues']) {
   const files = bank.filter(file=>file.voice===voice.value && file.category===category);
   if (!files.length) continue;
   const group = document.createElement('optgroup');
   group.label = t(category==='sections' ? 'Partes da música' : 'Comandos para a banda');
   for (const file of files) {
    const option = new Option(file.name,file.token);
    option.dataset.noI18n='';
    group.append(option);
   }
   preset.append(group);
  }
  if ([...preset.options].some(option=>option.value===choice))preset.value=choice;
  preset.disabled = insert.disabled = importing || !preset.options.length;
 }
 async function updateBank() {
  const request = ++bankRequest;
  bank = [];
  renderBank();
  try {
   const entries = await bridge?.guideBank(language.value);
   if (request!==bankRequest || !lane.isConnected) return;
   bank = entries || [];
   renderBank();
  } catch(error) {
   if (lane.isConnected)reportError(error.message);
  }
 }
 voice.onchange=renderBank;
 language.addEventListener('change',updateBank);
 updateBank();
 importButton.onclick = () => addGuide();
 insert.onclick = () => addGuide(preset.value,preset.selectedOptions[0]?.textContent);
 async function addGuide(sourceToken,label) {
  if (importing || busy) return;
  const prefix = study.currentProjectDir ? study.currentProjectDir + '/' : '';
  const anchor = study._saveTarget?.token || study.currentProjectFiles.find(file =>
   study.dirName(file.name) === study.currentProjectDir && file._nativeFile?._zlToken
  )?._nativeFile?._zlToken;
  if (!anchor) return reportError(t('Abra um projeto ou pasta antes de adicionar uma guia.'));
  const project = study.projectId;
  const at = study.snapEnabled ? snapToBeat(study.position(),study.tempo,study.duration) : study.position();
  importing = importButton.disabled = insert.disabled = true;
  try {
   const results = await bridge.importGuide({anchor,sourceToken,language:(window.__ZL_I18N__?.language || 'pt').slice(0,2)});
   if (!results.length || !lane.isConnected || project !== study.projectId) return;
   const assets = [];
   for (const result of results) {
    const name = prefix+result.relativePath;
    const file = new File([],result.name);
    file._zlToken = result.token;
    Object.defineProperty(file,'webkitRelativePath',{value:name});
    const buffer = await bridge.describeToken(result.token);
    assets.push({result,file,name,buffer});
   }
   if (!lane.isConnected || project !== study.projectId) return;
   await edit(()=>{
    let track = study.tracks.find(track=>study.isGuideTrack(track));
    if (!track) {
     track = {name:'Guia',group:'VS',volume:1,originalVolume:1,mute:false,solo:false,pan:0,items:[],restored:true};
     study.tracks.push(track);
    }
    const song = window.__ZL_LIBRARY_SCAN__?.songs.find(song=>song.name===window.__ZL_LIBRARY_CURRENT__);
    const total = assets.reduce((sum,asset)=>sum+asset.buffer.duration,0);
    let position = Math.max(0,Math.min(at,study.duration-total));
    const first = track.items.length;
    for (const {result,file,name,buffer} of assets) {
     track.items.push({file:name,projectFile:result.relativePath,displayName:label||result.displayName,buffer,position,offset:0,length:buffer.duration,pitch:0});
     position+=buffer.duration;
     study.currentProjectFiles.push(study.wrap(file,name));
     if (song) song.files.push(file);
    }
    selected = track.items[first];
    if (position>study.duration) {
     study.duration=position;
     if (study._executionBase)study._executionBase.duration=position*(study.executionFactor||1);
     study.$('[data-progress]').max=position;
     study.drawSections();
     study.refreshProjectMeta();
    }
   },false);
  } catch (error) {
   reportError(error.message);
  } finally {
   importing = importButton.disabled = false;
   renderBank();
  }
 }
 lane.querySelector('[data-duplicate-guide]').onclick = () => edit((track,item)=>{
  const length=lengthOf(item);
  const position=Math.max(0,Math.min(item.position+length,study.duration-length));
  selected={...item,position};
  track.items.splice(track.items.indexOf(item)+1,0,selected);
 });
 splitButton.onclick = () => {
  const at = study.position();
  if (!selected || at <= selected.position + .01 || at >= selected.position + lengthOf(selected) - .01) return;
  edit((track, item) => {
   const left = at - item.position;
   const right = {...item, position: at, offset: (item.offset || 0) + left * (item.playbackRate || 1), length: lengthOf(item) - left};
   item.length = left;
   track.items.splice(track.items.indexOf(item) + 1, 0, right);
   selected = right;
  });
 };
 lane.querySelector('[data-mute]').onclick = () => edit((_, item) => { item.muted = !item.muted; });
 deleteButton.onclick = () => {
  if (!selected || busy || deleteButton.disabled) return;
  const name = selected.displayName || selected.file.split(/[\\/]/).at(-1);
  if (!confirm(t('Excluir o trecho selecionado da Guia?') + '\n\n' + name)) return;
  edit((track, item) => {
   track.items = track.items.filter(clip => clip !== item);
   if (!track.items.length && study.tracks.length > 1) study.tracks = study.tracks.filter(candidate => candidate !== track);
   selected = null;
  });
 };
 let frame;
 function paint() {
  cursor.style.left = pct(cursorDrag?.time ?? study.position());
  frame = requestAnimationFrame(paint);
 }
 document.addEventListener('zl-edit-restored', render);
 document.addEventListener('keydown', guideShortcut, true);
 render();
 paint();
 function cleanup() {
  clipDrag?.abort();
  cancelAnimationFrame(frame);
  document.removeEventListener('zl-edit-restored', render);
  document.removeEventListener('keydown', guideShortcut, true);
  language.removeEventListener('change',updateBank);
  lane.remove();
 }
 return {language,cleanup};
}
