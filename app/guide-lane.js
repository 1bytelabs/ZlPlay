import {snapToBeat} from './beat-grid.mjs';

const portugueseNames = {
 'acapella':'A capela','all in':'Toda Banda','bass':'Baixo','big ending':'Grande Final','break':'Pausa',
 'breakdown':'Grande Pausa','bridge':'Ponte','build':'Cresce','channel':'Canal','chorus':'Refrão','click':'Click',
 'drums':'Bateria','drums in':'Entra Bateria','ending':'Final','exhortation':'Exortação','guitar':'Guitarra',
 'hits':'Ataques','hold':'Segura','instrumental':'Instrumental','interlude':'Interlúdio','intro':'Intro',
 'key change down':'Desce o Tom','key change up':'Sobe o Tom','keys':'Teclas','last time':'Última Vez',
 'out':'Saída','post chorus':'Pós-Refrão','pre chorus':'Pré-Refrão','rap':'Rap','refrain':'Coro','solo':'Solo',
 'slowly build':'Crescendo','softly':'Suave','swell':'Swell','tag':'Repete','turnaround':'Turnaround',
 'vamp':'Rampa','verse':'Verso','pad':'Pad'
};
const guideBanks = new Map();
function guideName(file,language) {
 const name = file.replace(/\.[^.]+$/,'').replace(/^(?:portug[u]?ese|português|english|spanish|español)\s*-\s*/i,'');
 if(language!=='pt')return name;
 const [,base,number='']=name.match(/^(.*?)(\s+\d+)?$/);
 return (portugueseNames[base.toLowerCase()]||base)+number;
}

export function mountGuideLane(study, scroll, reportError, language) {
 const content = scroll.querySelector('.vs-timeline-content');
 const lane = document.createElement('section');
 lane.className = 'zl-guide-lane';
 lane.innerHTML = '<header><strong>Guia</strong><button data-load-guide-bank>Carregar banco de guias</button><select data-guide-voice aria-label="Guia"></select><button data-insert-guide>Inserir guia</button><button data-import-guide>Adicionar guia externa</button><button data-split>Dividir no cursor</button><button data-mute>Silenciar trecho</button><button data-delete-guide>Excluir trecho</button></header><div class="zl-guide-clips"></div>';
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
 const lengthOf = item => item.length ?? Math.max(0, item.buffer.duration - (item.offset || 0)) / (item.playbackRate || 1);
 const pct = time => 100 * time / (study.duration || 1) + '%';
 const t = value => window.__ZL_I18N__?.t(value) || value;
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
   clip.title = item.displayName || item.file.split(/[\\/]/).at(-1);
   clip.textContent = clip.title;
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
   clip.onclick = () => { selected = item; render(); };
   clip.onpointerdown = event => {
    if (event.button !== 0 || busy) return;
    const rect = clip.getBoundingClientRect();
    const mode = event.clientX - rect.left < 6 ? 'start' : rect.right - event.clientX < 6 ? 'end' : 'move';
    const first = event.clientX;
    const position = item.position || 0, length = lengthOf(item), offset = item.offset || 0;
    const rate = item.playbackRate || 1;
    const secondsPerPixel = study.duration / clips.getBoundingClientRect().width;
    const limit = (value, low, high) => Math.max(low, Math.min(high, value));
    let moved = false, next = {position,length,offset};
    clip.setPointerCapture(event.pointerId);
    clip.onpointermove = move => {
     if (!moved && Math.abs(move.clientX - first) < 4) return;
     moved = true;
     const anchor = mode === 'end' ? position + length : position;
     let at = anchor + (move.clientX - first) * secondsPerPixel;
     if (study.snapEnabled) at = snapToBeat(at,study.tempo,study.duration);
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
    };
    clip.onpointercancel = render;
    clip.onpointerup = () => {
     if (!moved) return;
     clip.onclick = null;
     selected = item;
     edit((_,item) => Object.assign(item,next));
    };
   };
   clips.append(clip);
  }
  lane.querySelectorAll('header button:not([data-import-guide])').forEach(button => { button.disabled = !selected || busy; });
  const deleteButton=lane.querySelector('[data-delete-guide]');
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
 const loadBank = lane.querySelector('[data-load-guide-bank]');
 const voice = lane.querySelector('[data-guide-voice]');
 const insert = lane.querySelector('[data-insert-guide]');
 loadBank.hidden = voice.hidden = insert.hidden = !bridge?.importGuide;
 function updateBank() {
  const bank = guideBanks.get(language.value)||[];
  voice.replaceChildren(...bank.map(file=>{
   const option=new Option(guideName(file.name,language.value),file.token);
   option.dataset.noI18n='';
   return option;
  }));
  voice.disabled = insert.disabled = !bank.length;
 }
 loadBank.onclick = async () => {
  const selectedLanguage = language.value;
  loadBank.disabled = true;
  try {
   const folder = await bridge.openSelection('folder');
   if (!folder) return;
   const bank=folder.entries.filter(file=>/\.(wav|mp3|flac|ogg|aiff?)$/i.test(file.name));
   if (!bank.length) return reportError(t('Nenhuma guia de áudio encontrada na pasta.'));
   guideBanks.set(selectedLanguage,bank);
   updateBank();
  } catch(error) {
   reportError(error.message);
  } finally {
   loadBank.disabled = false;
  }
 };
 language.addEventListener('change',updateBank);
 updateBank();
 importButton.onclick = () => addGuide();
 insert.onclick = () => addGuide(voice.value);
 async function addGuide(sourceToken) {
  const prefix = study.currentProjectDir ? study.currentProjectDir + '/' : '';
  const anchor = study._saveTarget?.token || study.currentProjectFiles.find(file =>
   study.dirName(file.name) === study.currentProjectDir && file._nativeFile?._zlToken
  )?._nativeFile?._zlToken;
  if (!anchor) return reportError(t('Abra um projeto ou pasta antes de adicionar uma guia.'));
  const project = study.projectId;
  const at = study.snapEnabled ? snapToBeat(study.position(),study.tempo,study.duration) : study.position();
  if (at >= study.duration) return;
  if (importButton.disabled || busy) return;
  importButton.disabled = true;
  insert.disabled = true;
  try {
   const label = sourceToken ? voice.selectedOptions[0]?.textContent : null;
   const result = await bridge.importGuide({anchor,sourceToken,language:(window.__ZL_I18N__?.language || 'pt').slice(0,2)});
   if (!result || !lane.isConnected || project !== study.projectId) return;
   const file = new File([],result.name);
   file._zlToken = result.token;
   Object.defineProperty(file,'webkitRelativePath',{value:prefix+result.relativePath});
   const buffer = await bridge.describeToken(result.token);
   if (!lane.isConnected || project !== study.projectId) return;
   await edit(()=>{
    let track = study.tracks.find(track=>study.isGuideTrack(track));
    if (!track) {
     track = {name:'Guia',group:'VS',volume:1,originalVolume:1,mute:false,solo:false,pan:0,items:[],restored:true};
     study.tracks.push(track);
    }
    selected = {file:prefix+result.relativePath,projectFile:result.relativePath,displayName:label||result.displayName,buffer,position:at,offset:0,length:Math.min(buffer.duration,study.duration-at),pitch:0};
    track.items.push(selected);
    study.currentProjectFiles.push(study.wrap(file,prefix+result.relativePath));
    const song = window.__ZL_LIBRARY_SCAN__?.songs.find(song=>song.name===window.__ZL_LIBRARY_CURRENT__);
    if (song) song.files.push(file);
   },false);
  } catch (error) {
   reportError(error.message);
  } finally {
   importButton.disabled = false;
   insert.disabled = !voice.options.length;
  }
 }
 lane.querySelector('[data-split]').onclick = () => {
  const at = study.snapEnabled ? snapToBeat(study.position(), study.tempo, study.duration) : study.position();
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
 lane.querySelector('[data-delete-guide]').onclick = () => edit((track, item) => {
  if (study.tracks.length===1 && track.items.length===1) return;
  track.items = track.items.filter(clip => clip !== item);
  if (!track.items.length && study.tracks.length > 1) study.tracks = study.tracks.filter(candidate => candidate !== track);
  selected = null;
 });
 let frame;
 function paint() {
  cursor.style.left = pct(study.position());
  frame = requestAnimationFrame(paint);
 }
 document.addEventListener('zl-edit-restored', render);
 render();
 paint();
 return () => {
  cancelAnimationFrame(frame);
  document.removeEventListener('zl-edit-restored', render);
  language.removeEventListener('change',updateBank);
  lane.remove();
 };
}
