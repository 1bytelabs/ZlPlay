import {rppKey} from './tonal-versions.js';
import {flatNote} from './note-display.mjs';
import { shortestSemitoneDelta } from './tonal-distance.mjs';
import { installNativeAudio } from './native-audio.js';
const AUDIO_EXT=/\.(wav|mp3|flac|ogg|opus|m4a|aac)$/i;
const baseName=p=>p.replace(/\\/g,"/").split("/").pop();
const norm=s=>baseName(s).normalize("NFD").replace(/[\u0300-\u036f]/g,"").toLowerCase();
const esc=s=>String(s).replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
const fmt=s=>`${Math.floor((s||0)/60)}:${String(Math.floor((s||0)%60)).padStart(2,"0")}`;
const KEY_NAMES=["C","C#/Db","D","D#/Eb","E","F","F#/Gb","G","G#/Ab","A","A#/Bb","B"];
const PAD_NOTES=["C","C#","D","D#","E","F","F#","G","G#","A","A#","B"];
const PAD_FILES={"C":"C.wav","C#":"Cs.wav","D":"D.wav","D#":"Ds.wav","E":"E.wav","F":"F.wav","F#":"Fs.wav","G":"G.wav","G#":"Gs.wav","A":"A.wav","A#":"As.wav","B":"B.wav"};
const keyIndex=key=>({C:0,"C#":1,DB:1,D:2,"D#":3,EB:3,E:4,F:5,"F#":6,GB:6,G:7,"G#":8,AB:8,A:9,"A#":10,BB:10,B:11})[String(key||"").replace(/m$/i,"").toUpperCase()];

const REGION_PRESETS=[
  {title:"Abertura",items:[
    {name:"Contagem",color:"#B4B4B4"},{name:"Intro",color:"#FFD700"},{name:"Instrumental",color:"#00C8C8"},{name:"Interlúdio",color:"#96C864"},{name:"Turnaround",color:"#C864C8"}
  ]},
  {title:"Versos",items:[
    {name:"Verso",color:"#4682B4"},{name:"Verso 1",color:"#4682B4"},{name:"Verso 2",color:"#4169E1"},{name:"Verso 3",color:"#3C5FC8"},{name:"Verso 4",color:"#3755B4"},{name:"Verso 5",color:"#324BA0"}
  ]},
  {title:"Transições",items:[
    {name:"Pre-Refrão",color:"#8A2BE2"},{name:"Pre-Refrão 1",color:"#8A2BE2"},{name:"Pre-Refrão 2",color:"#8026D2"},{name:"Rampa",color:"#FFA500"}
  ]},
  {title:"Refrões / Coro",items:[
    {name:"Refrão",color:"#228B22"},{name:"Refrão 1",color:"#228B22"},{name:"Refrão 2",color:"#1E781E"},{name:"Refrão 3",color:"#1A6E1A"},{name:"Refrão 4",color:"#166416"},{name:"Refrão 5",color:"#145A14"},{name:"Coro",color:"#00C878"}
  ]},
  {title:"Pontes",items:[
    {name:"Ponte",color:"#A050C8"},{name:"Ponte 1",color:"#A050C8"},{name:"Ponte 2",color:"#9646BE"},{name:"Ponte 3",color:"#8C3CB4"},{name:"Ponte 4",color:"#8232AA"},{name:"Ponte 5",color:"#782DA0"}
  ]},
  {title:"Partes Musicais",items:[
    {name:"Solo",color:"#FF8C00"},{name:"Grande Pausa",color:"#787878"},{name:"Repete",color:"#FFEB96"},{name:"Tag",color:"#C8C850"}
  ]},
  {title:"Encerramento",items:[
    {name:"Final",color:"#B22222"},{name:"Saida",color:"#783C28"},{name:"Outro",color:"#FFBE78"}
  ]}
];

class ZipReader{
  constructor(file){this.file=file}
  async files(){
    const buf=await this.file.arrayBuffer(),v=new DataView(buf),u=new Uint8Array(buf);let e=-1;
    for(let i=Math.max(0,u.length-65557);i<=u.length-22;i++)if(v.getUint32(i,true)===0x06054b50)e=i;
    if(e<0)throw Error("ZIP inválido: diretório central não encontrado.");
    const count=v.getUint16(e+10,true),start=v.getUint32(e+16,true),out=[];let p=start,total=0;
    for(let i=0;i<count;i++){
      if(v.getUint32(p,true)!==0x02014b50)throw Error("ZIP inválido.");
      const method=v.getUint16(p+10,true),size=v.getUint32(p+20,true),raw=v.getUint32(p+24,true),nl=v.getUint16(p+28,true),xl=v.getUint16(p+30,true),cl=v.getUint16(p+32,true),off=v.getUint32(p+42,true),name=new TextDecoder().decode(u.slice(p+46,p+46+nl));p+=46+nl+xl+cl;
      if(name.endsWith("/"))continue;if(++total>300)throw Error("O ZIP tem arquivos demais para este teste (máximo 300).");
      if(raw>800*1024*1024)throw Error(`Arquivo muito grande no ZIP: ${name}`);
      const lnl=v.getUint16(off+26,true),lxl=v.getUint16(off+28,true),data=u.slice(off+30+lnl+lxl,off+30+lnl+lxl+size);let bytes;
      if(method===0)bytes=data;
      else if(method===8&&typeof DecompressionStream!=="undefined"){
        const stream=new Blob([data]).stream().pipeThrough(new DecompressionStream("deflate-raw"));bytes=new Uint8Array(await new Response(stream).arrayBuffer());
      }else throw Error(`Compressão não suportada neste navegador: ${name}`);
      out.push({name,size:raw,arrayBuffer:async()=>bytes.buffer.slice(bytes.byteOffset,bytes.byteOffset+bytes.byteLength),text:async()=>new TextDecoder().decode(bytes)});
    }return out;
  }
}

function parseRpp(text,audios=[]){
  const lines=text.split(/\r?\n/),tracks=[],stack=[];let tr=null,item=null;
  const q=line=>{const m=line.match(/"((?:\\.|[^"])*)"/);return m?m[1].replace(/\\"/g,'"'):""};
  for(const raw of lines){const line=raw.trim();
    if(line.startsWith("<")){const type=(line.slice(1).match(/^\S+/)||[""])[0];stack.push(type);if(item&&/^<SOURCE\s+CLICK\b/.test(line))item.builtinClick=true;if(type==="TRACK"){tr={name:"",volume:1,mute:false,solo:false,folderDelta:0,items:[]};tracks.push(tr)}else if(type==="ITEM"&&tr){item={name:"",position:0,offset:0,length:null,file:"",playbackRate:1,pitch:0};tr.items.push(item)}continue}
    if(line===">"){const type=stack.pop();if(type==="ITEM")item=null;if(type==="TRACK"){item=null;tr=null}continue}
    if(!tr)continue;
    if(line.startsWith("NAME ")&&!item)tr.name=q(line)||line.slice(5).trim()||tr.name;
    else if(line.startsWith("NAME ")&&item)item.name=q(line)||line.slice(5).trim()||item.name;
    else if(line.startsWith("VOLPAN ")&&!item)tr.volume=Math.max(0,Number(line.split(/\s+/)[1])||1);
    else if(line.startsWith("MUTESOLO ")&&!item){const p=line.split(/\s+/);tr.mute=Number(p[1])===1;tr.solo=Number(p[2])===1}
    else if(line.startsWith("ISBUS ")&&!item){const p=line.split(/\s+/);tr.folderDelta=Number(p[2])||0}
    else if(item&&line.startsWith("POSITION "))item.position=Number(line.split(/\s+/)[1])||0;
    else if(item&&line.startsWith("SOFFS "))item.offset=Number(line.split(/\s+/)[1])||0;
    else if(item&&line.startsWith("LENGTH "))item.length=Number(line.split(/\s+/)[1])||null;
    else if(item&&line.startsWith("PLAYRATE ")){const p=line.split(/\s+/);item.playbackRate=Number(p[1])||1;item.pitch=Number(p[3])||0}
    else if(item&&line.startsWith("FILE "))item.file=q(line);
  }
  for(const track of tracks)for(const it of track.items)if(it.builtinClick&&!it.file){const wav=audios.find(a=>norm(a.name)===norm(track.name+".wav"))||audios.find(a=>/(^|\b)(click|claqueta|metronom)(\b|$)/i.test(baseName(a.name)));if(wav)it.file=wav.name;}
  const groups=[];for(const track of tracks){const group=groups.at(-1);track.group=group&&!/^vs$/i.test(group.trim())?group:"VS";if(track.folderDelta>0&&track.name)groups.push(track.name);if(track.folderDelta<0)for(let i=0;i<Math.abs(track.folderDelta);i++)groups.pop()}return tracks.filter(t=>t.items.some(i=>i.file));
}

function parseRppTempo(text){
  let bpm=120,numerator=4,denominator=4,inTempo=false;const points=[];
  for(const raw of text.split(/\r?\n/)){const line=raw.trim();
    if(line.startsWith("TEMPO ")){const p=line.split(/\s+/);bpm=Number(p[1])||bpm;numerator=Number(p[2])||numerator;denominator=Number(p[3])||denominator}
    if(line.startsWith("<TEMPOENVEX")){inTempo=true;continue}
    if(inTempo&&line===">"){inTempo=false;continue}
    if(inTempo&&line.startsWith("PT ")){const p=line.split(/\s+/),time=Number(p[1]),pointBpm=Number(p[2]);if(Number.isFinite(time)&&pointBpm>0)points.push({time,bpm:pointBpm})}
  }
  if(!points.length||points[0].time>0)points.unshift({time:0,bpm});
  return{bpm:points[0].bpm,numerator,denominator,points:points.sort((a,b)=>a.time-b.time)};
}

function parseRppSections(text){
  const markers=[],regions=[],open=new Map(),tokens=line=>{const out=[];line.replace(/"((?:\\.|[^"])*)"|(\S+)/g,(_,q,w)=>{out.push(q!==undefined?q.replace(/\\"/g,'"'):w);return""});return out};
  for(const raw of text.split(/\r?\n/)){const line=raw.trim();if(!line.startsWith("MARKER "))continue;
    const p=tokens(line),id=p[1],time=Number(p[2]),name=p[3]||"",hasRegionFlag=p.includes("R");
    if(!Number.isFinite(time))continue;const nativeColor=Number(p[5])||0,color=(nativeColor&0x1000000)?"#"+[nativeColor&255,(nativeColor>>8)&255,(nativeColor>>16)&255].map(v=>v.toString(16).padStart(2,"0")).join(""):"";
    /* REAPER grava o início da região com o token R. O número após o nome não é
       um booleano confiável: em alguns projetos ele pode ser 9 ou outro valor.
       O fechamento usa o mesmo ID, nome vazio e normalmente não contém R. */
    if(hasRegionFlag&&name){
      if(open.has(id)){const prev=open.get(id);if(time>prev.time)regions.push({name:prev.name||`Região ${id}`,start:prev.time,end:time,color:prev.color,type:"region"})}
      open.set(id,{name,time,color});
      continue;
    }
    if(open.has(id)&&!name){
      const start=open.get(id);if(time>start.time)regions.push({name:start.name||`Região ${id}`,start:start.time,end:time,color:start.color,type:"region"});
      open.delete(id);continue;
    }
    if(name)markers.push({name,time,type:"marker"});
  }
  return{markers:markers.sort((a,b)=>a.time-b.time),regions:regions.sort((a,b)=>a.start-b.start)};
}

class VSStudy{
  constructor(host){this.host=host;this.ctx=null;this.master=null;this.tracks=[];this.nodes=[];this.pitchGraph=[];this.pitchBuses=new Map();this.clickProcessor=null;this.workletReady=false;this.playing=false;this.offset=0;this.started=0;this.duration=0;this.loop=false;this.loopSection=null;this.pendingSection=null;this.sections=[];this.rawSections={markers:[],regions:[]};this.raf=0;this.tempo={bpm:120,numerator:4,denominator:4,points:[{time:0,bpm:120}]};this.projectKey="";this.liveTranspose=0;this.lastStatusSectionIndex=-1;this.clickBuffer=null;this.metroTimer=0;this.nextBeat=0;this.clickSource="native";this.standaloneClickActive=false;this.standaloneMetroTimer=0;this.standaloneClickNodes=[];this.standaloneNextBeatTime=0;this.standaloneBeatIndex=0;this.standaloneBpm=120;this.tapTimes=[];this.standaloneAnchorTime=0;this.standaloneStopAt=0;this.projectTempo=null;this.savedProjectTempo=null;this.tempoSource="editable";this.freeTempoOverride=false;this.projectLaunchAt=0;this.timelineZoom=1;this.mappingRegions=false;this.regionMarks=[];this.selectedRegionIndex=-1;this.snapEnabled=true;this.mappingCursor=0;this.currentProjectName="";this.currentProjectDir="";this.currentProjectFiles=[];this.projectManifest=null;this.groupStates=new Map();this.vsVolume=1;this.vsMute=false;this.vsSolo=false;this.pendingReplaceTrack=null;
this.padBuffers=new Map();
this.padPlaying=false;
this.padNote=null;
this.padManualOverride=false;
this.padVoices=[];
this.padScheduleTimer=0;
this.padLoopToken=0;
this.padNextStartAt=0;
this.padLastVoice=null;
this.padBus=null;
this.padPanner=null;
this.padVolume=1;
this.padPan=0;
try{const pm=JSON.parse(localStorage.getItem("zosma-zlplay:pad-mix")||"{}");this.padVolume=Math.max(0,Math.min(1.5,Number(pm.volume??1)));this.padPan=Math.max(-1,Math.min(1,Number(pm.pan??0)))}catch{}
this.padRequestId=0;
this.controlMapMode=false;
this.controlMapTarget=null;
this.controlMapMidiAccess=null;
this.controlMapCcState=new Map();
this.controlMapMidiBound=false;
this.controlMappings={};
try{this.controlMappings=JSON.parse(localStorage.getItem("zosma-zlplay:control-mappings")||"{}")||{}}catch{}
this.render();
this.bindPadNoteButtons();
this.installControlMappingHandlers()}
  render(){this.host.innerHTML=`<section class="vs-study"><div class="vs-card"><div class="vs-head"><div class="vs-head-main"><button class="vs-back" data-back>← Voltar</button><div class="vs-brand">
  <div class="vs-brand-line">
    <h2><span class="zl-brand-wordmark">ZL<span>Play</span></span></h2>
    <a class="vs-support-btn"
       href="https://1bytelabs.com.br"
       target="_blank"
       rel="noopener noreferrer"
       title="Conhecer a 1Byte Labs"
       aria-label="Conhecer a 1Byte Labs">1Byte Labs</a>
  </div>
  <p>Abra suas multitracks diretamente no computador.</p>
</div></div><div class="vs-head-actions"><button class="vs-mode" type="button" data-control-map>Mapear</button><span class="vs-map-actions" data-map-actions><button class="vs-mode" type="button" data-map-import>Importar</button><button class="vs-mode" type="button" data-map-export>Exportar</button><button class="vs-mode" type="button" data-map-clear>Limpar mapeamentos</button><input class="vs-file-input" data-map-file type="file" accept=".zlmap,application/json"></span><button class="vs-mode" data-mode>Modo Ensaio</button></div></div><div class="vs-actions"><button class="vs-btn" data-pick-folder>Abrir pasta / projeto</button><button class="vs-btn secondary" data-pick-library>Abrir repertório</button><button class="vs-btn secondary" data-pick-zip>Abrir ZIP</button></div><input class="vs-file-input" data-folder type="file" webkitdirectory multiple><input class="vs-file-input" data-library type="file" webkitdirectory multiple><input class="vs-file-input" data-zip type="file" accept=".zip,application/zip"><input class="vs-file-input" data-add-track type="file" accept="audio/*,.wav,.mp3,.flac,.ogg,.opus,.m4a,.aac" multiple><input class="vs-file-input" data-replace-track type="file" accept="audio/*,.wav,.mp3,.flac,.ogg,.opus,.m4a,.aac"><div class="vs-status done"><span data-status-text>Os arquivos permanecem no seu dispositivo.</span><div class="vs-progress"><i data-status-progress></i></div></div><div class="vs-rpp-picker" hidden></div><div class="vs-library-picker" hidden></div><div class="vs-project"><div class="vs-title-row"><div><div class="vs-title"></div><div class="vs-meta"></div></div><div class="vs-project-actions"><button class="vs-mode" data-map-regions disabled>Regiões</button><button class="vs-mode" data-save-project disabled>Salvar projeto</button></div><div class="vs-key"></div></div><div class="vs-region-mapper" hidden><div class="vs-mapper-head"><strong>Mapeamento de regiões</strong><span>Marque o fim de cada parte. Cada região é criada para a esquerda, desde o fim da anterior.</span></div><div class="vs-mapper-controls"><span class="vs-mapper-position" data-mapper-position>Cursor 0:00</span><select class="vs-select vs-region-preset-select" data-region-preset-select aria-label="Escolher região pronta"><option value="">Escolher região…</option></select><input class="vs-text-input" data-custom-region-name type="text" placeholder="Ou digite um nome livre"><button class="vs-btn secondary" type="button" data-add-custom-region>Adicionar região</button><button class="vs-btn secondary" data-close-mapper>Concluir</button></div><div class="vs-region-editor" data-region-editor hidden>
  <div class="vs-region-editor-info">
    <strong data-selected-region-name>Nenhuma região selecionada</strong>
    <span data-selected-region-time></span>
  </div>
  <input class="vs-text-input" data-selected-region-input type="text" placeholder="Nome da região">
  <button class="vs-btn secondary" type="button" data-edit-region>Salvar nome</button>
  <button class="vs-btn danger" type="button" data-delete-region>Excluir</button>
</div>
<div class="vs-mapper-empty" data-region-editor-empty>Clique em uma região na faixa abaixo para editar ou excluir.</div></div><div class="vs-timeline" aria-label="Timeline das regiões"></div><div class="vs-transport"><button class="vs-btn" data-play>Play</button><button class="vs-btn secondary" data-pause>Pause</button><button class="vs-btn secondary vs-go-start" data-go-start type="button" aria-label="Voltar ao início" title="Voltar ao início">|◀</button><button class="vs-btn secondary" data-stop>Stop</button><span class="vs-transport-status" data-transport-status></span><button class="vs-snap-toggle active" type="button" data-snap-toggle aria-pressed="true" aria-label="Ativar ou desativar snap na grade" title="Snap na grade"><span class="vs-snap-icon" aria-hidden="true"></span></button><input class="vs-range" data-progress type="range" min="0" max="0" step="0.01" value="0"><span class="vs-time">0:00 / 0:00</span></div><div class="vs-metronome"><label class="vs-click-source-control">Click <select data-click-source><option value="project">Track do projeto</option><option value="native">Nativo ZLPlay</option></select></label><input data-metro type="checkbox" checked hidden><label class="vs-bpm vs-bpm-edit">BPM <input data-bpm-input type="number" min="20" max="400" step="0.01" value="120"></label><button class="vs-tap-tempo" type="button" data-tap-tempo>Tap 120</button><select class="vs-meter-select" data-meter aria-label="Compasso"><option value="2">2/4</option><option value="3">3/4</option><option value="4" selected>4/4</option><option value="6">6/8</option></select><div class="vs-accent-group"><label class="vs-switch"><input data-accent type="checkbox"> Acentuar</label><select class="vs-accent-select" data-accent-position aria-label="Posição da acentuação"><option value="first">Primeiro tempo</option><option value="last">Último tempo</option></select></div><div class="vs-pad-row"><div class="vs-pad-panel" aria-label="Pads"><strong>Pad</strong><div class="vs-pad-notes">${PAD_NOTES.map(note=>`<button type="button" class="vs-pad-note" data-pad-note="${note}" title="Pad ${note}">${note}</button>`).join("")}</div></div><div class="vs-zoom-controls" aria-label="Zoom da timeline"><button class="vs-btn secondary" type="button" data-zoom-out aria-label="Diminuir zoom">−</button><span data-zoom-label>100%</span><button class="vs-btn secondary" type="button" data-zoom-in aria-label="Aumentar zoom">+</button></div></div><input data-metro-volume type="range" min="0" max="1.5" step="0.01" value="1" hidden><span data-metro-label hidden>100%</span></div><div class="vs-mixer-tools"><strong>Mixer</strong><div class="vs-mixer-actions"><button class="vs-mode" type="button" data-add-track-btn>+ Track</button><button class="vs-mode" type="button" data-add-group>+ Grupo</button><button class="vs-mode" data-restore>Restaurar mix original</button></div></div><div class="vs-mixer-body"><aside class="vs-master vs-master-strip"><strong>MASTER</strong><div class="vs-master-fader-wrap"><input class="vs-channel-fader" data-master data-map-control="volume-master" type="range" min="0" max="1" step="0.01" value="1"></div><span class="vs-volume" data-master-label>100%</span><small>GERAL</small></aside><div class="vs-tracks"></div></div><div class="vs-warning"></div></div></div></section>`;
    const $=s=>this.host.querySelector(s);this.$=$;$(`[data-control-map]`)?.addEventListener("click",()=>this.toggleControlMapping());$("[data-map-export]")?.addEventListener("click",()=>this.exportControlMappings());$("[data-map-import]")?.addEventListener("click",()=>$("[data-map-file]")?.click());$("[data-map-clear]")?.addEventListener("click",()=>this.clearAllControlMappings());const mapFile=$("[data-map-file]");if(mapFile)mapFile.onchange=async e=>{const file=e.target.files?.[0];e.target.value="";if(file)await this.importControlMappings(file)};$("[data-mode]").disabled=true;$("[data-back]").onclick=()=>{location.hash=""};$("[data-mode]").onclick=()=>this.toggleMode();$("[data-restore]").onclick=()=>this.restoreOriginalMix();$("[data-pick-folder]").onclick=()=>$("[data-folder]").click();$("[data-pick-library]").onclick=()=>$("[data-library]").click();$("[data-pick-zip]").onclick=()=>$("[data-zip]").click();$("[data-folder]").onchange=e=>this.openFolderSelection([...e.target.files]);$("[data-library]").onchange=e=>this.openLibrarySelection([...e.target.files]);$("[data-zip]").onchange=async e=>{if(!e.target.files[0])return;this.status("Lendo ZIP...",false,4);try{this.load(await new ZipReader(e.target.files[0]).files())}catch(x){this.status(x.message,true)}};$("[data-add-track-btn]").onclick=()=>$("[data-add-track]").click();$("[data-add-track]").onchange=async e=>{const files=[...e.target.files];e.target.value="";if(files.length)await this.addTracks(files)};$("[data-add-group]").onclick=()=>this.createGroup();$("[data-replace-track]").onchange=async e=>{const file=e.target.files[0];e.target.value="";if(file)await this.replacePendingTrack(file)};$("[data-play]").onclick=()=>this.play();$("[data-pause]").onclick=()=>this.pause();$("[data-go-start]").onclick=()=>this.goToStart();$("[data-stop]").onclick=()=>this.stop();$("[data-progress]").oninput=e=>{const t=this.mappingRegions?this.setMappingCursor(+e.target.value,false):(+e.target.value);this.seek(t)};$("[data-master]").oninput=e=>{if(this.master)this.master.gain.value=+e.target.value;$("[data-master-label]").textContent=`${Math.round(e.target.value*100)}%`};$("[data-metro-volume]").oninput=e=>{$("[data-metro-label]").textContent=`${Math.round(e.target.value*100)}%`};$("[data-metro]").onchange=()=>{if(this.playing)this.restartMetro()};const clickSource=$("[data-click-source]");if(clickSource)clickSource.onchange=e=>{this.clickSource=e.target.value;this.applyClickSource();this.drawTracks();this.status(this.clickSource==="native"?"Click nativo ZLPlay selecionado.":"Click do projeto selecionado.");};$("[data-meter]").onchange=e=>{const v=+e.target.value;this.tempo.numerator=v;this.tempo.denominator=v===6?8:4;this.drawSections();this.renderRegionMenu();if(this.playing)this.restartMetro();else if(this.standaloneClickActive)this.restartStandaloneClick()};$("[data-accent]").onchange=()=>{if(this.standaloneClickActive)this.restartStandaloneClick()};const sectionSelect=$("[data-section]"),loopCheck=$("[data-loop]");if(sectionSelect)sectionSelect.onchange=e=>{this.selectSection(+e.target.value,true)};if(loopCheck)loopCheck.onchange=e=>this.loop=e.target.checked;$("[data-zoom-out]").onclick=()=>this.setTimelineZoom(this.timelineZoom-.5);$("[data-zoom-in]").onclick=()=>this.setTimelineZoom(this.timelineZoom+.5);$("[data-bpm-input]").onchange=e=>this.applyManualBpm(e.target.value);$("[data-tap-tempo]").onclick=()=>this.tapTempo();$("[data-map-regions]").onclick=()=>this.toggleRegionMapper();$("[data-save-project]").onclick=()=>this.saveProjectFile();$("[data-snap-toggle]").onclick=()=>this.toggleSnap();$("[data-close-mapper]").onclick=()=>this.toggleRegionMapper(false);$("[data-add-custom-region]").onclick=()=>{const input=$("[data-custom-region-name]"),name=input.value.trim();if(!name)return;this.addRegionMark(name,"#65507f");input.value=""};$("[data-custom-region-name]").onkeydown=e=>{if(e.key==="Enter"){e.preventDefault();$("[data-add-custom-region]").click()}};$("[data-edit-region]").onclick=()=>this.saveSelectedRegionName();$("[data-delete-region]").onclick=()=>this.deleteSelectedRegion();if(!this._spaceHandler){this._spaceHandler=e=>{if(e.code!=="Space"||e.repeat)return;const t=e.target,tag=t?.tagName;if(t?.isContentEditable||tag==="INPUT"||tag==="TEXTAREA"||tag==="SELECT"||tag==="BUTTON")return;if(!this.tracks.length)return;e.preventDefault();this.playing?this.pause():this.play()};document.addEventListener("keydown",this._spaceHandler)}}
  wrap(f,name){return{name,size:f.size,arrayBuffer:()=>f.arrayBuffer(),text:()=>f.text()}}
  status(msg,error=false,progress=null){const e=this.$(".vs-status");this.$("[data-status-text]").textContent=msg;const ts=this.$("[data-transport-status]");if(ts)ts.textContent=msg;e.classList.toggle("error",error);e.classList.toggle("done",progress===null);if(progress!==null)this.$("[data-status-progress]").style.width=`${Math.max(0,Math.min(100,progress))}%`}
  panLabel(value){return Math.abs(value)<.01?"C":value<0?`L${Math.round(Math.abs(value)*100)}`:`R${Math.round(value*100)}`}
  isClickTrack(t){return /(^|\b)(click|claqueta|metronom)(\b|$)/i.test(t?.name||"")}
  isGuideTrack(t){return /(^|\b)(guia|guide)(\b|$)/i.test(t?.name||"")}
  isSpecialTrack(t){return this.isClickTrack(t)||this.isGuideTrack(t)}
  ensureGroupState(name){name=(name||"VS").trim()||"VS";if(!(this.groupStates instanceof Map))this.groupStates=new Map();if(!this.groupStates.has(name)){const legacy=this.groupPans instanceof Map?Number(this.groupPans.get(name)||0):0;this.groupStates.set(name,{volume:1,pan:legacy,mute:false,solo:false})}const st=this.groupStates.get(name);st.volume=Number.isFinite(Number(st.volume))?Number(st.volume):1;st.pan=Number.isFinite(Number(st.pan))?Number(st.pan):0;st.mute=!!st.mute;st.solo=!!st.solo;return st}
  musicalOrder(name){const n=this.normalizeTrackLabel(name).replace(/[_-]+/g," ");const exact=(...a)=>a.includes(n);const has=re=>re.test(n);
    /* específicos primeiro: abreviações comuns de stems também contam */
    if(has(/\b(synth\s*bass|bass\s*synth)\b/))return 40;
    if(has(/\b(acoustic\s*guitar|acoustic|violao|violão)\b/)||/^ag(?:\s*\d+)?$/.test(n))return 20;
    if(has(/\b(background\s*vocals?|backing\s*vocals?|bgvs?|choir|coral|vocals?|voice|voz|vozes)\b/))return 60;
    if(has(/\b(drums?|bateria|percussao|percussão|percussion|perc|shaker|tambourine|pandeiro|cajon|cajón|clap|snare|kick|hi\s*hat|hihat|cymbals?|pratos?|toms?)\b/)||exact("loop"))return 50;
    /* EG / EG 1 / EG1 = Electric Guitar */
    if(has(/\b(electric\s*guitar|guitars?|guitarra|gtr)\b/)||/^eg\s*\d*$/.test(n))return 30;
    if(has(/\b(bass|baixo)\b/))return 40;
    /* Rhodes é teclado; inclui também abreviações usuais de teclas */
    if(has(/\b(rhodes|keys?|keyboard|teclado|piano|synth\s*keys?|organ|orgao|órgão|strings?|string|pad|wurlitzer|wurli)\b/))return 10;
    return 45;
  }
  compareMusical(a,b){const pa=this.musicalOrder(a?.name||a),pb=this.musicalOrder(b?.name||b);return pa-pb||String(a?.name||a).localeCompare(String(b?.name||b),"pt-BR",{numeric:true,sensitivity:"base"})}
  groupMusicalOrder(name,tracks=[]){const byName=this.musicalOrder(name);if(byName!==45)return byName;const members=tracks.filter(t=>(t.group||"VS")===name);if(!members.length)return 45;const counts=new Map();for(const t of members){const p=this.musicalOrder(t.name);counts.set(p,(counts.get(p)||0)+1)}return [...counts.entries()].sort((a,b)=>b[1]-a[1]||a[0]-b[0])[0][0]}
  sortGroupNames(names,tracks=[]){return [...names].sort((a,b)=>this.groupMusicalOrder(a,tracks)-this.groupMusicalOrder(b,tracks)||String(a).localeCompare(String(b),"pt-BR",{numeric:true,sensitivity:"base"}))}
  allGroupNames(){const names=new Set();for(const t of this.tracks){if(this.isSpecialTrack(t))continue;const name=(t.group||"VS").trim()||"VS";if(name!=="VS")names.add(name)}if(this.groupStates instanceof Map)for(const name of this.groupStates.keys())if(name&&name!=="VS")names.add(name);return this.sortGroupNames(names,this.tracks)}
  recalcDuration(){let d=0;for(const t of this.tracks)for(const it of t.items||[])d=Math.max(d,(Number(it.position)||0)+(it.length==null?Math.max(0,(it.buffer?.duration||0)-(Number(it.offset)||0)):Number(it.length)||0));this.duration=d;const progress=this.$("[data-progress]");if(progress)progress.max=d}
  refreshProjectMeta(){const meta=this.$(".vs-meta");if(!meta||!this.currentProjectName)return;const tom=this.projectManifest?'<span class="vs-pill">Projeto .zlplay</span>':"";meta.innerHTML=`<span class="vs-pill">${this.tracks.length} tracks</span><span class="vs-pill">${fmt(this.duration)}</span><span class="vs-pill">Compasso ${this.tempo.numerator}/${this.tempo.denominator}</span>${this.sections.length?`<span class="vs-pill">${this.sections.length} ${this.sections.length===1?"região":"regiões"}</span>`:""}${tom}`}
  createGroup(){const raw=window.prompt("Nome do novo subgrupo:","");if(raw===null)return;const name=raw.trim();if(!name)return;if(/^(click|guia|guide|vs|master)$/i.test(name)){this.status("Escolha outro nome para o subgrupo.",true);return}if(this.allGroupNames().some(g=>g.toLowerCase()===name.toLowerCase())){this.status(`O grupo ${name} já existe.`,true);return}this.ensureGroupState(name);this.drawTracks();this.status(`Grupo ${name} criado. Use o menu ⋮ das tracks para movê-las.`)}
  renameGroup(oldName){const raw=window.prompt("Novo nome do grupo:",oldName);if(raw===null)return;const name=raw.trim();if(!name||name===oldName)return;if(/^(click|guia|guide|vs|master)$/i.test(name)){this.status("Escolha outro nome para o subgrupo.",true);return}if(this.allGroupNames().some(g=>g.toLowerCase()===name.toLowerCase())){this.status(`O grupo ${name} já existe.`,true);return}const state=this.groupStates?.get(oldName)||{volume:1,pan:0,mute:false,solo:false};this.groupStates?.delete(oldName);this.groupStates?.set(name,state);for(const t of this.tracks)if((t.group||"VS")===oldName)t.group=name;this.drawTracks();this.updateGains();this.updatePans();this.save();this.status(`Grupo ${oldName} renomeado para ${name}.`)}
  deleteGroup(name){if(!name||name==="VS")return;if(!window.confirm(`Excluir o grupo "${name}"? As tracks serão movidas para o Bus VS.`))return;for(const t of this.tracks)if((t.group||"VS")===name)t.group="VS";this.groupStates?.delete(name);this.groupPans?.delete(name);this.drawTracks();this.updateGains();this.updatePans();this.save();this.status(`Grupo ${name} excluído. As tracks foram movidas para o Bus VS.`)}
  moveTrackToGroup(t,name){name=(name||"VS").trim()||"VS";this.ensureGroupState(name);t.group=name;this.drawTracks();this.updateGains();this.updatePans();this.save();this.status(`${t.name} movida para ${name}.`)}
  deleteTrack(t){if(!window.confirm(`Remover "${t.name}" deste projeto?\n\nO arquivo de áudio não será apagado do dispositivo.`))return;const was=this.playing;this.stop();this.tracks=this.tracks.filter(x=>x!==t);this.recalcDuration();this.buildSections();this.drawTracks();this.drawSections();this.refreshProjectMeta();this.save();this.status(`${t.name} removida do projeto. O arquivo original permanece no dispositivo.`)}
  uniqueTrackName(base){let name=(base||"Track").trim()||"Track",n=2;const used=new Set(this.tracks.map(t=>t.name.toLowerCase()));if(!used.has(name.toLowerCase()))return name;while(used.has(`${name} ${n}`.toLowerCase()))n++;return `${name} ${n}`}
  async addTracks(nativeFiles){const files=nativeFiles.filter(f=>AUDIO_EXT.test(f.name));if(!files.length){this.status("Nenhum arquivo de áudio válido foi selecionado.",true);return}this.stop();let added=0;for(const file of files){try{this.status(`Adicionando ${file.name}...`,false,Math.min(90,10+added/files.length*70));const wrapped=this.wrap(file,file.name),buffer=await this.decodeProjectAudio(file),name=this.uniqueTrackName(baseName(file.name).replace(/\.[^.]+$/,"")),click=/(^|\b)(click|claqueta|metronom)(\b|$)/i.test(name);const track={name,group:"VS",volume:1,originalVolume:1,mute:click,solo:false,originalMute:click,originalSolo:false,pan:0,pitchEnabled:undefined,items:[{position:0,offset:0,length:null,pitch:0,file:file.name,projectFile:baseName(file.name),buffer}],restored:true,addedManually:true};this.tracks.push(track);this.currentProjectFiles.push(wrapped);added++}catch(e){console.error(e);this.status(`Não foi possível adicionar ${file.name}.`,true)}}this.recalcDuration();this.buildSections();this.drawTracks();this.drawSections();this.refreshProjectMeta();this.save();this.status(`${added} track(s) adicionada(s). Para manter o .zlplay portátil, deixe esses arquivos na mesma pasta do projeto.`)}
  requestReplaceTrack(t){this.pendingReplaceTrack=t;this.$("[data-replace-track]").click()}
  async replacePendingTrack(file){const t=this.pendingReplaceTrack;this.pendingReplaceTrack=null;if(!t)return;try{this.stop();this.status(`Substituindo áudio de ${t.name}...`,false,25);const buffer=await this.decodeProjectAudio(file),old=t.items?.[0]||{};t.items=[{position:Number(old.position)||0,offset:0,length:null,pitch:Number(old.pitch)||0,file:file.name,projectFile:baseName(file.name),buffer}];this.currentProjectFiles.push(this.wrap(file,file.name));this.recalcDuration();this.drawTracks();this.drawSections();this.refreshProjectMeta();this.save();this.status(`Áudio de ${t.name} substituído. O nome da track foi preservado.`)}catch(e){console.error(e);this.status(`Falha ao substituir o áudio de ${t.name}.`,true)}}
  timelineGridMetrics(){const bpm=this.tempo?.bpm||120,beatsPerBar=this.tempo?.numerator===6&&this.tempo?.denominator===8?3:(this.tempo?.numerator||4),beatSeconds=60/bpm,barSeconds=beatsPerBar*beatSeconds;return{bpm,beatsPerBar,beatSeconds,barSeconds}}
  updateTimelineGrid(){const timeline=this.host.querySelector(".vs-timeline");if(timeline&&this.duration){const {beatSeconds}=this.timelineGridMetrics(),beatWidth=beatSeconds/this.duration*100;timeline.style.setProperty("--beat-width",`${beatWidth}%`)}}
  setTimelineZoom(value,keepAnchor=true){const next=Math.max(1,Math.min(8,Math.round(value*2)/2)),scroll=this.host.querySelector(".vs-timeline-scroll"),content=this.host.querySelector(".vs-timeline-content"),oldWidth=content?.scrollWidth||0,ratio=this.duration?this.position()/this.duration:0,anchor=scroll&&oldWidth?ratio*oldWidth-scroll.scrollLeft:0;this.timelineZoom=next;if(content)content.style.width=`${next*100}%`;const label=this.host.querySelector("[data-zoom-label]");if(label)label.textContent=`${Math.round(next*100)}%`;const out=this.host.querySelector("[data-zoom-out]"),inc=this.host.querySelector("[data-zoom-in]");if(out)out.disabled=next<=1;if(inc)inc.disabled=next>=8;requestAnimationFrame(()=>{if(scroll&&content&&keepAnchor&&oldWidth){const target=ratio*content.scrollWidth-anchor;scroll.scrollLeft=Math.max(0,Math.min(scroll.scrollWidth-scroll.clientWidth,target))}const canvas=this.host.querySelector(".vs-timeline-waveform");if(canvas)this.drawTimelineWaveform(canvas);this.updateTimelineGrid()})}
  addLiveControls(){const stop=this.host.querySelector("[data-stop]");if(!stop||this.host.querySelector("[data-solo-click]"))return;const solo=document.createElement("button"),loop=document.createElement("button"),padPlay=document.createElement("button"),padStop=document.createElement("button");solo.type=loop.type=padPlay.type=padStop.type="button";solo.className=loop.className="vs-btn secondary vs-live-toggle";padPlay.className=padStop.className="vs-btn secondary vs-pad-transport";solo.dataset.soloClick="";loop.dataset.loopCurrent="";padPlay.dataset.padPlay="";padStop.dataset.padStop="";solo.textContent="Solo Click";loop.textContent="Loop";padPlay.textContent="Pad ▶";padStop.textContent="Pad ■";solo.onclick=()=>this.toggleSoloClick();loop.onclick=()=>this.toggleCurrentLoop();padPlay.onclick=()=>this.playPadManual();padStop.onclick=()=>this.stopPad();
    const transport=this.host.querySelector(".vs-transport"),play=this.host.querySelector("[data-play]"),pause=this.host.querySelector("[data-pause]"),goStart=this.host.querySelector("[data-go-start]"),snap=this.host.querySelector("[data-snap-toggle]"),status=this.host.querySelector("[data-transport-status]");
    if(transport){[play,pause,stop,solo,loop,goStart,padPlay,padStop,snap,status].forEach(el=>{if(el)transport.append(el)})}this.updatePadUi();const repeat=this.host.querySelector("[data-loop]");repeat?.addEventListener("change",()=>this.updateGains());const oldPaint=this.paint.bind(this);this.paint=p=>{oldPaint(p);this.updateTimelineGrid()}}
  goToStart(){this.pause();this.offset=0;this.pendingSection=null;this.currentLoop=false;this.currentLoopSection=null;this.lastStatusSectionIndex=-1;this.paint(0);this.$("[data-progress]").value=0;this.status("Posição voltou ao início da música.")}
  toggleSoloClick(){this.soloClick=!this.soloClick;const button=this.host.querySelector("[data-solo-click]"),metro=this.host.querySelector("[data-metro]"),fade=1.5;button?.classList.toggle("on",this.soloClick);if(!this.playing){if(this.soloClick){this.startStandaloneClick();this.status(`Click nativo livre: ${Math.round(this.standaloneBpm)} BPM.`)}else{this.stopStandaloneClick();this.status("Click livre parado.")}this.updateRegionLoopFeedback();return}if(this.soloClick){this.toggleCurrentLoop(true,fade);if(this.clickSource==="native"&&metro&&!metro.checked){metro.checked=true;this.restartMetro()}}else this.toggleCurrentLoop(false,fade)}
  updateRegionLoopFeedback(){const active=this.currentLoop&&this.currentLoopSection,idx=active?this.sections.findIndex(s=>s===this.currentLoopSection||(Math.abs(s.start-this.currentLoopSection.start)<.001&&Math.abs(s.end-this.currentLoopSection.end)<.001)):-1;this.host.querySelectorAll(".vs-segment").forEach((el,i)=>{el.classList.toggle("looping",i===idx);el.classList.toggle("solo-click-region",i===idx&&this.soloClick&&this.playing)})}
  toggleCurrentLoop(force,fadeSeconds=0){const enabled=typeof force==="boolean"?force:!this.currentLoop,button=this.host.querySelector("[data-loop-current]");if(enabled){const section=this.sectionAt(this.position());if(!section){this.status("A reprodução não está dentro de uma região.");return}this.currentLoop=true;this.currentLoopSection=section;button?.classList.add("on");this.updateRegionLoopFeedback();this.updateGains(fadeSeconds);this.status(`Loop ativo: ${section.name}.`)}else{this.currentLoop=false;this.currentLoopSection=null;button?.classList.remove("on");this.updateRegionLoopFeedback();this.updateGains(fadeSeconds)}}
  toggleMode(){const rehearsal=this.host.classList.toggle("vs-mode-rehearsal");this.$("[data-mode]").textContent=rehearsal?"Voltar ao Mixer":"Modo Ensaio";window.scrollTo({top:0,behavior:"smooth"})}
  restoreOriginalMix(){for(const t of this.tracks)Object.assign(t,{volume:t.originalVolume,mute:t.originalMute,solo:t.originalSolo,pan:0,restored:false});this.vsPan=0;this.vsVolume=1;this.vsMute=false;this.vsSolo=false;this.groupPans=new Map;this.groupStates=new Map;for(const t of this.tracks)if(!this.isSpecialTrack(t))this.ensureGroupState(t.group||"VS");try{localStorage.removeItem("zosma-zlplay:"+this.projectId)}catch{}this.drawTracks();this.updateGains();this.updatePans();this.status("Mix original restaurado.")}
  cleanPath(path){return String(path||"").replace(/\\/g,"/").replace(/^\.\//,"").replace(/\/+/g,"/")}
  dirName(path){const p=this.cleanPath(path),i=p.lastIndexOf("/");return i>=0?p.slice(0,i):""}
  relativeToProject(path){const p=this.cleanPath(path),root=this.currentProjectDir?this.cleanPath(this.currentProjectDir)+"/":"";return root&&p.startsWith(root)?p.slice(root.length):baseName(p)}
  async openFolderSelection(nativeFiles){const list=[...nativeFiles],parts=f=>String(f.webkitRelativePath||f.name||"").replace(/\\/g,"/").split("/").filter(Boolean),depths=list.map(f=>parts(f).length).filter(Boolean),rootDepth=depths.length?Math.min(...depths):1,rootFiles=list.filter(f=>parts(f).length===rootDepth);if(!rootFiles.length){this.status("Nenhum arquivo válido foi encontrado na raiz da pasta.",true);return}const ignored=list.length-rootFiles.length;if(ignored>0)console.info(`[ZLPlay] ${ignored} arquivo(s) de subpastas ignorado(s).`);const files=rootFiles.map(f=>this.wrap(f,f.webkitRelativePath||f.name)),projects=files.filter(f=>/\.zlplay$/i.test(f.name));if(projects.length===1){await this.loadTomCerto(files,projects[0]);return}if(projects.length>1){this.showLibrary(files,projects,"Projetos encontrados nesta pasta");return}this.load(files)}
  async openLibrarySelection(nativeFiles){const files=nativeFiles.map(f=>this.wrap(f,f.webkitRelativePath||f.name)),projects=files.filter(f=>/\.zlplay$/i.test(f.name));if(!projects.length){this.status("Nenhum projeto .zlplay foi encontrado nesso repertório.",true);return}this.showLibrary(files,projects,"Repertório Tom Certo")}
  async readManifest(file){const data=JSON.parse(await file.text());if(!data||data.format!=="tom-certo-project")throw Error("Arquivo .zlplay inválido.");if((data.formatVersion||1)>1)throw Error("Este projeto foi criado por uma versão mais nova do Tom Certo.");return data}
  showLibrary(files,projects,title){const box=this.$(".vs-library-picker");box.hidden=false;box.innerHTML=`<h3>${esc(title)}</h3><p>${projects.length} projeto(s) disponível(is). Cada projeto usa somente arquivos da própria pasta.</p><div class="vs-library-grid">${projects.map((p,i)=>`<button type="button" data-library-index="${i}"><strong>${esc(baseName(p.name).replace(/\.zlplay$/i,""))}</strong><small>${esc(this.dirName(p.name)||"Pasta selecionada")}</small></button>`).join("")}</div>`;box.querySelectorAll("[data-library-index]").forEach(b=>b.onclick=async()=>{box.hidden=true;await this.loadTomCerto(files,projects[+b.dataset.libraryIndex])});this.status("Escolha uma música do repertório.")}
  async loadTomCerto(files,manifestFile){try{this.status("Abrindo projeto Tom Certo...",false,8);const manifest=await this.readManifest(manifestFile),projectDir=this.dirName(manifestFile.name),prefix=projectDir?projectDir+"/":"",scoped=files.filter(f=>{const path=this.cleanPath(f.name);return prefix?path.startsWith(prefix):!path.includes("/")});const lookup=new Map(scoped.map(f=>[this.cleanPath(f.name),f])),audioFiles=[],missing=[];for(const t of manifest.tracks||[])for(const it of t.items||[{file:t.file}]){const rel=this.cleanPath(it.file);if(rel.split("/").includes("..")||rel.startsWith("/")){missing.push(rel);continue}const full=prefix+rel,f=lookup.get(full);if(f&&!audioFiles.includes(f))audioFiles.push(f);else if(!f)missing.push(rel)}if(missing.length)throw Error(`Arquivos do projeto não encontrados na própria pasta: ${[...new Set(missing)].join(", ")}`);await this.loadProjectManifest(scoped,manifest,manifestFile,projectDir)}catch(e){console.error(e);this.status(e.message||"Não foi possível abrir o projeto .zlplay.",true)}}
  async loadProjectManifest(files,manifest,manifestFile,projectDir){this.stop();this.tracks=[];this.duration=0;this.groupStates=new Map();this.vsVolume=1;this.vsMute=false;this.vsSolo=false;this.currentProjectDir=projectDir||"";this.currentProjectFiles=files;this.projectManifest=manifest;this.tempoSource="zlplay";const prefix=this.currentProjectDir?this.currentProjectDir+"/":"",lookup=new Map(files.map(f=>[this.cleanPath(f.name),f])),decoded=new Map();this.tempo={bpm:Number(manifest.song?.bpm)||120,numerator:Number(manifest.song?.timeSignature?.numerator)||4,denominator:Number(manifest.song?.timeSignature?.denominator)||4,points:[{time:0,bpm:Number(manifest.song?.bpm)||120}]};this.rawSections={markers:[],regions:(manifest.regions||[]).map(r=>({name:String(r.name||"Região"),start:Number(r.start)||0,end:Number(r.end)||0,color:String(r.color||""),type:"region"}))};let n=0;for(const spec of manifest.tracks||[]){const items=[];for(const raw of spec.items||[{file:spec.file,position:0,offset:0,length:null,pitch:0}]){const rel=this.cleanPath(raw.file);if(rel.startsWith("/")||(rel.split("/").includes("..")&&!window.zlplayNative))continue;const f=lookup.get(prefix+rel);if(!f)continue;let buffer=decoded.get(f.name);if(!buffer){this.status(`Preparando track ${++n}...`,false,15+Math.min(70,n*5));buffer=await this.decodeProjectAudio(f);decoded.set(f.name,buffer)}const it={position:Number(raw.position)||0,offset:Number(raw.offset)||0,length:raw.length==null?null:Number(raw.length),pitch:Number(raw.pitch)||0,file:f.name,projectFile:rel,buffer};items.push(it);this.duration=Math.max(this.duration,it.position+(it.length||Math.max(0,buffer.duration-it.offset)))}if(items.length)this.tracks.push({name:String(spec.name||baseName(items[0].file).replace(/\.[^.]+$/,"")),group:String(spec.group||"VS"),volume:Number(spec.volume??1),originalVolume:Number(spec.originalVolume??spec.volume??1),mute:!!spec.mute,solo:!!spec.solo,originalMute:!!spec.originalMute,originalSolo:!!spec.originalSolo,pan:Number(spec.pan)||0,output:spec.output||null,pitchEnabled:typeof spec.pitchEnabled==="boolean"?spec.pitchEnabled:undefined,items,restored:true})}if(!this.tracks.length)throw Error("Nenhuma track válida foi encontrada na pasta deste projeto.");this.vsPan=Number(manifest.mixer?.vsPan)||0;this.vsVolume=Number(manifest.mixer?.vsVolume??1);this.vsMute=!!manifest.mixer?.vsMute;this.vsSolo=!!manifest.mixer?.vsSolo;this.groupPans=new Map(manifest.mixer?.groupPans||[]);this.groupStates=new Map();for(const [name,state] of manifest.mixer?.groupStates||[])this.groupStates.set(name,{volume:Number(state?.volume??1),pan:Number(state?.pan??0),mute:!!state?.mute,solo:!!state?.solo,output:state?.output||null});for(const [name,pan] of this.groupPans)this.ensureGroupState(name).pan=Number(pan)||0;for(const t of this.tracks)if(!this.isSpecialTrack(t))this.ensureGroupState(t.group||"VS");this.metroPan=Number(manifest.settings?.metroPan)||0;this.timelineZoom=Math.max(1,Math.min(8,Number(manifest.settings?.timelineZoom)||1));this.snapEnabled=manifest.settings?.snapEnabled!==false;this.toggleSnap(this.snapEnabled);this.finishLoadedProject(manifest.song?.title||baseName(manifestFile.name).replace(/\.zlplay$/i,""),manifest.song?.key||"Não informado",false,true);this.status("Projeto .zlplay aberto. Os stems foram carregados somente da pasta deste projeto.")}
  finishLoadedProject(project,key,isOriginal=false,isTomCerto=false){this.projectTempo=JSON.parse(JSON.stringify(this.tempo));this.savedProjectTempo=this.tempoSource==="zlplay"?JSON.parse(JSON.stringify(this.tempo)):null;this.freeTempoOverride=false;this.buildSections();for(const t of this.tracks)if(!this.isSpecialTrack(t))this.ensureGroupState(t.group||"VS");this.projectId=`${project}|${this.tracks.map(t=>t.name).join("|")}`;this.clickSource="native";this.applyClickSource();this.drawTracks();this.drawSections();this.setMeterFromProject();this.$(".vs-title").textContent=project;this.currentProjectName=project;this.projectKey=key||"Não informado";this.liveTranspose=0;this.drawKeyControl(isOriginal);const bpmInput=this.$("[data-bpm-input]");if(bpmInput)bpmInput.value=String(Math.round(this.tempo.bpm*100)/100);this.$(".vs-meta").innerHTML=`<span class="vs-pill">${this.tracks.length} tracks</span><span class="vs-pill">${fmt(this.duration)}</span><span class="vs-pill">Compasso ${this.tempo.numerator}/${this.tempo.denominator}</span>${this.sections.length?`<span class="vs-pill">${this.sections.length} ${this.sections.length===1?"região":"regiões"}</span>`:""}${isTomCerto?'<span class="vs-pill">Projeto .zlplay</span>':''}`;this.$("[data-progress]").max=this.duration;this.$(".vs-project").classList.add("active");this.$("[data-mode]").disabled=false;this.$("[data-map-regions]").disabled=false;this.$("[data-save-project]").disabled=false;this.padManualOverride=false;this.padNote=this.padAutoNote();this.preloadPad(this.padNote);this.updatePadUi();this.standaloneBpm=Number(this.tempo.bpm)||120;this.tapTimes=[];this.updateTapTempoUi()}
  projectBpm(){return Math.max(20,Math.min(400,Number(this.projectTempo?.bpm??this.tempo?.bpm)||120))}
  restoreProjectTempo(useSaved=false){this.freeTempoOverride=false;const authoritative=useSaved&&this.tempoSource==="zlplay"&&this.savedProjectTempo?this.savedProjectTempo:this.projectTempo;if(authoritative){if(useSaved&&this.tempoSource==="zlplay")this.projectTempo=JSON.parse(JSON.stringify(authoritative));const numerator=this.tempo?.numerator??authoritative.numerator,denominator=this.tempo?.denominator??authoritative.denominator;this.tempo={...JSON.parse(JSON.stringify(authoritative)),numerator,denominator}}const bpm=Math.max(20,Math.min(400,Number(authoritative?.bpm??this.tempo?.bpm)||120));this.standaloneBpm=bpm;const input=this.$("[data-bpm-input]");if(input)input.value=String(Math.round(bpm*100)/100);this.updateTapTempoUi();this.drawSections();return bpm}
  applyManualBpm(value){const bpm=Math.max(20,Math.min(400,Number(value)||120));if(this.playing){const projectBpm=this.restoreProjectTempo();this.status(`Durante o VS o BPM permanece no projeto: ${projectBpm}.`);return}const freeMode=!!this.soloClick||!!this.standaloneClickActive||this.tempoSource==="rpp";this.standaloneBpm=bpm;this.tempo={...this.tempo,bpm,points:[{time:0,bpm}]};this.freeTempoOverride=freeMode;if(!freeMode&&this.tempoSource!=="rpp"){this.projectTempo=JSON.parse(JSON.stringify(this.tempo));}this.$("[data-bpm-input]").value=String(Math.round(bpm*100)/100);this.updateTapTempoUi();if(this.standaloneClickActive)this.restartStandaloneClick();this.drawSections();this.status(freeMode?`BPM livre definido em ${bpm}. O Play volta ao BPM original do projeto.`:`BPM do projeto definido em ${bpm}.`)}
  snapTime(time){const raw=Math.max(0,Math.min(this.duration,time));if(!this.snapEnabled)return raw;const {beatSeconds}=this.timelineGridMetrics();return Math.max(0,Math.min(this.duration,Math.round(raw/beatSeconds)*beatSeconds))}
  toggleSnap(force){this.snapEnabled=typeof force==="boolean"?force:!this.snapEnabled;const b=this.$("[data-snap-toggle]");if(b){b.classList.toggle("active",this.snapEnabled);b.setAttribute("aria-pressed",this.snapEnabled?"true":"false");b.title=this.snapEnabled?"Snap ligado: clique encaixa em qualquer linha de batida da grade":"Snap desligado: clique livre"}if(this.mappingRegions&&this.snapEnabled)this.mappingCursor=this.snapTime(this.mappingCursor);this.updateMapperPosition();this.status(this.snapEnabled?"Snap na grade ativado: todas as batidas são pontos de encaixe.":"Snap desativado: clique livre.")}
  renderRegionMenu(){const select=this.$("[data-region-preset-select]");if(!select)return;select.innerHTML='<option value="">Escolher região…</option>'+REGION_PRESETS.map((g,gi)=>`<optgroup label="${esc(g.title)}">${g.items.map((it,ii)=>`<option value="${gi}:${ii}">${esc(it.name)}</option>`).join("")}</optgroup>`).join("");select.value="";select.onchange=()=>{if(!select.value)return;const [gi,ii]=select.value.split(":").map(Number),it=REGION_PRESETS[gi]?.items[ii];if(it)this.addRegionMark(it.name,it.color);select.value=""}}
  setMappingCursor(time,doSeek=true){this.mappingCursor=this.snapTime(time);if(doSeek)this.seek(this.mappingCursor);this.updateMapperPosition();return this.mappingCursor}
  updateMapperPosition(){const e=this.$("[data-mapper-position]");if(!e)return;const raw=this.mappingRegions?this.mappingCursor:this.position(),snapped=this.snapTime(raw);e.textContent=`Cursor ${fmt(snapped)}${this.snapEnabled?" · grade":" · livre"}`}
  toggleRegionMapper(force){const next=typeof force==="boolean"?force:!this.mappingRegions;this.mappingRegions=next;const panel=this.$(".vs-region-mapper");panel.hidden=!next;if(next){this.regionMarks=this.sections.length?this.sections.map(s=>({name:s.name,start:s.end,color:s.color||""})):[];this.selectedRegionIndex=-1;this.mappingCursor=this.snapTime(this.position());this.renderRegionMenu();this.renderRegionEditor();this.toggleSnap(this.snapEnabled);this.updateMapperPosition();panel.scrollIntoView({behavior:"smooth",block:"nearest"})}else{this.selectedRegionIndex=-1;this.status("Mapeamento de regiões concluído.")}}
  addRegionMark(name="Região",color=""){const start=this.snapTime(this.mappingRegions?this.mappingCursor:this.position()),near=this.regionMarks.find(m=>Math.abs(m.start-start)<.01);if(near){near.name=name;near.color=color||near.color||""}else this.regionMarks.push({name,start,color});this.regionMarks.sort((a,b)=>a.start-b.start);this.commitRegionMarks();this.seek(start);this.status(`${name} marcado em ${fmt(start)}.`)}
  commitRegionMarks(){this.regionMarks=this.regionMarks.filter((m,i,a)=>m.start>0&&m.start<=this.duration&&(!i||Math.abs(m.start-a[i-1].start)>.005)).sort((a,b)=>a.start-b.start);this.rawSections.regions=this.regionMarks.map((m,i)=>({name:m.name,start:i?this.regionMarks[i-1].start:0,end:m.start,color:m.color||"",type:"region"})).filter(r=>r.end>r.start);this.rawSections.markers=[];this.buildSections();this.drawSections();if(this.selectedRegionIndex>=this.regionMarks.length)this.selectedRegionIndex=this.regionMarks.length-1;this.renderRegionEditor();this.updateMapperPosition();this.status(`${this.sections.length} região(ões) mapeada(s).`)}
  selectRegionForEdit(index){if(!this.mappingRegions)return;index=Number(index);if(index<0||index>=this.regionMarks.length)return;this.selectedRegionIndex=index;const mark=this.regionMarks[index];this.mappingCursor=mark.start;this.renderRegionEditor();this.updateMapperPosition();this.drawSections();this.status(`${mark.name} selecionada para edição.`)}
  renderRegionEditor(){const box=this.$("[data-region-editor]"),empty=this.$("[data-region-editor-empty]");if(!box||!empty)return;const i=this.selectedRegionIndex,mark=i>=0?this.regionMarks[i]:null;if(!mark){box.hidden=true;empty.hidden=false;return}box.hidden=false;empty.hidden=true;const start=i?this.regionMarks[i-1].start:0;this.$("[data-selected-region-name]").textContent=mark.name;this.$("[data-selected-region-time]").textContent=`${fmt(start)}–${fmt(mark.start)}`;const input=this.$("[data-selected-region-input]");input.value=mark.name}
  saveSelectedRegionName(){const i=this.selectedRegionIndex;if(i<0||!this.regionMarks[i])return;const input=this.$("[data-selected-region-input]"),name=input.value.trim();if(!name)return;this.regionMarks[i].name=name;this.commitRegionMarks();this.status(`Região renomeada para ${name}.`)}
  deleteSelectedRegion(){const i=this.selectedRegionIndex;if(i<0||!this.regionMarks[i])return;const name=this.regionMarks[i].name;this.regionMarks.splice(i,1);this.selectedRegionIndex=-1;this.commitRegionMarks();this.renderRegionEditor();this.status(`${name} excluída.`)}
  markProjectTempoSaved(){this.savedProjectTempo=JSON.parse(JSON.stringify(this.projectTempo||this.tempo));if(this.tempoSource!=="rpp")this.tempoSource="zlplay";this.freeTempoOverride=false}
  projectPayload(){return{format:"tom-certo-project",formatVersion:1,appVersion:"1.5.0-beta",song:{title:this.currentProjectName||"Projeto Tom Certo",bpm:this.projectBpm(),timeSignature:{numerator:this.tempo?.numerator||4,denominator:this.tempo?.denominator||4},key:this.projectKey||"Não informado",duration:this.duration},tracks:this.tracks.map(t=>({name:t.name,group:t.group||"VS",volume:t.volume,originalVolume:t.originalVolume,mute:t.mute,solo:t.solo,originalMute:t.originalMute,originalSolo:t.originalSolo,pan:t.pan||0,output:t.output||null,pitchEnabled:typeof t.pitchEnabled==="boolean"?t.pitchEnabled:undefined,items:(t.items||[]).map(it=>({file:it.projectFile||this.relativeToProject(it.file),position:it.position||0,offset:it.offset||0,length:it.length,pitch:it.pitch||0}))})),regions:this.sections.map(s=>({name:s.name,start:s.start,end:s.end,color:s.color||""})),mixer:{vsPan:this.vsPan||0,vsVolume:Number(this.vsVolume??1),vsMute:!!this.vsMute,vsSolo:!!this.vsSolo,groupPans:[...this.allGroupNames().map(name=>[name,this.ensureGroupState(name).pan])],groupStates:[...this.groupStates.entries()].map(([name,state])=>[name,{volume:Number(state.volume??1),pan:Number(state.pan??0),mute:!!state.mute,solo:!!state.solo,output:state.output||null}])},settings:{metroPan:this.metroPan||0,timelineZoom:this.timelineZoom||1,snapEnabled:this.snapEnabled!==false}}}
  async saveProjectFile(){if(!this.tracks.length)return;if(this.tempoSource!=="rpp"&&!this.freeTempoOverride){const input=this.$("[data-bpm-input]"),bpm=Math.max(20,Math.min(400,Number(input?.value)||Number(this.tempo?.bpm)||120));this.standaloneBpm=bpm;this.tempo={...this.tempo,bpm,points:[{time:0,bpm}]};this.projectTempo=JSON.parse(JSON.stringify(this.tempo));if(input)input.value=String(Math.round(bpm*100)/100);this.updateTapTempoUi()}const text=JSON.stringify(this.projectPayload(),null,2),safe=(this.currentProjectName||"projeto").replace(/[\\/:*?"<>|]+/g,"-").trim()||"projeto",name=`${safe}.zlplay`;try{if(window.showSaveFilePicker){const handle=await window.showSaveFilePicker({suggestedName:name,types:[{description:"Projeto Tom Certo",accept:{"application/json":[".zlplay"]}}]});const writable=await handle.createWritable();await writable.write(text);await writable.close();this.markProjectTempoSaved();await this.afterProjectSaved?.(handle,text);this.status(`Projeto salvo como ${name}. Mantenha-o na mesma pasta dos stems.`);return}}catch(e){if(e?.name==="AbortError")return}const a=document.createElement("a");a.href=URL.createObjectURL(new Blob([text],{type:"application/json"}));a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(a.href),1000);this.markProjectTempoSaved();this.status(`Projeto ${name} gerado. Coloque-o na mesma pasta dos stems.`)}
  chooseRpp(files,rpps){const box=this.$(".vs-rpp-picker");box.hidden=false;box.innerHTML=`<h3>Qual RPP representa o Original?</h3><p>Escolha o arquivo que representa o tom original dos áudios.</p><div class="vs-rpp-row"><select class="vs-select">${rpps.map((r,i)=>`<option value="${i}">${esc(baseName(r.name))} — ${esc(flatNote(rppKey(r.name)||"Não informado"))}</option>`).join("")}</select><button class="vs-btn">Abrir projeto</button></div>`;box.querySelector("button").onclick=()=>{const rpp=rpps[+box.querySelector("select").value];box.hidden=true;this.load(files,rpp,true)};this.status("Escolha o tom/projeto que deseja estudar.")}
  async load(files,chosenRpp=null){
    this.stop();this.tracks=[];this.duration=0;this.groupStates=new Map();this.vsVolume=1;this.vsMute=false;this.vsSolo=false;this.host.classList.remove("vs-mode-rehearsal");this.$("[data-mode]").textContent="Modo Ensaio";this.$("[data-mode]").disabled=true;if(!this.currentProjectName)this.$(".vs-project").classList.remove("active");this.status("Analisando projeto...",false,8);try{
      const projects=files.filter(f=>/\.zlplay$/i.test(f.name));if(projects.length===1){await this.loadTomCerto(files,projects[0]);return}if(projects.length>1){this.showLibrary(files,projects,"Projetos Tom Certo encontrados");return}const rpps=files.filter(f=>/\.rpp$/i.test(f.name)),audios=files.filter(f=>AUDIO_EXT.test(f.name));if(!audios.length)throw Error("Nenhuma track de áudio foi encontrada.");this.currentProjectFiles=files;this.currentProjectDir=this.dirName(audios[0]?.name||"");this.projectManifest=null;if(rpps.length>1&&!chosenRpp){this.chooseRpp(files,rpps);return}this.$(".vs-rpp-picker").hidden=true;
      const rpp=chosenRpp||rpps[0];this.tempoSource=rpp?"rpp":"editable";const projectSource=rpp?rpp.name:(this.currentProjectDir?baseName(this.currentProjectDir):(files[0].name||"Projeto Tom Certo")),projectRaw=baseName(projectSource).replace(/\.(rpp|zip|wav|mp3|flac|ogg|opus|m4a|aac)$/i,"");const km=projectRaw.match(/\(([A-G](?:#|b)?m?)\)\s*$/i),key=km?km[1]:"Não informado",project=projectRaw.replace(/\s*\([A-G](?:#|b)?m?\)\s*$/i,""),isOriginal=/(^|[\s_-])original([\s_-]|$)/i.test(project);
      let spec=[];if(rpp){const rppText=await rpp.text();spec=parseRpp(rppText,audios);this.tempo=parseRppTempo(rppText);this.rawSections=parseRppSections(rppText)}else{this.tempo={bpm:120,numerator:4,denominator:4,points:[{time:0,bpm:120}]};this.rawSections={markers:[],regions:[]}}if(!spec.length)spec=audios.map(a=>({name:baseName(a.name).replace(/\.[^.]+$/,"") ,volume:1,items:[{position:0,offset:0,length:null,file:a.name}]}));
      const byName=new Map(audios.map(a=>[norm(a.name),a])),missing=[],decodeFailed=[],decodedCache=new Map();let n=0;
      for(const s of spec){const items=[];for(const it of s.items){const f=byName.get(norm(it.file));if(!f){missing.push(baseName(it.file));continue}let buffer=decodedCache.get(f.name);if(!buffer){n++;this.status(`Preparando track ${n} de ${audios.length}: ${baseName(f.name)}...`,false,12+Math.round(n/audios.length*76));try{buffer=await this.decodeProjectAudio(f);decodedCache.set(f.name,buffer)}catch(decodeError){console.error("[ZLPlay decode]",f.name,decodeError);if(rpp)throw decodeError;decodeFailed.push(baseName(f.name));continue}}items.push({...it,buffer,file:f.name,projectFile:this.relativeToProject(f.name)});this.duration=Math.max(this.duration,it.position+(it.length||Math.max(0,buffer.duration-it.offset)))}if(items.length){const fallback=items[0].name||baseName(items[0].file).replace(/\.[^.]+$/,"");this.tracks.push({...s,name:(s.name||fallback||`Track ${this.tracks.length+1}`).trim(),items,originalVolume:s.volume,originalMute:!!s.mute,originalSolo:!!s.solo,mute:!!s.mute,solo:!!s.solo})}}
      if(!this.tracks.length)throw Error(decodeFailed.length?`Nenhuma track pôde ser decodificada. Primeiro erro: ${decodeFailed[0]}.`:"As tracks citadas pelo projeto não foram encontradas.");this.status("Montando o mixer...",false,94);this.projectId=`${project}|${this.tracks.map(t=>t.name).join("|")}`;this.restore();this.finishLoadedProject(project,key,isOriginal,false);this.$(".vs-meta").insertAdjacentHTML("beforeend",rpp?'<span class="vs-pill">Organizado pelo .rpp</span>':'<span class="vs-pill">Sem .rpp</span>');const warnings=[];if(missing.length)warnings.push(`Arquivos ausentes: ${[...new Set(missing)].join(", ")}`);if(decodeFailed.length)warnings.push(`Não decodificados: ${[...new Set(decodeFailed)].join(", ")}`);this.$(".vs-warning").textContent=warnings.join(" · ");this.status(rpp?"Projeto pronto. BPM e tom podem ser ajustados manualmente.":decodeFailed.length?`Projeto aberto com ${this.tracks.length} tracks. ${decodeFailed.length} arquivo(s) não puderam ser decodificados; veja o aviso acima.`:"Tracks prontas. Defina BPM/tom e use Regiões para preparar o projeto.")
    }catch(e){console.error(e);this.status(e.message||"Não foi possível abrir o VS.",true)}
  }
  audioContext(){if(!this.ctx){this.ctx=new AudioContext();this.master=this.ctx.createGain();this.master.connect(this.ctx.destination);this.metroPanner=this.ctx.createStereoPanner();this.metroPanner.pan.value=this.metroPan||0;this.metroPanner.connect(this.master)}return this.ctx}
  async decodeProjectAudio(file){const label=baseName(file?.name||"arquivo de áudio");let firstError=null;for(let attempt=0;attempt<2;attempt++){try{const data=await file.arrayBuffer();if(!data||data.byteLength<16)throw Error("arquivo vazio ou incompleto");return await this.audioContext().decodeAudioData(data)}catch(e){firstError=firstError||e;if(attempt===0)await new Promise(resolve=>setTimeout(resolve,60))}}const err=new Error(`Não foi possível decodificar "${label}".`);err.cause=firstError;throw err}

  saveControlMappings(){try{localStorage.setItem("zosma-zlplay:control-mappings",JSON.stringify(this.controlMappings||{}))}catch{}}

  validControlMappingBinding(id,b){const meta=this.controlTargetMeta(id);if(!meta||!b||typeof b!=="object")return false;if(b.type==="key")return meta.kind!=="fader"&&typeof b.code==="string"&&!!b.code;if(b.type==="midi-note")return meta.kind!=="fader"&&Number.isInteger(Number(b.channel))&&Number(b.channel)>=0&&Number(b.channel)<=15&&Number.isInteger(Number(b.note))&&Number(b.note)>=0&&Number(b.note)<=127;if(b.type==="midi-cc")return Number.isInteger(Number(b.channel))&&Number(b.channel)>=0&&Number(b.channel)<=15&&Number.isInteger(Number(b.cc))&&Number(b.cc)>=0&&Number(b.cc)<=127;return false}

  sanitizeControlMappings(raw){const clean={};if(!raw||typeof raw!=="object"||Array.isArray(raw))return clean;for(const [id,b] of Object.entries(raw)){if(!this.validControlMappingBinding(id,b))continue;if(b.type==="key")clean[id]={type:"key",code:String(b.code),key:typeof b.key==="string"?b.key:""};else if(b.type==="midi-note")clean[id]={type:"midi-note",channel:Number(b.channel),note:Number(b.note)};else if(b.type==="midi-cc")clean[id]={type:"midi-cc",channel:Number(b.channel),cc:Number(b.cc)}}return clean}

  exportControlMappings(){const mappings=this.sanitizeControlMappings(this.controlMappings||{}),count=Object.keys(mappings).length;if(!count){this.status("Nenhum mapeamento salvo para exportar.");return}const payload={format:"zlplay-control-mappings",version:1,exportedAt:new Date().toISOString(),mappings},blob=new Blob([JSON.stringify(payload,null,2)],{type:"application/json"}),url=URL.createObjectURL(blob),a=document.createElement("a");a.href=url;a.download="ZLPlay-Mapeamento.zlmap";document.body.append(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(url),1000);this.status(`${count} mapeamento(s) exportado(s) para ZLPlay-Mapeamento.zlmap.`)}

  async importControlMappings(file){try{const data=JSON.parse(await file.text()),raw=data?.format==="zlplay-control-mappings"?data.mappings:(data?.mappings||data),clean=this.sanitizeControlMappings(raw),count=Object.keys(clean).length;if(!count)throw new Error("Arquivo sem mapeamentos compatíveis.");this.controlMappings=clean;this.saveControlMappings();this.controlMapTarget=null;this.refreshControlMappingVisuals();this.status(`${count} mapeamento(s) importado(s). Eles valem para todos os projetos neste navegador.`)}catch(e){console.warn("[ZLPlay mapping import]",e);this.status(`Não foi possível importar o mapeamento: ${e.message||"arquivo inválido"}`,true)}}

  clearAllControlMappings(){const count=Object.keys(this.controlMappings||{}).length;if(!count){this.status("Não há mapeamentos salvos.");return}if(!confirm(`Remover os ${count} mapeamento(s) de controles deste navegador?`))return;this.controlMappings={};this.saveControlMappings();this.controlMapTarget=null;this.controlMapCcState.clear();this.refreshControlMappingVisuals();this.status("Todos os mapeamentos de controles foram removidos.")}

  controlTargetMeta(id){const fixed={
    "transport-play":{label:"Play",kind:"button",selector:"[data-play]"},
    "transport-pause":{label:"Pause",kind:"button",selector:"[data-pause]"},
    "transport-stop":{label:"Stop",kind:"button",selector:"[data-stop]"},
    "transport-solo-click":{label:"Solo Click",kind:"button",selector:"[data-solo-click]"},
    "transport-loop":{label:"Loop",kind:"button",selector:"[data-loop-current]"},
    "transport-start":{label:"Voltar ao início",kind:"button",selector:"[data-go-start]"},
    "transport-pad-play":{label:"Pad Play",kind:"button",selector:"[data-pad-play]"},
    "transport-pad-stop":{label:"Pad Stop",kind:"button",selector:"[data-pad-stop]"},
    "transport-snap":{label:"Snap",kind:"button",selector:"[data-snap-toggle]"},
    "library-prev":{label:"Música anterior",kind:"button",selector:"[data-library-prev]"},
    "library-open":{label:"Repertório",kind:"button",selector:"[data-library-order]"},
    "library-next":{label:"Próxima música",kind:"button",selector:"[data-library-next]"},
    "click-tap":{label:"Tap Tempo",kind:"button",selector:"[data-tap-tempo]"},
    "zoom-out":{label:"Diminuir Zoom",kind:"button",selector:"[data-zoom-out]"},
    "zoom-in":{label:"Aumentar Zoom",kind:"button",selector:"[data-zoom-in]"},
    "volume-master":{label:"Volume Master",kind:"fader",selector:'[data-map-control="volume-master"]'},
    "volume-click":{label:"Volume Click",kind:"fader",selector:'[data-map-control="volume-click"]'},
    "volume-pad":{label:"Volume Pad",kind:"fader",selector:'[data-map-control="volume-pad"]'},
    "volume-guide":{label:"Volume Guia",kind:"fader",selector:'[data-map-control="volume-guide"]'},
    "volume-bus-vs":{label:"Volume Bus VS",kind:"fader",selector:'[data-map-control="volume-bus-vs"]'}
  };if(fixed[id])return fixed[id];if(id?.startsWith("pad-note:")){const note=id.slice(9);return{label:`Pad ${note}`,kind:"button",note,selector:"[data-pad-note]"}}return null}

  controlTargetFromElement(el){if(!(el instanceof Element))return null;const checks=[
    ["[data-play]","transport-play"],["[data-pause]","transport-pause"],["[data-stop]","transport-stop"],["[data-solo-click]","transport-solo-click"],["[data-loop-current]","transport-loop"],["[data-go-start]","transport-start"],["[data-pad-play]","transport-pad-play"],["[data-pad-stop]","transport-pad-stop"],["[data-snap-toggle]","transport-snap"],["[data-library-prev]","library-prev"],["[data-library-order]","library-open"],["[data-library-next]","library-next"],["[data-tap-tempo]","click-tap"],["[data-zoom-out]","zoom-out"],["[data-zoom-in]","zoom-in"]
  ];for(const [sel,id] of checks){const node=el.closest(sel);if(node)return{id,node,...this.controlTargetMeta(id)}}const pad=el.closest("[data-pad-note]");if(pad){const id=`pad-note:${pad.dataset.padNote}`;return{id,node:pad,...this.controlTargetMeta(id)}}const fader=el.closest("[data-map-control]");if(fader){const id=fader.dataset.mapControl;const meta=this.controlTargetMeta(id);if(meta)return{id,node:fader,...meta}}return null}

  controlMappingElements(){const sels=["[data-play]","[data-pause]","[data-stop]","[data-solo-click]","[data-loop-current]","[data-go-start]","[data-pad-play]","[data-pad-stop]","[data-snap-toggle]","[data-library-prev]","[data-library-order]","[data-library-next]","[data-tap-tempo]","[data-zoom-out]","[data-zoom-in]","[data-pad-note]","[data-map-control]"];return[...this.host.querySelectorAll(sels.join(","))]}

  bindingLabel(b){if(!b)return"";if(b.type==="key")return b.code?.replace(/^Key/,"").replace(/^Digit/,"")||b.key||"Tecla";if(b.type==="midi-note")return`Note ${b.note}`;if(b.type==="midi-cc")return`CC ${b.cc}`;return""}

  refreshControlMappingVisuals(){for(const el of this.controlMappingElements()){const info=this.controlTargetFromElement(el);el.classList.remove("vs-map-eligible","vs-map-bound","vs-map-awaiting");el.removeAttribute("data-map-binding");if(this.controlMapMode&&(el.matches("[data-zoom-out]")||el.matches("[data-zoom-in]")))el.disabled=false;if(!this.controlMapMode||!info)continue;el.classList.add("vs-map-eligible");const binding=this.controlMappings?.[info.id];if(binding){el.classList.add("vs-map-bound");el.dataset.mapBinding=this.bindingLabel(binding)}if(this.controlMapTarget?.id===info.id)el.classList.add("vs-map-awaiting")}const b=this.$?.("[data-control-map]");if(b){b.classList.toggle("on",!!this.controlMapMode);const label=this.controlMapMode?"Mapeando…":"Mapear";if(b.textContent!==label)b.textContent=label}}

  async toggleControlMapping(force){this.controlMapMode=typeof force==="boolean"?force:!this.controlMapMode;this.controlMapTarget=null;this.host.classList.toggle("vs-control-mapping",this.controlMapMode);this.refreshControlMappingVisuals();if(this.controlMapMode){this.status("Mapeamento ativo: clique em um controle destacado e depois pressione uma tecla, MIDI Note ou mova um MIDI CC.");await this.initControlMidi()}else{this.setTimelineZoom(this.timelineZoom,false);this.status("Mapeamento de controles encerrado.")}}

  selectControlMappingTarget(info){if(!info)return;this.controlMapTarget={id:info.id,label:info.label,kind:info.kind};this.refreshControlMappingVisuals();const old=this.controlMappings?.[info.id],suffix=old?` Atual: ${this.bindingLabel(old)}.`:"";this.status(info.kind==="fader"?`${info.label}: mova um fader/knob MIDI CC. Delete remove o mapeamento.${suffix}`:`${info.label}: pressione uma tecla, MIDI Note ou MIDI CC. Delete remove o mapeamento.${suffix}`)}

  sameControlBinding(a,b){return a&&b&&a.type===b.type&&(a.type==="key"?a.code===b.code:a.type==="midi-note"?a.note===b.note&&a.channel===b.channel:a.cc===b.cc&&a.channel===b.channel)}

  assignControlMapping(id,binding){for(const [other,b] of Object.entries(this.controlMappings||{}))if(other!==id&&this.sameControlBinding(b,binding))delete this.controlMappings[other];this.controlMappings[id]=binding;this.saveControlMappings();const meta=this.controlTargetMeta(id);this.status(`${meta?.label||id} mapeado para ${this.bindingLabel(binding)}.`);this.controlMapTarget=null;this.refreshControlMappingVisuals()}

  clearSelectedControlMapping(){const id=this.controlMapTarget?.id;if(!id)return;if(this.controlMappings?.[id]){delete this.controlMappings[id];this.saveControlMappings();this.status(`${this.controlMapTarget.label}: mapeamento removido.`)}else this.status(`${this.controlMapTarget.label}: nenhum mapeamento salvo.`);this.controlMapTarget=null;this.refreshControlMappingVisuals()}

  installControlMappingHandlers(){if(this.controlMapHandlersInstalled)return;this.controlMapHandlersInstalled=true;this.host.addEventListener("pointerdown",e=>{if(!this.controlMapMode)return;const info=this.controlTargetFromElement(e.target);if(!info)return;e.preventDefault();e.stopPropagation();this.selectControlMappingTarget(info)},true);this.host.addEventListener("click",e=>{if(!this.controlMapMode)return;const info=this.controlTargetFromElement(e.target);if(!info)return;e.preventDefault();e.stopPropagation();this.selectControlMappingTarget(info)},true);this._controlMapKeyHandler=e=>{const tag=e.target?.tagName,typing=e.target?.isContentEditable||tag==="INPUT"||tag==="TEXTAREA"||tag==="SELECT";if(this.controlMapMode&&this.controlMapTarget){if(e.code==="Escape"){e.preventDefault();this.controlMapTarget=null;this.refreshControlMappingVisuals();this.status("Seleção de mapeamento cancelada.");return}if(e.code==="Delete"||e.code==="Backspace"){e.preventDefault();this.clearSelectedControlMapping();return}if(this.controlMapTarget.kind==="fader"){if(!typing){e.preventDefault();this.status(`${this.controlMapTarget.label}: use MIDI CC para controlar o valor do fader.`)}return}if(typing)return;e.preventDefault();e.stopImmediatePropagation();this.assignControlMapping(this.controlMapTarget.id,{type:"key",code:e.code,key:e.key});return}if(this.controlMapMode||typing||e.repeat)return;for(const [id,b] of Object.entries(this.controlMappings||{})){if(b.type==="key"&&b.code===e.code){e.preventDefault();e.stopImmediatePropagation();this.executeMappedControl(id);return}}};document.addEventListener("keydown",this._controlMapKeyHandler,true);this.controlMapObserver=new MutationObserver(records=>{if(!this.controlMapMode)return;const changedElements=records.some(r=>[...r.addedNodes,...r.removedNodes].some(n=>n.nodeType===1));if(changedElements)this.refreshControlMappingVisuals()});this.controlMapObserver.observe(this.host,{childList:true,subtree:true})}

  async initControlMidi(){if(this.controlMapMidiAccess){this.bindControlMidiInputs();return}if(!navigator.requestMIDIAccess){this.status("Mapeamento ativo. Web MIDI não está disponível neste navegador; teclado continua disponível.");return}try{this.controlMapMidiAccess=await navigator.requestMIDIAccess({sysex:false});this.bindControlMidiInputs();this.controlMapMidiAccess.onstatechange=()=>this.bindControlMidiInputs();this.status(`Mapeamento ativo. MIDI detectado: ${this.controlMapMidiAccess.inputs.size} entrada(s). Clique em um controle para mapear.`)}catch(e){console.warn("[ZLPlay MIDI]",e);this.status("Mapeamento ativo. Acesso MIDI não autorizado; teclado continua disponível.")}}

  bindControlMidiInputs(){if(!this.controlMapMidiAccess)return;for(const input of this.controlMapMidiAccess.inputs.values())input.onmidimessage=e=>this.handleControlMidiMessage(e)}

  handleControlMidiMessage(e){const [s,d1,d2]=e.data||[],type=s&0xf0,channel=s&0x0f;if(type===0x90&&d2>0){if(this.controlMapMode&&this.controlMapTarget){if(this.controlMapTarget.kind==="fader"){this.status(`${this.controlMapTarget.label}: para fader use MIDI CC.`);return}this.assignControlMapping(this.controlMapTarget.id,{type:"midi-note",channel,note:d1});return}for(const [id,b] of Object.entries(this.controlMappings||{}))if(b.type==="midi-note"&&b.channel===channel&&b.note===d1)this.executeMappedControl(id);return}if(type!==0xb0)return;if(this.controlMapMode&&this.controlMapTarget){this.assignControlMapping(this.controlMapTarget.id,{type:"midi-cc",channel,cc:d1});return}for(const [id,b] of Object.entries(this.controlMappings||{})){if(b.type!=="midi-cc"||b.channel!==channel||b.cc!==d1)continue;const meta=this.controlTargetMeta(id);if(meta?.kind==="fader")this.executeMappedControl(id,d2/127);else{const key=`${channel}:${d1}:${id}`,last=this.controlMapCcState.get(key)||0;this.controlMapCcState.set(key,d2);if(d2>=64&&last<64)this.executeMappedControl(id)}}}

  executeMappedControl(id,value=null){const meta=this.controlTargetMeta(id);if(!meta)return;if(meta.kind==="fader"){const nodes=[...this.host.querySelectorAll(meta.selector)];for(const el of nodes){if(!(el instanceof HTMLInputElement))continue;const min=Number(el.min||0),max=Number(el.max||1),v=min+(max-min)*Math.max(0,Math.min(1,Number(value)||0));el.value=String(v);el.dispatchEvent(new Event("input",{bubbles:true}));el.dispatchEvent(new Event("change",{bubbles:true}))}return}let node;if(meta.note)node=[...this.host.querySelectorAll("[data-pad-note]")].find(el=>el.dataset.padNote===meta.note);else node=this.host.querySelector(meta.selector);node?.click()}

  bindPadNoteButtons(){this.host.querySelectorAll("[data-pad-note]").forEach(btn=>{btn.onclick=()=>this.playPadNote(btn.dataset.padNote,true)});this.updatePadUi()}

  padAutoNote(){const base=keyIndex(this.projectKey);if(base===undefined)return"C";const idx=((base+(Number(this.liveTranspose)||0))%12+12)%12;return PAD_NOTES[idx]}

  savePadMix(){try{localStorage.setItem("zosma-zlplay:pad-mix",JSON.stringify({volume:Number(this.padVolume??1),pan:Number(this.padPan||0)}))}catch{}}
  updatePadMix(){if(this.padBus)this.padBus.gain.value=Number(this.padVolume??1);if(this.padPanner)this.padPanner.pan.value=Math.max(-1,Math.min(1,Number(this.padPan)||0))}
  ensurePadBus(){const ctx=this.audioContext();if(!this.padBus){this.padBus=ctx.createGain();this.padPanner=ctx.createStereoPanner();this.padBus.connect(this.padPanner);this.padPanner.connect(this.master)}this.updatePadMix();return this.padBus}

  async loadPadBuffer(note){if(!PAD_FILES[note])throw Error(`Pad inválido: ${note}`);if(this.padBuffers.has(note))return this.padBuffers.get(note);const response=await fetch(`./pads/${PAD_FILES[note]}`);if(!response.ok)throw Error(`Arquivo do Pad ${note} não encontrado.`);const buffer=await this.audioContext().decodeAudioData(await response.arrayBuffer());this.padBuffers.set(note,buffer);return buffer}

  preloadPad(note){if(!PAD_FILES[note]||this.padBuffers.has(note))return;this.loadPadBuffer(note).catch(err=>console.warn("[ZLPlay Pad]",err))}

  updatePadUi(){this.host.querySelectorAll("[data-pad-note]").forEach(btn=>{const selected=btn.dataset.padNote===this.padNote;btn.classList.toggle("selected",selected);btn.classList.toggle("on",selected&&this.padPlaying)});const play=this.host.querySelector("[data-pad-play]"),stop=this.host.querySelector("[data-pad-stop]");play?.classList.toggle("on",this.padPlaying);stop?.classList.toggle("on",!this.padPlaying)}

  fadeAndStopPadVoices(seconds=.55){const ctx=this.ctx;if(!ctx)return;const now=ctx.currentTime,voices=[...(this.padVoices||[])];for(const voice of voices){try{const param=voice.gain.gain;if(typeof param.cancelAndHoldAtTime==="function")param.cancelAndHoldAtTime(now);else{param.cancelScheduledValues(now);param.setValueAtTime(Math.max(0,Math.min(1,param.value)),now)}param.linearRampToValueAtTime(0,now+seconds)}catch{}try{voice.src.stop(now+seconds+.05)}catch{}}this.padVoices=[];this.padLastVoice=null}

  startPadLoop(buffer,requestedStartAt=null){const ctx=this.audioContext(),bus=this.ensurePadBus(),token=++this.padLoopToken;clearInterval(this.padScheduleTimer);this.padScheduleTimer=0;this.fadeAndStopPadVoices(1.2);const firstStart=Math.max(ctx.currentTime,Number(requestedStartAt)||ctx.currentTime),crossfade=.5,step=Math.max(.05,buffer.duration-crossfade),curveN=256,fadeIn=new Float32Array(curveN),fadeOut=new Float32Array(curveN);for(let i=0;i<curveN;i++){const x=i/(curveN-1);fadeIn[i]=Math.sin(x*Math.PI*.5);fadeOut[i]=Math.cos(x*Math.PI*.5)}this.padNextStartAt=firstStart;this.padLastVoice=null;this.padVoices=[];
    const schedule=()=>{if(token!==this.padLoopToken||!this.padPlaying)return;const horizon=ctx.currentTime+Math.max(18,buffer.duration*2.5);while(this.padNextStartAt<horizon){const at=this.padNextStartAt,src=ctx.createBufferSource(),gain=ctx.createGain(),voice={src,gain,startAt:at};src.buffer=buffer;src.connect(gain);gain.connect(bus);
        if(!this.padLastVoice){gain.gain.setValueAtTime(0,at);gain.gain.linearRampToValueAtTime(1,at+1.2)}
        else{gain.gain.setValueAtTime(0,at);gain.gain.setValueCurveAtTime(fadeIn,at,crossfade);const prev=this.padLastVoice;try{prev.gain.gain.cancelScheduledValues(at);prev.gain.gain.setValueAtTime(1,at);prev.gain.gain.setValueCurveAtTime(fadeOut,at,crossfade)}catch{}}
        src.start(at,0,buffer.duration);try{src.stop(at+buffer.duration+.02)}catch{}this.padVoices.push(voice);this.padLastVoice=voice;src.onended=()=>{const i=this.padVoices.indexOf(voice);if(i>=0)this.padVoices.splice(i,1);try{src.disconnect();gain.disconnect()}catch{}};this.padNextStartAt+=step}}
    schedule();this.padScheduleTimer=setInterval(schedule,500)}

  async playPadNote(note,manual=false,startAt=null){if(!PAD_FILES[note])return;if(manual)this.padManualOverride=true;const request=++this.padRequestId;try{const ctx=this.audioContext();await ctx.resume();const buffer=await this.loadPadBuffer(note);if(request!==this.padRequestId)return;if(this.padPlaying&&this.padNote===note){this.updatePadUi();return}this.padPlaying=true;this.padNote=note;this.startPadLoop(buffer,startAt);this.updatePadUi()}catch(err){console.error(err);this.status(`Não foi possível carregar o Pad ${note}.`,true)}}

  playPadManual(){const note=this.padNote||this.padAutoNote();this.playPadNote(note,!!this.padManualOverride)}

  stopPad(){this.padRequestId++;this.padPlaying=false;clearInterval(this.padScheduleTimer);this.padScheduleTimer=0;this.padLoopToken++;this.fadeAndStopPadVoices(.6);this.updatePadUi()}

  ensureProjectPad(startAt=null,forceProject=false){const note=this.padAutoNote();if(forceProject)this.padManualOverride=false;if(!this.padPlaying){this.padManualOverride=false;this.padNote=note;this.updatePadUi();this.playPadNote(note,false,startAt);return}if(this.padNote!==note)this.playPadNote(note,false,startAt)}

  buildSections(){const r=this.rawSections.regions.filter(x=>x.end>x.start);if(r.length)this.sections=r;else{const m=this.rawSections.markers;this.sections=m.map((x,i)=>({name:x.name,start:x.time,end:i+1<m.length?m[i+1].time:this.duration,type:"marker"})).filter(x=>x.end>x.start)}this.loopSection=this.sections[0]||null}
  drawKeyControl(isOriginal){const box=this.$(".vs-key"),current=keyIndex(this.projectKey),minor=/m$/i.test(this.projectKey),allKeys=KEY_NAMES.map(x=>x.split("/")[0]),normalized=current===undefined?"C":allKeys[current];box.innerHTML=`<label>Tom do projeto <select data-project-key>${allKeys.map(k=>`<option value="${k}" ${k===normalized?"selected":""}>${k}</option>`).join("")}</select></label><label>Tocar em <select data-live-key>${KEY_NAMES.map((name,i)=>`<option value="${i}" ${i===(current===undefined?0:current)?"selected":""}>${name}</option>`).join("")}</select></label>`;box.querySelector("[data-project-key]").onchange=e=>{this.projectKey=e.target.value;this.liveTranspose=0;this.padManualOverride=false;this.drawKeyControl(isOriginal);const note=this.padAutoNote();this.padNote=note;this.preloadPad(note);this.updatePadUi();if(this.padPlaying)this.playPadNote(note,false);this.status(`Tom do projeto definido como ${this.projectKey}.`)};box.querySelector("[data-live-key]").onchange=e=>this.changeLiveKey(+e.target.value)}
  async changeLiveKey(target){const current=keyIndex(this.projectKey);if(current===undefined)return;const delta=shortestSemitoneDelta(current,target),at=this.position(),was=this.playing;this.liveTranspose=delta;const padNote=this.padAutoNote();this.preloadPad(padNote);if(!this.padManualOverride){if(this.padPlaying)this.playPadNote(padNote,false);else{this.padNote=padNote;this.updatePadUi()}}if(!was)return;const mustRebuild=[...this.pitchBuses.values()].some(bus=>{const effective=bus.base+(bus.eligible?delta:0);return effective!==0&&!bus.pitchParam});if(mustRebuild){this.halt();this.offset=at;await this.play();return}for(const bus of this.pitchBuses.values()){if(bus.pitchParam)bus.pitchParam.setTargetAtTime(bus.base+(bus.eligible?delta:0),this.ctx.currentTime,.025)}}
  setMeterFromProject(){const meter=this.$("[data-meter]"),value=this.tempo.numerator===6&&this.tempo.denominator===8?"6":[2,3,4].includes(this.tempo.numerator)?String(this.tempo.numerator):"4";meter.value=value}
  drawSections(){
    const select=this.$("[data-section]"),toggle=this.$("[data-loop]"),timeline=this.$(".vs-timeline");
    let scroll=this.host.querySelector(".vs-timeline-scroll"),content=this.host.querySelector(".vs-timeline-content");
    if(!scroll){scroll=document.createElement("div");scroll.className="vs-timeline-scroll";content=document.createElement("div");content.className="vs-timeline-content";timeline.before(scroll);scroll.append(content);content.append(timeline)}
    let regions=content.querySelector(".vs-regions");if(!regions){regions=document.createElement("div");regions.className="vs-regions";content.prepend(regions)}
    const regionStatus=this.host.querySelector("[data-transport-status]");regions.innerHTML="";timeline.innerHTML="";scroll.hidden=!this.duration;timeline.hidden=!this.duration;regions.hidden=!this.duration;
    const regionHead=document.createElement("div");regionHead.className="vs-regions-head";regionHead.innerHTML='<span class="vs-regions-label">REGIÕES</span>';if(regionStatus)regionHead.append(regionStatus);regions.append(regionHead);
    const hasSections=this.sections.length>0;if(select)select.disabled=!hasSections;if(toggle)toggle.disabled=!hasSections;if(!hasSections){if(toggle)toggle.checked=false;this.loop=false;if(select)select.innerHTML="<option>Sem regiões ou marcadores</option>";const empty=document.createElement("div");empty.className="vs-empty-regions";empty.textContent="";regions.append(empty)}else if(select){select.innerHTML=this.sections.map((s,i)=>`<option value="${i}">${esc(s.name)} · ${fmt(s.start)}–${fmt(s.end)}</option>`).join("")}
    if(!this.duration)return;content.style.width=`${this.timelineZoom*100}%`;
    const canvas=document.createElement("canvas");canvas.className="vs-timeline-waveform";timeline.append(canvas);
    const ruler=document.createElement("div");ruler.className="vs-timeline-ruler";const {barSeconds}=this.timelineGridMetrics();for(let t=0,n=1;t<this.duration;t+=barSeconds,n++){const tick=document.createElement("span");tick.style.left=`${t/this.duration*100}%`;tick.textContent=n;ruler.append(tick)}timeline.append(ruler);
    this.sections.forEach((s,i)=>{const button=document.createElement("button");button.type="button";button.className=`vs-segment${i===0?" active":""}${this.mappingRegions&&i===this.selectedRegionIndex?" editing":""}`;button.dataset.sectionIndex=i;button.style.left=`${s.start/this.duration*100}%`;button.style.width=`${(s.end-s.start)/this.duration*100}%`;button.title=this.mappingRegions?`Editar ${s.name} · ${fmt(s.start)}–${fmt(s.end)}`:`${s.name} · ${fmt(s.start)}–${fmt(s.end)}`;if(s.color)button.style.setProperty("--region-color",s.color);button.innerHTML=`<span>${esc(s.name)}</span><small>${fmt(s.start)}</small>`;button.onclick=()=>this.mappingRegions?this.selectRegionForEdit(i):this.selectSection(i,true);regions.append(button)});
    const playhead=document.createElement("i");playhead.className="vs-timeline-playhead";timeline.append(playhead);timeline.onclick=e=>{if(e.target.closest("button"))return;const rect=timeline.getBoundingClientRect(),ratio=Math.max(0,Math.min(1,(e.clientX-rect.left)/rect.width));this.mappingRegions?this.setMappingCursor(ratio*this.duration):this.seek(this.snapTime(ratio*this.duration))};
    this.updateTimelineGrid();this.drawTimelineWaveform(canvas);this.setTimelineZoom(this.timelineZoom,false);if(hasSections)this.selectSection(0,false);else this.paint(this.position());this.updateRegionLoopFeedback()
  }
  drawTimelineWaveform(canvas){const w=Math.max(300,canvas.clientWidth||1200),h=canvas.clientHeight||70,dpr=window.devicePixelRatio||1;canvas.width=w*dpr;canvas.height=h*dpr;const c=canvas.getContext("2d");c.scale(dpr,dpr);c.clearRect(0,0,w,h);c.strokeStyle="#b45cffaa";c.lineWidth=1;const bins=Math.floor(w/2),sum=new Float32Array(bins);for(const t of this.tracks){for(const it of t.items||[]){const data=it.buffer.getChannelData(0),start=Math.max(0,Math.floor(it.position/this.duration*bins)),end=Math.min(bins,Math.ceil((it.position+(it.length||it.buffer.duration))/this.duration*bins));for(let x=start;x<end;x++){const idx=Math.floor((x-start)/Math.max(1,end-start)*data.length);sum[x]=Math.max(sum[x],Math.abs(data[Math.min(data.length-1,idx)]||0))}}}c.beginPath();for(let x=0;x<bins;x++){const y=h/2-sum[x]*h*.42;c.moveTo(x*2,h/2+sum[x]*h*.42);c.lineTo(x*2,y)}c.stroke()}
  selectSection(index,seek=false){const section=this.sections[index];if(!section)return;if(seek&&this.playing&&this.pendingSection&&this.pendingSection.index===index){this.cancelPendingGuide();this.pendingSection=null;const p=this.position();this.host.querySelectorAll(".vs-segment").forEach((el,i)=>{el.classList.remove("pending");const s=this.sections[i];el.classList.toggle("active",!!s&&p>=s.start&&p<s.end)});this.status(`Salto para ${section.name} cancelado.`);return}this.cancelPendingGuide();const oldSectionSelect=this.$("[data-section]");if(oldSectionSelect)oldSectionSelect.value=String(index);if(seek&&this.playing){const p=this.position(),current=this.sections.find(s=>p>=s.start&&p<s.end);if(current){this.pendingSection={index,section,boundary:current.end,guidePlayed:false};this.host.querySelectorAll(".vs-segment").forEach((el,i)=>{el.classList.toggle("active",i===index);el.classList.toggle("pending",i===index)});this.playPendingGuide(p);this.status(`Próximo trecho: ${section.name}. A troca acontecerá ao terminar ${current.name}.`);return}}this.pendingSection=null;this.loopSection=section;this.host.querySelectorAll(".vs-segment").forEach((el,i)=>{el.classList.toggle("active",i===index);el.classList.remove("pending")});if(seek)this.seek(section.start)}
  projectHasNativePitch(){return this.tracks.some(track=>track.items.some(item=>(Number(item.pitch)||0)!==0))}
  normalizeTrackLabel(name){return String(name||"").normalize("NFD").replace(/[\u0300-\u036f]/g,"").trim().replace(/\s+/g," ").toLowerCase()}
  defaultPitchEligible(t){if(this.isSpecialTrack(t))return false;const n=this.normalizeTrackLabel(t.name);if(n==="loop")return false;const words=n.replace(/[_-]+/g," ").split(/\s+/).filter(Boolean);const blocked=new Set(["drum","drums","bateria","percussao","percussion","perc","shaker","tambourine","pandeiro","cajon","clap","snare","kick","hihat","hi-hat","cymbal","cymbals","prato","pratos","toms","tom"]);return !words.some(w=>blocked.has(w))}
  pitchEligible(t){if(this.isSpecialTrack(t))return false;if(typeof t.pitchEnabled==="boolean")return t.pitchEnabled;if(this.projectHasNativePitch())return (Number(t.items?.[0]?.pitch)||0)!==0;return this.defaultPitchEligible(t)}
  async setTrackPitchEnabled(t,enabled){if(this.isSpecialTrack(t))return;const at=this.position(),was=this.playing;t.pitchEnabled=!!enabled;this.save();this.drawTracks();this.status(`${t.name}: alteração de tom ${t.pitchEnabled?"ativada":"desativada"}.`);if(was){this.halt();this.offset=at;await this.play()}}
  pitchGroup(t){const base=Number(t.items[0]?.pitch)||0,eligible=this.pitchEligible(t);return{key:`${base}:${eligible?1:0}`,base,eligible}}
  trackMenuHtml(t){const groups=this.allGroupNames(),current=(t.group||"VS");const options=[`<option value="" selected disabled>Selecionar grupo...</option>`,...(current!=="VS"?[`<option value="VS">Bus VS</option>`]:[]),...groups.filter(g=>g!==current).map(g=>`<option value="${esc(g)}">${esc(g)}</option>`)];const manualPitch=!this.isSpecialTrack(t),pitch=manualPitch?`<label class="vs-pitch-option"><input type="checkbox" data-pitch-enabled ${this.pitchEligible(t)?"checked":""}> Alterar tom</label>`:"";return `<details class="vs-channel-menu"><summary title="Opções da track">⋮</summary><div class="vs-channel-menu-pop">${pitch}<label>Mover para grupo<select data-move-group>${options.join("")}</select></label><button type="button" data-replace>Substituir áudio</button><button type="button" class="danger" data-delete>Excluir track</button></div></details>`}
  makePadStrip(){const strip=document.createElement("div");strip.className="vs-channel-strip is-pad";const vol=Number(this.padVolume??1),pan=Number(this.padPan||0);strip.innerHTML=`<div class="vs-channel-top"></div><div class="vs-channel-pan"><span>Pan</span><input data-pan type="range" min="-1" max="1" step="0.01" value="${pan}"><b>${this.panLabel(pan)}</b></div><div class="vs-channel-fader-wrap"><input class="vs-channel-fader" data-volume data-map-control="volume-pad" type="range" min="0" max="1.5" step="0.01" value="${vol}"></div><div class="vs-channel-value">${Math.round(vol*100)}%</div><div class="vs-channel-ms"></div><div class="vs-channel-name">Pad</div><div class="vs-channel-group">PAD</div>`;strip.querySelector("[data-volume]").oninput=e=>{this.padVolume=+e.target.value;strip.querySelector(".vs-channel-value").textContent=`${Math.round(this.padVolume*100)}%`;this.updatePadMix();this.savePadMix()};strip.querySelector("[data-pan]").oninput=e=>{this.padPan=+e.target.value;strip.querySelector(".vs-channel-pan b").textContent=this.panLabel(this.padPan);this.updatePadMix();this.savePadMix()};return strip}
  makeNativeClickStrip(){const strip=document.createElement("div");strip.className="vs-channel-strip is-click is-native-click";const vol=Number(this.$("[data-metro-volume]")?.value||1),pan=Number(this.metroPan||0);strip.innerHTML=`<div class="vs-channel-top"></div><div class="vs-channel-pan"><span>Pan</span><input data-pan type="range" min="-1" max="1" step="0.01" value="${pan}"><b>${this.panLabel(pan)}</b></div><div class="vs-channel-fader-wrap"><input class="vs-channel-fader" data-volume data-map-control="volume-click" type="range" min="0" max="1.5" step="0.01" value="${vol}"></div><div class="vs-channel-value">${Math.round(vol*100)}%</div><div class="vs-channel-ms"><button class="vs-toggle" data-m>M</button><button class="vs-toggle" data-s>S</button></div><div class="vs-channel-name">Click ZLPlay</div><div class="vs-channel-group">CLICK</div>`;strip.querySelector("[data-volume]").oninput=e=>{const v=+e.target.value,m=this.$("[data-metro-volume]");if(m)m.value=v;strip.querySelector(".vs-channel-value").textContent=`${Math.round(v*100)}%`};strip.querySelector("[data-pan]").oninput=e=>{this.metroPan=+e.target.value;strip.querySelector(".vs-channel-pan b").textContent=this.panLabel(this.metroPan);this.updatePans()};strip.querySelector("[data-m]").onclick=e=>{const m=this.$("[data-metro]");if(m){m.checked=!m.checked;e.target.classList.toggle("on",!m.checked);if(this.playing)this.restartMetro()}};strip.querySelector("[data-s]").onclick=()=>this.toggleSoloClick();return strip}
  makeTrackStrip(t){if(t.pan===undefined)t.pan=0;const isClick=this.isClickTrack(t),isGuide=this.isGuideTrack(t);const strip=document.createElement("div");strip.className=`vs-channel-strip${isClick?" is-click":""}${isGuide?" is-guide":""}`;strip.innerHTML=`<div class="vs-channel-top">${this.trackMenuHtml(t)}</div><div class="vs-channel-pan"><span>Pan</span><input data-pan type="range" min="-1" max="1" step="0.01" value="${t.pan}"><b>${this.panLabel(t.pan)}</b></div><div class="vs-channel-fader-wrap"><input class="vs-channel-fader" data-volume type="range" min="0" max="${Math.max(1.5,t.volume)}" step="0.01" value="${t.volume}"></div><div class="vs-channel-value">${Math.round(t.volume*100)}%</div><div class="vs-channel-ms"><button class="vs-toggle ${t.mute?"on":""}" data-m>M</button><button class="vs-toggle ${t.solo?"on":""}" data-s>S</button></div><div class="vs-channel-name" title="${esc(t.name)}">${esc(t.name)}</div><div class="vs-channel-group">${isClick?"CLICK":isGuide?"GUIA":esc(t.group||"VS")}</div>`;const mapVolume=strip.querySelector("[data-volume]");if(mapVolume&&(isClick||isGuide))mapVolume.dataset.mapControl=isClick?"volume-click":"volume-guide";strip.querySelector("[data-m]").onclick=e=>{t.mute=!t.mute;e.target.classList.toggle("on",t.mute);this.updateGains();this.save()};strip.querySelector("[data-s]").onclick=e=>{t.solo=!t.solo;e.target.classList.toggle("on",t.solo);this.updateGains();this.save()};strip.querySelector("[data-volume]").oninput=e=>{t.volume=+e.target.value;strip.querySelector(".vs-channel-value").textContent=`${Math.round(t.volume*100)}%`;this.updateGains();this.save()};strip.querySelector("[data-pan]").oninput=e=>{t.pan=+e.target.value;strip.querySelector(".vs-channel-pan b").textContent=this.panLabel(t.pan);this.updatePans();this.save()};const pitchToggle=strip.querySelector("[data-pitch-enabled]");if(pitchToggle)pitchToggle.onchange=e=>this.setTrackPitchEnabled(t,e.target.checked);const mover=strip.querySelector("[data-move-group]");if(mover){mover.disabled=isClick||isGuide;mover.onchange=e=>{if(e.target.value)this.moveTrackToGroup(t,e.target.value)}}strip.querySelector("[data-replace]").onclick=()=>this.requestReplaceTrack(t);strip.querySelector("[data-delete]").onclick=()=>this.deleteTrack(t);return strip}
  makeBusStrip(name,state,type="group"){const strip=document.createElement("div");strip.className=`vs-channel-strip vs-bus-strip ${type==="vs"?"vs-vs-bus":""}`;const deletable=type==="group"&&name!=="VS";strip.innerHTML=`<div class="vs-channel-top">${deletable?`<button type="button" class="vs-bus-rename" title="Renomear grupo">✎</button><button type="button" class="vs-bus-delete" title="Excluir grupo">×</button>`:""}</div><div class="vs-channel-pan"><span>Pan</span><input data-pan type="range" min="-1" max="1" step="0.01" value="${state.pan||0}"><b>${this.panLabel(state.pan||0)}</b></div><div class="vs-channel-fader-wrap"><input class="vs-channel-fader" data-volume type="range" min="0" max="${Math.max(1.5,Number(state.volume??1))}" step="0.01" value="${Number(state.volume??1)}"></div><div class="vs-channel-value">${Math.round(Number(state.volume??1)*100)}%</div><div class="vs-channel-ms"><button class="vs-toggle ${state.mute?"on":""}" data-m>M</button><button class="vs-toggle ${state.solo?"on":""}" data-s>S</button></div><div class="vs-channel-name">${esc(name)}</div><div class="vs-channel-group">${type==="vs"?"BUS VS":"GRUPO"}</div>`;const mapVolume=strip.querySelector("[data-volume]");if(mapVolume&&type==="vs")mapVolume.dataset.mapControl="volume-bus-vs";const apply=()=>{this.updateGains();this.updatePans();this.save()};strip.querySelector("[data-volume]").oninput=e=>{state.volume=+e.target.value;strip.querySelector(".vs-channel-value").textContent=`${Math.round(state.volume*100)}%`;apply()};strip.querySelector("[data-pan]").oninput=e=>{state.pan=+e.target.value;strip.querySelector(".vs-channel-pan b").textContent=this.panLabel(state.pan);apply()};strip.querySelector("[data-m]").onclick=e=>{state.mute=!state.mute;e.target.classList.toggle("on",state.mute);apply()};strip.querySelector("[data-s]").onclick=e=>{state.solo=!state.solo;e.target.classList.toggle("on",state.solo);apply()};const ren=strip.querySelector(".vs-bus-rename");if(ren)ren.onclick=()=>this.renameGroup(name);const del=strip.querySelector(".vs-bus-delete");if(del)del.onclick=()=>this.deleteGroup(name);return strip}
  drawTracks(){for(const t of this.tracks){if(!t.group)t.group="VS"}const box=this.$(".vs-tracks");box.innerHTML="";if(!(this.groupStates instanceof Map))this.groupStates=new Map();const projectClick=this.tracks.find(t=>this.isClickTrack(t)),guides=this.tracks.filter(t=>this.isGuideTrack(t)),normal=this.tracks.filter(t=>!this.isSpecialTrack(t));for(const t of normal)if((t.group||"VS")!=="VS")this.ensureGroupState(t.group);const names=this.sortGroupNames(this.allGroupNames(),normal),scroll=document.createElement("div");scroll.className="vs-mixer-scroll";const consoleEl=document.createElement("div");consoleEl.className="vs-mixer-console";scroll.append(consoleEl);
    const specialBank=document.createElement("section");specialBank.className="vs-mixer-bank vs-special-bank";specialBank.innerHTML='<div class="vs-bank-title">CLICK / GUIA / PAD</div><div class="vs-bank-channels"></div>';const specialChannels=specialBank.querySelector(".vs-bank-channels");if(this.clickSource==="native"||!projectClick)specialChannels.append(this.makeNativeClickStrip());else specialChannels.append(this.makeTrackStrip(projectClick));for(const t of guides)specialChannels.append(this.makeTrackStrip(t));specialChannels.append(this.makePadStrip());consoleEl.append(specialBank);
    const thisRef=this,vsState={get volume(){return Number(thisRef.vsVolume??1)},set volume(v){thisRef.vsVolume=v},get pan(){return Number(thisRef.vsPan||0)},set pan(v){thisRef.vsPan=v},get mute(){return !!thisRef.vsMute},set mute(v){thisRef.vsMute=v},get solo(){return !!thisRef.vsSolo},set solo(v){thisRef.vsSolo=v}};
    const scope=document.createElement("section");scope.className="vs-mix-scope";scope.innerHTML='<div class="vs-scope-title">BUS VS · MIX</div><div class="vs-scope-content"></div>';const content=scope.querySelector(".vs-scope-content");content.append(this.makeBusStrip("VS",vsState,"vs"));
    for(const name of names){const members=normal.filter(t=>(t.group||"VS")===name).sort((a,b)=>this.compareMusical(a,b)),state=this.ensureGroupState(name),group=document.createElement("section");group.className="vs-mixer-bank vs-subgroup-bank";group.innerHTML=`<div class="vs-bank-title">${esc(name)} <small>${members.length}</small></div><div class="vs-bank-channels"></div>`;const channels=group.querySelector(".vs-bank-channels");channels.append(this.makeBusStrip(name,state,"group"));for(const t of members)channels.append(this.makeTrackStrip(t));content.append(group)}consoleEl.append(scope);
    const add=document.createElement("button");add.type="button";add.className="vs-add-channel";add.innerHTML='<strong>＋</strong><span>Track</span>';add.onclick=()=>this.$("[data-add-track]").click();consoleEl.append(add);for(const t of normal.filter(t=>(t.group||"VS")==="VS").sort((a,b)=>this.compareMusical(a,b)))content.append(this.makeTrackStrip(t));box.append(scroll);this.applyClickSource();if(this.controlMapMode)queueMicrotask(()=>this.refreshControlMappingVisuals())}

  async setupPitchGraph(){this.clearPitchGraph();const ctx=this.audioContext(),groups=new Map(this.tracks.map(t=>{const g=this.pitchGroup(t);return[g.key,g]})),needsPitch=[...groups.values()].some(g=>g.base+(g.eligible?this.liveTranspose:0)!==0),compensation=needsPitch?.135:0;if(needsPitch&&!this.workletReady){await ctx.audioWorklet.addModule(new URL("./assets/soundtouch-processor-C1MAJdwL.js",import.meta.url));this.workletReady=true}for(const group of groups.values()){const input=ctx.createGain(),effective=group.base+(group.eligible?this.liveTranspose:0);let output,pitchParam=null;if(effective!==0){output=new AudioWorkletNode(ctx,"soundtouch-processor",{processorOptions:{interpolationStrategy:"lanczos",sampleBufferType:"circular"}});pitchParam=output.parameters.get("pitchSemitones");pitchParam.value=effective;output.parameters.get("playbackRate").value=1;input.connect(output)}else{output=ctx.createDelay(1);output.delayTime.value=compensation;input.connect(output)}output.connect(this.master);this.pitchBuses.set(group.key,{inputNode:input,output,pitchParam,base:group.base,eligible:group.eligible});this.pitchGraph.push(input,output)}this.clickProcessor=ctx.createDelay(1);this.clickProcessor.delayTime.value=compensation;this.clickProcessor.connect(this.metroPanner||ctx.destination);this.pitchGraph.push(this.clickProcessor)}
  clearPitchGraph(){for(const node of this.pitchGraph)try{node.disconnect()}catch{}this.pitchGraph=[];this.pitchBuses.clear();this.clickProcessor=null}
  applyClickSource(){const hasProjectClick=this.tracks.some(t=>this.isClickTrack(t)),native=this.clickSource==="native"||!hasProjectClick;this.clickSource=native?"native":"project";const select=this.$("[data-click-source]"),metro=this.$("[data-metro]");if(select)select.value=this.clickSource;if(metro)metro.checked=native;for(const t of this.tracks)if(this.isClickTrack(t))t.__clickProjectMuted=native;if(this.playing)this.restartMetro();this.updateGains()}
  updateGains(fadeSeconds=0){const trackSolo=this.tracks.some(t=>t.solo),groupSolo=[...(this.groupStates||new Map()).values()].some(s=>s.solo),muteGuide=this.loop||this.currentLoop,ctx=this.ctx,now=ctx?.currentTime||0,fade=Math.max(0,Number(fadeSeconds)||0);this.tracks.forEach(t=>{if(!t.gain)return;const isClick=this.isClickTrack(t),isGuide=this.isGuideTrack(t),special=this.isSpecialTrack(t),state=special?null:this.ensureGroupState(t.group||"VS");const soloKeepsProjectClick=this.soloClick&&isClick&&this.clickSource==="project";let muted=t.__clickProjectMuted||(this.soloClick&&!soloKeepsProjectClick)||t.mute||(muteGuide&&isGuide)||(trackSolo&&!t.solo);if(!trackSolo&&!special&&groupSolo&&!state.solo)muted=true;if(!trackSolo&&special&&this.vsSolo)muted=true;if(!special&&(this.vsMute||state.mute))muted=true;const gain=special?Number(t.volume||0):Number(t.volume||0)*Number(this.vsVolume??1)*Number(state.volume??1),target=muted?0:gain,param=t.gain.gain;if(fade>0&&ctx){try{if(typeof param.cancelAndHoldAtTime==="function")param.cancelAndHoldAtTime(now);else{const current=param.value;param.cancelScheduledValues(now);param.setValueAtTime(current,now)}param.linearRampToValueAtTime(target,now+fade)}catch{param.value=target}}else param.value=target})}
  updatePans(){for(const t of this.tracks){if(!t.panner)continue;const special=this.isSpecialTrack(t),group=special?0:this.ensureGroupState(t.group||"VS").pan||0,value=(t.pan||0)+(special?0:(this.vsPan||0)+group);t.panner.pan.value=Math.max(-1,Math.min(1,value))}if(this.metroPanner)this.metroPanner.pan.value=this.metroPan||0}
  async play(internalResume=false){if(this.playing||!this.tracks.length)return;const ctx=this.audioContext();let freeClickWasActive=false,freeBpm=120,projectBpm=this.projectBpm();if(!internalResume){freeClickWasActive=this.standaloneClickActive||this.soloClick;freeBpm=Number(this.standaloneBpm)||120;projectBpm=this.restoreProjectTempo(freeClickWasActive&&this.tempoSource==="zlplay")}await ctx.resume();await this.setupPitchGraph();let launchAt=ctx.currentTime,smoothClickHandoff=false;if(!internalResume){smoothClickHandoff=freeClickWasActive&&this.offset<=.05&&Math.abs(freeBpm-projectBpm)<.02;if(this.currentLoop)this.toggleCurrentLoop(false);this.soloClick=false;this.host.querySelector("[data-solo-click]")?.classList.remove("on");this.updateRegionLoopFeedback();if(smoothClickHandoff){launchAt=this.nextStandaloneBarTime(.18);this.standaloneStopAt=launchAt;this.status("VS preparado: entrada no próximo tempo 1 sem interromper o click.")}else if(freeClickWasActive)this.stopStandaloneClick(false)}if(this.offset>=this.duration)this.offset=0;this.nodes=[];this.projectLaunchAt=launchAt>ctx.currentTime+.01?launchAt:0;this.started=launchAt-this.offset;for(const t of this.tracks){t.gain=ctx.createGain();t.panner=ctx.createStereoPanner();const group=this.pitchGroup(t),bus=this.pitchBuses.get(group.key);t.gain.connect(t.panner);t.panner.connect(bus.inputNode);for(const it of t.items){const local=this.offset-it.position,end=it.length||Math.max(0,it.buffer.duration-it.offset);if(local>=end)continue;const src=ctx.createBufferSource();src.buffer=it.buffer;src.connect(t.gain);const delay=Math.max(0,it.position-this.offset),sourceOffset=it.offset+Math.max(0,local),duration=Math.max(0,end-Math.max(0,local));if(duration>0){src.start(launchAt+delay,sourceOffset,duration);this.nodes.push(src)}}}this.updateGains();this.updatePans();this.playing=true;this.applyClickSource();this.ensureProjectPad(launchAt,!internalResume);this.tick()}
  position(){if(!this.playing)return this.offset;if(this.projectLaunchAt&&this.ctx.currentTime<this.projectLaunchAt)return this.offset;return Math.max(0,Math.min(this.duration,this.ctx.currentTime-this.started))}
  pause(){if(!this.playing)return;this.offset=this.position();this.halt();const current=this.sectionAt(this.offset);this.status(current?`Pausado em: ${current.name}.`:"Reprodução pausada.")}
  stop(){this.halt();this.pendingSection=null;this.host.querySelectorAll(".vs-segment").forEach(el=>el.classList.remove("pending"));this.offset=0;this.paint(0);if(this.tracks.length)this.status("Projeto pronto para reproduzir.")}
  halt(){this.cancelPendingGuide();for(const n of this.nodes)try{n.stop()}catch{}this.nodes=[];this.pendingGuideNode=null;this.playing=false;this.lastStatusSectionIndex=-1;clearInterval(this.metroTimer);this.metroTimer=0;cancelAnimationFrame(this.raf);if(this.projectLaunchAt&&this.standaloneClickActive)this.stopStandaloneClick(false);this.projectLaunchAt=0;this.clearPitchGraph()}
  seek(v){const was=this.playing;this.halt();this.offset=Math.max(0,Math.min(this.duration,v));this.paint(this.offset);if(was)this.play(true)}
  sectionAt(p){return this.sections.find(s=>p>=s.start&&p<s.end)||null}
  guideLeadFor(section){const bpm=this.tempoSegmentAt(section.start).point.bpm,pulses=this.tempo.numerator===6&&this.tempo.denominator===8?3:Math.max(1,this.tempo.numerator||4);return Math.min(4,Math.max(.75,pulses*60/bpm))}
  cancelPendingGuide(){for(const src of this.pendingGuideNodes||[])try{src.stop()}catch{}if(this.pendingGuideGain)try{this.pendingGuideGain.disconnect()}catch{}this.pendingGuideNodes=[];this.pendingGuideNode=null;this.pendingGuideGain=null;this.pendingGuidePanner=null;this.pendingGuideTrack=null;this.pendingGuideStartAt=null;this.pendingGuideMutedTracks=[];if(this.guideSuppressed){this.guideSuppressed=false;this.updateGains()}}
  playPendingGuide(p){const pending=this.pendingSection;if(!pending||pending.guidePlayed||!this.ctx)return false;const target=pending.section,previous=this.sections.filter(s=>s.start<target.start-.001).reduce((best,s)=>!best||s.start>best.start?s:best,null),captureStart=Math.max(0,target.start-8,previous?.start??0),lead=target.start-captureStart,threshold=pending.boundary-lead;if(lead<=.03||p<threshold||p>=pending.boundary-.02)return false;const guide=this.tracks.find(t=>this.isGuideTrack(t)),solo=this.tracks.some(t=>t.solo);if(!guide||guide.mute||(solo&&!guide.solo)||!guide.gain||!guide.panner)return false;const late=Math.max(0,p-threshold),wantedStart=captureStart+late,wantedEnd=target.start,segments=[];for(const item of guide.items||[]){if(!item?.buffer)continue;const end=item.position+(item.length||Math.max(0,item.buffer.duration-item.offset)),from=Math.max(wantedStart,item.position),to=Math.min(wantedEnd,end);if(to-from>.03)segments.push({item,from,duration:to-from})}if(!segments.length)return false;const gain=this.ctx.createGain();gain.gain.value=Number(guide.volume??1);gain.connect(guide.panner);this.pendingGuideGain=gain;this.pendingGuideTrack=guide;this.pendingGuideNodes=[];guide.gain.gain.value=0;this.guideSuppressed=true;for(const seg of segments){const src=this.ctx.createBufferSource();src.buffer=seg.item.buffer;src.connect(gain);src.start(this.ctx.currentTime+Math.max(0,seg.from-wantedStart),seg.item.offset+(seg.from-seg.item.position),seg.duration);this.pendingGuideNodes.push(src);this.nodes.push(src)}this.pendingGuideNode=this.pendingGuideNodes[0]||null;pending.guidePlayed=true;return true}
  tick(){if(!this.playing)return;if(this.projectLaunchAt&&this.ctx.currentTime<this.projectLaunchAt){this.paint(this.offset);this.raf=requestAnimationFrame(()=>this.tick());return}if(this.projectLaunchAt&&this.ctx.currentTime>=this.projectLaunchAt)this.projectLaunchAt=0;let p=this.position();if(this.pendingSection)this.playPendingGuide(p);if(this.pendingSection&&p>=this.pendingSection.boundary-.005){const next=this.pendingSection;if(this.currentLoop)this.toggleCurrentLoop(false);if(this.loop){this.loop=false;const repeat=this.$("[data-loop]");if(repeat)repeat.checked=false}this.selectSection(next.index,false);this.lastStatusSectionIndex=-1;this.seek(next.section.start);return}if(this.currentLoop&&this.currentLoopSection&&p>=this.currentLoopSection.end){this.seek(this.currentLoopSection.start);return}if(this.loop&&this.loopSection&&p>=this.loopSection.end){this.seek(this.loopSection.start);return}if(p>=this.duration){this.stop();return}this.paint(p);this.raf=requestAnimationFrame(()=>this.tick())}
  paint(p){this.$("[data-progress]").value=p;this.$(".vs-time").textContent=`${fmt(p)} / ${fmt(this.duration)}`;const timeline=this.host.querySelector(".vs-timeline");if(timeline)timeline.style.setProperty("--played-pct",`${this.duration?Math.max(0,Math.min(100,p/this.duration*100)):0}%`);const playhead=this.host.querySelector(".vs-timeline-playhead");if(playhead)playhead.style.left=`${this.duration?Math.max(0,Math.min(100,p/this.duration*100)):0}%`;this.host.querySelectorAll(".vs-segment").forEach((el,i)=>{const s=this.sections[i];el.classList.toggle("current",!!s&&p>=s.start&&p<s.end)});if(this.playing&&!this.pendingSection){const index=this.sections.findIndex(s=>p>=s.start&&p<s.end);if(index>=0&&index!==this.lastStatusSectionIndex){this.lastStatusSectionIndex=index;this.status(`Tocando agora: ${this.sections[index].name}.`)}}if(this.mappingRegions)this.updateMapperPosition()}
  async ensureClickBuffer(){if(this.clickBuffer)return this.clickBuffer;const data=await fetch("./Meuclick.wav").then(r=>{if(!r.ok)throw Error("Click não encontrado");return r.arrayBuffer()});this.clickBuffer=await this.audioContext().decodeAudioData(data);return this.clickBuffer}
  updateTapTempoUi(){const b=this.$("[data-tap-tempo]");if(b)b.textContent="Tap"}
  tapTempo(){const now=performance.now();if(!this.tapTimes.length||now-this.tapTimes[this.tapTimes.length-1]>2200)this.tapTimes=[];this.tapTimes.push(now);if(this.tapTimes.length>6)this.tapTimes.shift();if(this.tapTimes.length>=2){const diffs=[];for(let i=1;i<this.tapTimes.length;i++)diffs.push(this.tapTimes[i]-this.tapTimes[i-1]);const avg=diffs.reduce((a,b)=>a+b,0)/diffs.length,bpm=Math.max(20,Math.min(400,Math.round(60000/avg)));if(this.playing){this.updateTapTempoUi();return}this.applyManualBpm(bpm)}else this.updateTapTempoUi()}
  async startStandaloneClick(){try{const ctx=this.audioContext();await ctx.resume();await this.ensureClickBuffer();this.stopStandaloneClick(false);this.standaloneClickActive=true;this.standaloneStopAt=0;this.standaloneAnchorTime=ctx.currentTime+.035;this.standaloneNextBeatTime=this.standaloneAnchorTime;this.standaloneBeatIndex=0;this.scheduleStandaloneClick();this.standaloneMetroTimer=setInterval(()=>this.scheduleStandaloneClick(),25)}catch(e){console.error(e);this.status("Não foi possível iniciar o click nativo.",true)}}
  stopStandaloneClick(clearSolo=false){clearInterval(this.standaloneMetroTimer);this.standaloneMetroTimer=0;this.standaloneClickActive=false;this.standaloneStopAt=0;for(const n of this.standaloneClickNodes||[])try{n.stop()}catch{}this.standaloneClickNodes=[];if(clearSolo){this.soloClick=false;this.host.querySelector("[data-solo-click]")?.classList.remove("on")}}
  restartStandaloneClick(){if(!this.standaloneClickActive)return;this.startStandaloneClick()}
  nextStandaloneBarTime(minLead=.12){const ctx=this.audioContext(),bpm=Math.max(20,Math.min(400,Number(this.standaloneBpm)||120)),step=60/bpm,rawMeter=+this.$("[data-meter]").value,pulses=rawMeter===6?3:Math.max(1,rawMeter||4),bar=pulses*step,anchor=this.standaloneAnchorTime||ctx.currentTime,target=ctx.currentTime+Math.max(.05,Number(minLead)||.12),bars=Math.max(0,Math.ceil((target-anchor)/bar));return anchor+bars*bar}
  scheduleStandaloneClick(){if(!this.standaloneClickActive||!this.clickBuffer||!this.ctx)return;const ctx=this.ctx;if(this.standaloneStopAt&&ctx.currentTime>=this.standaloneStopAt-.004){clearInterval(this.standaloneMetroTimer);this.standaloneMetroTimer=0;this.standaloneClickActive=false;this.standaloneStopAt=0;return}const bpm=Math.max(20,Math.min(400,Number(this.standaloneBpm)||120)),step=60/bpm,rawMeter=+this.$("[data-meter]").value,pulses=rawMeter===6?3:rawMeter,horizon=this.standaloneStopAt?Math.min(ctx.currentTime+.18,this.standaloneStopAt-.0005):ctx.currentTime+.18;while(this.standaloneNextBeatTime<horizon){const place=((this.standaloneBeatIndex%pulses)+pulses)%pulses,accent=this.$("[data-accent]").checked&&(this.$("[data-accent-position]").value==="first"?place===0:place===pulses-1),src=ctx.createBufferSource(),gain=ctx.createGain();src.buffer=this.clickBuffer;src.playbackRate.value=accent?1.16:1;gain.gain.value=this.metroGain()*(accent?1.3:1);src.connect(gain);gain.connect(this.metroPanner||this.master||ctx.destination);src.start(this.standaloneNextBeatTime);this.standaloneClickNodes.push(src);src.onended=()=>{const i=this.standaloneClickNodes.indexOf(src);if(i>=0)this.standaloneClickNodes.splice(i,1);try{src.disconnect();gain.disconnect()}catch{}};this.standaloneNextBeatTime+=step;this.standaloneBeatIndex++}}
  async restartMetro(){clearInterval(this.metroTimer);this.metroTimer=0;if(!this.playing||!this.$("[data-metro]").checked)return;try{await this.ensureClickBuffer();if(!this.playing)return;this.nextBeat=this.nextBeatAt(this.position());this.scheduleMetro();this.metroTimer=setInterval(()=>this.scheduleMetro(),25)}catch(e){console.error(e);this.status("Não foi possível carregar o som do metrônomo.",true)}}
  tempoSegmentAt(time){let index=0;for(let i=1;i<this.tempo.points.length;i++){if(this.tempo.points[i].time<=time)index=i;else break}return{index,point:this.tempo.points[index],next:this.tempo.points[index+1]}}
  metroGain(){const raw=+this.$("[data-metro-volume]").value;return raw<=1?raw:Math.pow(10,(raw-1)*12/20)}
  nextBeatAt(time){const {point}=this.tempoSegmentAt(time),step=60/point.bpm;return point.time+Math.ceil(Math.max(0,time-point.time-.0001)/step)*step}
  scheduleMetro(){if(!this.playing||!this.clickBuffer)return;const now=this.position(),loopEnd=this.currentLoop&&this.currentLoopSection?this.currentLoopSection.end:this.loop&&this.loopSection?this.loopSection.end:this.duration,safety=loopEnd<this.duration?.3:.04,horizon=Math.min(loopEnd-safety,now+.16);while(this.nextBeat<horizon){const seg=this.tempoSegmentAt(this.nextBeat);if(seg.next&&this.nextBeat>=seg.next.time-.0001){this.nextBeat=seg.next.time;continue}if(this.nextBeat>=now-.02){const step=60/seg.point.bpm,rawMeter=+this.$("[data-meter]").value,pulses=rawMeter===6?3:rawMeter,beat=Math.round((this.nextBeat-seg.point.time)/step),place=((beat%pulses)+pulses)%pulses,accent=this.$("[data-accent]").checked&&(this.$("[data-accent-position]").value==="first"?place===0:place===pulses-1),src=this.ctx.createBufferSource(),gain=this.ctx.createGain();src.buffer=this.clickBuffer;src.playbackRate.value=accent?1.16:1;gain.gain.value=this.metroGain()*(accent?1.3:1);src.connect(gain);gain.connect(this.clickProcessor||this.ctx.destination);src.start(Math.max(this.ctx.currentTime,this.started+this.nextBeat));this.nodes.push(src)}this.nextBeat+=60/seg.point.bpm}}
  save(){try{localStorage.setItem("zosma-zlplay:"+this.projectId,JSON.stringify({groupNamingVersion:2,tracks:this.tracks.map(t=>({name:t.name,volume:t.volume,mute:t.mute,solo:t.solo,pan:t.pan||0,group:t.group||"VS",pitchEnabled:typeof t.pitchEnabled==="boolean"?t.pitchEnabled:undefined})),vsPan:this.vsPan||0,vsVolume:Number(this.vsVolume??1),vsMute:!!this.vsMute,vsSolo:!!this.vsSolo,groupStates:[...(this.groupStates||new Map()).entries()]}))}catch{}}
  restore(){try{const data=JSON.parse(localStorage.getItem("zosma-zlplay:"+this.projectId)||"[]"),s=Array.isArray(data)?data:(data.tracks||[]);this.vsPan=Array.isArray(data)?0:(data.vsPan||0);this.vsVolume=Array.isArray(data)?1:Number(data.vsVolume??1);this.vsMute=Array.isArray(data)?false:!!data.vsMute;this.vsSolo=Array.isArray(data)?false:!!data.vsSolo;this.groupStates=new Map(Array.isArray(data)?[]:(data.groupStates||[]));this.groupPans=new Map(Array.isArray(data)?[]:(data.groups||[]));for(const [name,pan] of this.groupPans)this.ensureGroupState(name).pan=Number(pan)||0;for(const t of this.tracks){const x=s.find(v=>v.name===t.name);if(x){Object.assign(t,{volume:x.volume,mute:x.mute,solo:x.solo,pan:x.pan||0,output:x.output||null,group:!data.groupNamingVersion&&["Outros","GRUPO"].includes(x.group)?"VS":x.group||t.group||"VS"});if(typeof x.pitchEnabled==="boolean")t.pitchEnabled=x.pitchEnabled;t.restored=true}if(!this.isSpecialTrack(t))this.ensureGroupState(t.group||"VS")}}catch{}}
}

function mount(){
  let host=document.querySelector("#zosma-vs-study");
  if(!host){
    host=document.createElement("div");
    host.id="zosma-vs-study";
    document.body.append(host);
  }
  if(host.dataset.mounted==="1")return;
  host.dataset.mounted="1";
  host.hidden=false;
  const study=new VSStudy(host);
  study.addLiveControls();
  const back=host.querySelector("[data-back]");
  if(back)back.remove();
  const mode=host.querySelector("[data-mode]");
  if(mode)mode.disabled=false;
  const transport=host.querySelector(".vs-transport"),
        metro=host.querySelector(".vs-metronome"),
        timeline=host.querySelector(".vs-timeline");
  if(transport&&metro&&timeline){transport.after(metro);metro.after(timeline)}
  const playBtn=host.querySelector("[data-play]"),pauseBtn=host.querySelector("[data-pause]"),stopBtn=host.querySelector("[data-stop]");const setPlaying=v=>{if(playBtn){playBtn.classList.toggle("is-playing",!!v);playBtn.setAttribute("aria-pressed",v?"true":"false")}};if(playBtn)playBtn.addEventListener("click",()=>setTimeout(()=>setPlaying(true),0));if(pauseBtn)pauseBtn.addEventListener("click",()=>setPlaying(false));if(stopBtn)stopBtn.addEventListener("click",()=>setPlaying(false));
  host.addEventListener("dblclick",e=>{const r=e.target;if(!(r instanceof HTMLInputElement)||r.type!=="range")return;let v=null;if(r.matches("[data-pan],[data-metro-pan]"))v="0";else if(r.matches(".vs-channel-fader,[data-master],[data-volume],[data-metro-volume]"))v="1";if(v===null)return;r.value=v;r.dispatchEvent(new Event("input",{bubbles:true}));r.dispatchEvent(new Event("change",{bubbles:true}));});

  /* ZLPlay Library 1.7.0
     Pasta principal = repertório/repertório.
     Subpastas = músicas isoladas.
     .zlplay da raiz = índice/ordem.
     .zlplay dentro da música = configuração individual.
  */
  const ZL_LIBRARY_VERSION=1;
  const zlNorm=s=>String(s||"").trim().replace(/\s+/g," ");
  const zlIsAudio=f=>/\.(wav|mp3|ogg|m4a|aac|flac)$/i.test(f.name||"");
  const zlIsRpp=f=>/\.rpp$/i.test(f.name||"");
  const zlIsProject=f=>/\.zlplay$/i.test(f.name||"");
  const zlIsLibrary=f=>/\.(zlset|zlplaylibrary)$/i.test(f.name||"");

  function zlRelativeParts(file){
    const p=(file.webkitRelativePath||file.relativePath||file.name||"").replace(/\\/g,"/");
    return p.split("/").filter(Boolean);
  }
  function zlScanSelectedTree(files){
    const list=[...files], byFolder=new Map(), rootProjects=[],nested=[];
    for(const f of list){
      const parts=zlRelativeParts(f);
      /* webkitdirectory:
         "Pasta/Repertório.zlplaylibrary" = raiz do repertório;
         "Pasta/Musica/arquivo" = raiz da música;
         "Pasta/Musica/Media/arquivo" = subpasta, portanto IGNORADA. */
      if(parts.length<=2){ if(zlIsLibrary(f))rootProjects.push(f); continue; }
      if(parts.length!==3){nested.push(f);continue;}
      const root=parts[0], songFolder=parts[1];
      if(!songFolder)continue;
      if(!byFolder.has(songFolder))byFolder.set(songFolder,{name:songFolder,root,files:[],rpps:[],projects:[],audio:[]});
      const e=byFolder.get(songFolder); e.files.push(f);
      if(zlIsRpp(f))e.rpps.push(f);
      if(zlIsProject(f))e.projects.push(f);
      if(zlIsAudio(f))e.audio.push(f);
    }
    for(const file of nested){const song=byFolder.get(zlRelativeParts(file)[1]);if(song&&(song.projects.length||song.rpps.length))song.files.push(file);}
    const songs=[...byFolder.values()].filter(x=>x.audio.length||x.rpps.length||x.projects.length);
    return {songs,rootProjects};
  }
  async function zlDownloadJson(data,name){
    if(window.zlplayNative?.saveLibrary){
      const entries=[...new Set((window.__ZL_LIBRARY_SCAN__?.songs||[]).flatMap(song=>song.files))].map(file=>({token:file._zlToken,relativePath:file.webkitRelativePath||file.relativePath}));
      try{
        const result=await window.zlplayNative.saveLibrary({data,entries,language:window.__ZL_I18N__?.language||'pt-BR'});
        if(result)study.status?.(`Repertório salvo em ${result.name}.`);
        return !!result;
      }catch(error){study.status?.(error.message,true);return false;}
    }
    const text=JSON.stringify(data,null,2);
    if("showSaveFilePicker" in window){
      try{
        const handle=await window.showSaveFilePicker({
          suggestedName:name,
          types:[{description:"Repertório ZLPlay",accept:{"application/json":[".zlset"]}}]
        });
        const writable=await handle.createWritable();
        await writable.write(text);
        await writable.close();
        study.status?.(`Repertório salvo em ${handle.name}.`);
        return true;
      }catch(err){
        if(err?.name==="AbortError")return false;
        console.warn("Salvar como indisponível; usando download.",err);
      }
    }
    const blob=new Blob([text],{type:"application/json"});
    const a=document.createElement("a");a.href=URL.createObjectURL(blob);a.download=name;
    document.body.append(a);a.click();setTimeout(()=>{URL.revokeObjectURL(a.href);a.remove()},500);
    study.status?.("Escolha onde salvar o arquivo do repertório.");
    return true;
  }
  function zlLibraryPayload(songs,order){
    return {type:"zlplay-library",version:ZL_LIBRARY_VERSION,createdAt:new Date().toISOString(),
      order:[...order],songs:songs.map(s=>({id:s.name,name:s.name,folder:s.name,
        hasRpp:!!s.rpps.length,project:s.projects[0]?.name||null,originalKey:s.originalKey||null,tonalKey:s.tonalKey||null,source:s.selectedSource?.name||null,rpp:s.selectedRpp?.name||s.rpps[0]?.name||null}))};
  }
  function zlShowLibraryManager(scan,existing=null){
    for(const song of scan.songs){const saved=existing?.songs?.find(s=>(s.folder||s.name)===song.name)?.rpp;if(saved){const name=String(saved).replace(/\\/g,"/").split("/").pop();song.selectedRpp=song.rpps.find(f=>f.name===name)||song.rpps.find(f=>zlRelativeParts(f).at(-1)===name)||song.rpps[0];}else if(!song.selectedRpp||!song.rpps.includes(song.selectedRpp))song.selectedRpp=song.rpps[0];}
    for(const song of scan.songs){const saved=existing?.songs?.find(s=>(s.folder||s.name)===song.name);song.originalKey=saved?.originalKey||song.originalKey||null;song.tonalKey=saved?.tonalKey||song.tonalKey||null;const files=[...song.projects,...song.rpps];const name=String(saved?.source||saved?.rpp||"").replace(/\\/g,"/").split("/").pop();if(saved?.source||(!song.selectedSource&&name))song.selectedSource=files.find(f=>f.name===name)||files[0];song.selectedSource||=files[0];}
    let order=(existing?.order||[]).filter(n=>scan.songs.some(s=>s.name===n));
    for(const s of scan.songs)if(!order.includes(s.name))order.push(s.name);
    const wrap=document.createElement("div");wrap.className="zl-library-modal";
    const box=document.createElement("div");box.className="zl-library-box";
    box.innerHTML=`<div class="zl-library-head"><strong>Repertório ZLPlay</strong><button type="button" data-close>×</button></div>
      <p>${scan.songs.length} música(s) encontrada(s). Arraste para definir a ordem.</p>
      <div class="zl-library-list"></div>
      <div class="zl-library-actions"><button type="button" data-save>Salvar repertório</button><button type="button" data-open>Abrir primeira música</button></div>`;
    wrap.append(box);(document.fullscreenElement||document.body).append(wrap);
    const list=box.querySelector(".zl-library-list");
    let selected=window.__ZL_LIBRARY_CURRENT__&&order.includes(window.__ZL_LIBRARY_CURRENT__)?window.__ZL_LIBRARY_CURRENT__:order[0]||null;
    const render=()=>{list.innerHTML="";order.forEach((name,i)=>{const row=document.createElement("div");row.className="zl-library-row"+(name===selected?" selected":"");row.draggable=true;row.dataset.name=name;
      const song=scan.songs.find(s=>s.name===name);
      row.innerHTML=`<span class="zl-grip">☰</span><b>${i+1}.</b><span class="zl-song-name" title="${name.replace(/"/g,"&quot;")}">${name}</span><small>${song?.selectedSource?/\.zlplay$/i.test(song.selectedSource.name)?".zlplay":"RPP":"áudios"}</small>`;
      if(song.projects.length+song.rpps.length>1){
        const label=document.createElement("label");label.className="zl-library-rpp";label.textContent="Projeto de origem";
        const select=document.createElement("select");select.className="vs-select";select.setAttribute("aria-label","Arquivo de projeto de "+name);
        [...song.projects,...song.rpps].forEach((file,index)=>{const option=document.createElement("option");option.value=String(index);option.textContent=file.name;option.selected=file===song.selectedSource;select.append(option)});
        select.onchange=()=>{song.selectedSource=[...song.projects,...song.rpps][+select.value];if(/\.rpp$/i.test(song.selectedSource.name))song.selectedRpp=song.selectedSource};
        for(const event of ["click","dblclick","mousedown","dragstart"])select.addEventListener(event,e=>e.stopPropagation());
        label.append(select);row.append(label);
      }
      row.ondragstart=e=>e.dataTransfer.setData("text/plain",name);
      row.ondragover=e=>e.preventDefault();
      row.ondrop=e=>{e.preventDefault();const from=e.dataTransfer.getData("text/plain"),to=name;if(from===to)return;const a=order.indexOf(from),b=order.indexOf(to);order.splice(a,1);order.splice(b,0,from);window.__ZL_LIBRARY_ORDER__=[...order];render()};
      row.onclick=e=>{if(e.target.closest("button,select,input,label,.zl-library-keys"))return;selected=name;render()};
      row.ondblclick=()=>{selected=name;wrap.remove();window.__ZL_LIBRARY_OPEN_SONG__?.(name)};
      list.append(row);window.__ZL_DECORATE_SONG__?.(row,song)})};
    render();
    box.querySelector("[data-close]").onclick=()=>wrap.remove();
    box.querySelector("[data-save]").onclick=async()=>{const ok=await zlDownloadJson(zlLibraryPayload(scan.songs,order),"Repertório.zlset");if(ok)window.__ZL_LIBRARY_ORDER__=[...order]};
    box.querySelector("[data-open]").textContent="Abrir música";
    box.querySelector("[data-open]").onclick=()=>{if(selected){wrap.remove();window.__ZL_LIBRARY_OPEN_SONG__?.(selected)}};
    window.__ZL_LIBRARY_ORDER__=[...order];
  }
  study.afterProjectSaved=async(handle,text)=>{
    const scan=window.__ZL_LIBRARY_SCAN__,song=scan?.songs.find(s=>s.name===window.__ZL_LIBRARY_CURRENT__);
    if(!song||!study.currentProjectFiles.some(f=>song.files.includes(f._nativeFile)))return;
    const file=await handle.getFile(),prefix=zlRelativeParts(song.files[0]).slice(0,-1).join("/");
    Object.defineProperty(file,"webkitRelativePath",{value:prefix+"/"+file.name});
    const previous=song.selectedSource||song.projects[0]||song.rpps[0],same=previous?.name===file.name;
    song.files=song.files.filter(f=>f.name!==file.name);song.projects=song.projects.filter(f=>f.name!==file.name);song.files.push(file);song.projects.push(file);song.selectedSource=file;
    if(!same&&confirm("Projeto salvo. Deseja salvar o repertório usando esta nova versão .zlplay?"))await zlDownloadJson(zlLibraryPayload(scan.songs,window.__ZL_LIBRARY_ORDER__||scan.songs.map(s=>s.name)),"Repertório.zlset");
  };
  async function zlReadProjectFile(file){try{return JSON.parse(await file.text())}catch{return null}}

  async function zlProcessLibraryFolder(files,{askCreate=true}={}){
    const scan=zlScanSelectedTree(files);
    if(scan.songs.length<1){study.status?.("Nenhuma pasta de música encontrada.");return false}
    window.__ZL_LIBRARY_SCAN__=scan;
    const existingFile=scan.rootProjects.find(f=>/^repertório\.zlplaylibrary$/i.test(f.name||""))||scan.rootProjects.find(zlIsLibrary);
    const existing=existingFile?await zlReadProjectFile(existingFile):null;
    if(existingFile&&existing?.type!=="zlplay-library"){
      study.status?.("Arquivo .zlplaylibrary inválido.");return false;
    }
    if(existing?.type==="zlplay-library"){
      const known=new Set((existing.songs||[]).map(s=>s.folder||s.name));
      const added=scan.songs.filter(s=>!known.has(s.name));
      if(added.length)study.status?.(`${added.length} nova(s) música(s) encontrada(s) no repertório.`);
      zlShowLibraryManager(scan,existing);
      return true;
    }
    if(scan.songs.length>=2||(!askCreate&&scan.songs.length>=1)){
      const ok=!askCreate||confirm(`${scan.songs.length} pastas de músicas foram encontradas.\n\nCriar um repertório ZLPlay para esta pasta principal?`);
      if(ok){zlShowLibraryManager(scan,null);return true}
    }else{
      study.status?.("Esta pasta contém apenas uma música. Use Abrir pasta / projeto.");
    }
    return false;
  }
  window.__ZL_PARSE_RPP__=parseRpp;window.__ZL_PARSE_RPP_TEMPO__=parseRppTempo;window.__ZL_PARSE_RPP_SECTIONS__=parseRppSections;window.__ZL_REGION_PRESETS__=REGION_PRESETS;window.__ZL_PROCESS_LIBRARY_FILES__=zlProcessLibraryFolder;
  window.__ZL_SAVE_LIBRARY__=async()=>{const scan=window.__ZL_LIBRARY_SCAN__;if(!scan?.songs?.length)return false;return zlDownloadJson(zlLibraryPayload(scan.songs,window.__ZL_LIBRARY_ORDER__||scan.songs.map(s=>s.name)),"Repertório.zlset");};
  function zlChooseFolderOpenMode(){
    return new Promise(resolve=>{
      const wrap=document.createElement("div");wrap.className="zl-openmode-modal";
      const box=document.createElement("div");box.className="zl-openmode-box";
      box.innerHTML=`<strong>Como deseja abrir esta pasta?</strong>
        <p>Projeto individual importa somente os arquivos que estão diretamente na pasta da música. Conteúdo de subpastas como Media, Peaks e Backups é ignorado.</p>
        <div class="zl-openmode-actions">
          <button type="button" data-individual>Projeto individual</button>
          <button type="button" data-library>Repertório / Repertório</button>
          <button type="button" data-cancel>Cancelar</button>
        </div>`;
      wrap.append(box);document.body.append(wrap);
      const done=value=>{wrap.remove();resolve(value)};
      box.querySelector("[data-individual]").onclick=()=>done("individual");
      box.querySelector("[data-library]").onclick=()=>done("library");
      box.querySelector("[data-cancel]").onclick=()=>done(null);
      wrap.onclick=e=>{if(e.target===wrap)done(null)};
    });
  }
  function zlInstallLibraryFlow(){
    const inputs=[...host.querySelectorAll('input[type="file"]')];
    const folderInput=inputs.find(i=>i.webkitdirectory||i.hasAttribute("webkitdirectory")||i.multiple);
    if(!folderInput)return;
    folderInput.addEventListener("change",async e=>{
      const files=[...e.target.files||[]];if(!files.length)return;
      if(window.__ZL_FOLDER_OPEN_MODE__==="library"){
        window.__ZL_FOLDER_OPEN_MODE__=null;
        e.stopImmediatePropagation();e.preventDefault();
        await zlProcessLibraryFolder(files,{askCreate:true});
        return;
      }
      /* Projeto individual: não tenta detectar repertório.
         O evento segue para o importador original, inclusive com Media/Peaks/Backups. */
      window.__ZL_FOLDER_OPEN_MODE__=null;
    },true);

    // Isolamento: ao abrir música do repertório, só os File objects daquela subpasta são enviados ao importador existente.
    window.__ZL_LIBRARY_OPEN_SONG__=name=>{
      const scan=window.__ZL_LIBRARY_SCAN__,song=scan?.songs.find(s=>s.name===name);if(!song)return;
      window.__ZL_LIBRARY_CURRENT__=name;
      if(song.selectedSource||song.projects.length||song.rpps.length){
        const chosen=song.selectedSource||song.projects[0]||song.rpps[0];
        const files=song.files.map(f=>study.wrap(f,f.webkitRelativePath||f.name)),source=files[song.files.indexOf(chosen)];
        if(/\.zlplay$/i.test(chosen.name))return study.loadTomCerto(files,source);
        return study.load(files.filter(f=>!/\.zlplay$/i.test(f.name)),source);
      }
      // Projetos ZLPlay e áudio seguem pelo importador individual.
      const dt=new DataTransfer();song.files.forEach(f=>dt.items.add(f));
      folderInput.files=dt.files;
      folderInput.dispatchEvent(new Event("change",{bubbles:true}));
    };
  }
  setTimeout(zlInstallLibraryFlow,0);

  setTimeout(()=>{const transport=host.querySelector(".vs-transport");if(!transport||transport.querySelector("[data-library-order]"))return;
    const ref=transport.querySelector("button");
    const mk=(label,title,attr)=>{const b=document.createElement("button");b.type="button";if(ref)b.className=ref.className;b.textContent=label;b.title=title;b.setAttribute(attr,"");return b};
    const prev=mk("‹","Música anterior","data-library-prev"),lib=mk("Repertório","Ver e organizar músicas","data-library-order"),next=mk("›","Próxima música","data-library-next");
    prev.onclick=()=>zlNavigateLibrary(-1);
    next.onclick=()=>zlNavigateLibrary(1);
    lib.onclick=()=>{if(window.__ZL_LIBRARY_SCAN__)zlShowLibraryManager(window.__ZL_LIBRARY_SCAN__,{order:window.__ZL_LIBRARY_ORDER__||[]});else study.status?.("Abra uma pasta principal com várias músicas para criar/abrir o repertório.")};
    transport.append(prev,lib,next);
  },0);



  function zlNavigateLibrary(delta){
    const order=window.__ZL_LIBRARY_ORDER__||[],cur=window.__ZL_LIBRARY_CURRENT__;
    if(!order.length)return;
    let i=order.indexOf(cur);if(i<0)i=0;
    const ni=i+delta;if(ni<0||ni>=order.length)return;
    const target=order[ni];
    try{if(study.playing)study.halt?.()}catch{}
    window.__ZL_NEXT_AUDIO_READY__=false;window.__ZL_NEXT_AUDIO_LOADING__=false;window.__ZL_NEXT_FILES__=null;
    window.__ZL_LIBRARY_OPEN_SONG__?.(target);
  }
  function zlPrepareNextMetadata(){
    const order=window.__ZL_LIBRARY_ORDER__||[],cur=window.__ZL_LIBRARY_CURRENT__,i=order.indexOf(cur);
    const next=i>=0?order[i+1]:null;if(!next)return;
    window.__ZL_NEXT_META__={name:next,prepared:true};
  }
  function zlPreloadNextAudio(){
    if(window.__ZL_NEXT_AUDIO_READY__||window.__ZL_NEXT_AUDIO_LOADING__)return;
    const scan=window.__ZL_LIBRARY_SCAN__,name=window.__ZL_NEXT_META__?.name;
    const song=scan?.songs.find(s=>s.name===name);if(!song)return;
    window.__ZL_NEXT_AUDIO_LOADING__=true;
    // Mantém File objects/referências prontos; não conecta nenhum AudioNode e não toca nada.
    window.__ZL_NEXT_FILES__=song.files.slice();
    window.__ZL_NEXT_AUDIO_READY__=true;window.__ZL_NEXT_AUDIO_LOADING__=false;
  }
  setInterval(()=>{if(!window.__ZL_LIBRARY_CURRENT__)return;zlPrepareNextMetadata();
    try{const p=study.position?.()||0,d=study.duration||0;if(study.playing&&d>0&&d-p<=20)zlPreloadNextAudio()}catch{}
  },1000);


  setTimeout(()=>{const stop=host.querySelector("[data-stop]");if(stop)stop.addEventListener("click",()=>{if(!window.__ZL_LIBRARY_CURRENT__)return;zlPrepareNextMetadata();zlPreloadNextAudio()})},0);


  setTimeout(()=>{
    const buttons=[...host.querySelectorAll("button")];
    const openFolder=buttons.find(b=>/abrir pasta\s*\/\s*projeto/i.test(b.textContent||""));
    if(openFolder&&!window.zlplayNative&&!openFolder.dataset.zlOpenModeBound){
      openFolder.dataset.zlOpenModeBound="1";
      openFolder.addEventListener("click",async e=>{
        if(window.__ZL_OPENMODE_REENTRY__){window.__ZL_OPENMODE_REENTRY__=false;return}
        e.preventDefault();e.stopImmediatePropagation();
        const mode=await zlChooseFolderOpenMode();
        if(!mode)return;
        window.__ZL_FOLDER_OPEN_MODE__=mode;
        if(mode==="library"){
          /* Usa o input de pasta já existente; o listener de captura enviará
             a seleção exclusivamente ao fluxo de repertório. */
          folderInput.click();
        }else{
          /* Reentra no fluxo original do botão para manter exatamente
             o comportamento anterior de projeto individual. */
          window.__ZL_OPENMODE_REENTRY__=true;
          openFolder.click();
        }
      },true);
    }
  },0);

  setTimeout(()=>{
    const buttons=[...host.querySelectorAll("button")];
    const openLib=buttons.find(b=>/abrir repertório/i.test(b.textContent||""));
    if(!openLib||openLib.dataset.zlLibraryBound)return;
    openLib.dataset.zlLibraryBound="1";
    const inp=document.createElement("input");
    inp.type="file";inp.multiple=true;inp.setAttribute("webkitdirectory","");inp.style.display="none";
    host.append(inp);
    inp.onchange=async()=>{const files=[...inp.files||[]];if(files.length)await zlProcessLibraryFolder(files,{askCreate:true});inp.value=""};
    openLib.onclick=e=>{e.preventDefault();e.stopImmediatePropagation();inp.click()};
  },0);

  setTimeout(()=>{
    host.classList.add("vs-compact-layout");
    const title=host.querySelector(".vs-title-row"),transport=host.querySelector(".vs-transport"),metro=host.querySelector(".vs-metronome"),tools=host.querySelector(".vs-mixer-tools"),pad=host.querySelector(".vs-pad-row");
    const actions=host.querySelector(".vs-project-actions");const opening=host.querySelector(".vs-actions");opening.append(actions);
    host.querySelector(".vs-head").insertBefore(opening,host.querySelector(".vs-head-actions"));
    const navigation=document.createElement("nav");navigation.className="vs-library-navigation";navigation.setAttribute("aria-label","Repertório de músicas");
    for(const selector of ["[data-library-prev]","[data-library-order]","[data-library-next]"]){const button=host.querySelector(selector);if(button)navigation.append(button)}
    title.insertBefore(navigation,host.querySelector(".vs-key"));
    const performance=document.createElement("div");performance.className="vs-performance-bar";transport.before(performance);performance.append(transport,pad);
    tools.insertBefore(metro,tools.querySelector(".vs-mixer-actions"));
  },0);

  window.__ZLPLAY__=study;
}
installNativeAudio(VSStudy);
document.readyState==="loading"?document.addEventListener("DOMContentLoaded",mount):mount();

