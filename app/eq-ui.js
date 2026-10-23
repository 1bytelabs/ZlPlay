import {responseDb} from './eq-response.mjs';
const esc=s=>String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const toSlider=f=>Math.log(Math.max(20,f)/20)/Math.log(1000)*1000;
const fromSlider=v=>Math.round(20*1000**(v/1000));
const hz=f=>f>=1000?`${+(f/1000).toFixed(2)} kHz`:`${f} Hz`;
export function openChannelEffects(id){
 document.querySelector('.zl-fx-modal')?.remove();this.channelEffects||={};const current=this.channelEffects[id]||{},bands=[0,1,2,3].map(i=>current.bands?.[i]||{freq:[120,500,2500,8000][i],gain:0,q:.707});
 const wrap=document.createElement('div');wrap.className='zl-audio-modal zl-fx-modal';
 const slider=(label,attr,min,max,step,value)=>`<label>${label}<input ${attr} type="range" min="${min}" max="${max}" step="${step}" value="${value}"><output></output></label>`;
 wrap.innerHTML=`<section class="zl-audio-box"><header><strong>${esc(id.replace(/^track:|^group:/,''))} · Ganho e EQ</strong><button data-close aria-label="Fechar">×</button></header><label class="zl-inline"><input data-fx-enabled type="checkbox" ${current.enabled?'checked':''}> Ativar ganho e EQ</label><div class="zl-fx-filters">${slider('Ganho antes do fader','data-fx-trim',-24,24,.5,Number(current.trim)||0)}${slider('Corte de graves','data-fx-hp',0,400,5,Number(current.highpass)||0)}${slider('Corte de agudos','data-fx-lp',0,1001,1,current.lowpass?Math.max(1,toSlider(current.lowpass)+1):0)}</div><svg data-fx-curve viewBox="0 0 600 150" role="img" aria-label="Curva do equalizador com frequências em Hz e kHz"></svg><div class="zl-fx-bands">${bands.map((b,i)=>`<fieldset><legend>${['Graves','Médios 1','Médios 2','Agudos'][i]}</legend>${slider('Frequência',`data-fx-freq="${i}"`,0,1000,1,toSlider(b.freq))}${slider('Ganho',`data-fx-gain="${i}"`,-18,18,.5,b.gain)}${slider('Q · largura',`data-fx-q="${i}"`,.1,10,.01,b.q)}</fieldset>`).join('')}</div><button data-fx-reset>Zerar ajustes</button></section>`;
 (document.fullscreenElement||document.body).append(wrap);
 const read=selector=>Number(wrap.querySelector(selector).value);
 const apply=event=>{
  const lp=read('[data-fx-lp]'),value={enabled:wrap.querySelector('[data-fx-enabled]').checked,trim:read('[data-fx-trim]'),highpass:read('[data-fx-hp]'),lowpass:lp>0?fromSlider(lp-1):0,bands:bands.map((b,i)=>({freq:fromSlider(read(`[data-fx-freq="${i}"]`)),gain:read(`[data-fx-gain="${i}"]`),q:read(`[data-fx-q="${i}"]`)}))};
  const previous=this.channelEffects[id]||current;
  value.lowpass=event?.target?.hasAttribute('data-fx-lp')?value.lowpass:Number(previous.lowpass)||0;
  value.bands.forEach((band,i)=>{const saved=previous.bands?.[i]||bands[i];if(event?.target?.dataset.fxFreq!==String(i))band.freq=saved.freq;if(event?.target?.dataset.fxQ!==String(i))band.q=saved.q;});
  if(event)this.channelEffects[id]=value;
  wrap.querySelectorAll('input[type=range]').forEach(input=>{let text=input.value;if(input.hasAttribute('data-fx-freq'))text=hz(value.bands[Number(input.dataset.fxFreq)].freq);else if(input.hasAttribute('data-fx-hp'))text=value.highpass?hz(value.highpass):'Desligado';else if(input.hasAttribute('data-fx-lp'))text=value.lowpass?hz(value.lowpass):'Desligado';else if(input.hasAttribute('data-fx-gain')||input.hasAttribute('data-fx-trim'))text+=' dB';input.nextElementSibling.textContent=text;input.setAttribute('aria-valuetext',text);input.title=text;});
  const points=[];for(let x=0;x<=600;x+=3){const db=responseDb(value,20*1000**(x/600),this._nativeState?.sampleRate||48000);points.push(`${x},${60-Math.max(-24,Math.min(24,db))*2}`);}
  const axis=[20,50,100,200,500,1000,2000,5000,10000,20000].map(f=>{const x=Math.log(f/20)/Math.log(1000)*600;return `<path d="M${x} 10V115" stroke="#302a3d"/><text x="${x}" y="138" text-anchor="${f===20?'start':f===20000?'end':'middle'}" fill="#c7bed7" font-size="11">${f>=1000?f/1000+'k':f}</text>`;}).join('');
  wrap.querySelector('svg').innerHTML=`${axis}<path d="M0 60H600" stroke="#5b506b"/><polyline points="${points.join(' ')}" fill="none" stroke="#ad83fb" stroke-width="2"/>`;
  if(event){this.nativeSync();this.syncNativeAux().catch(()=>{});this.save();}
 };
 wrap.querySelector('[data-close]').onclick=()=>wrap.remove();wrap.addEventListener('input',apply);wrap.querySelector('[data-fx-reset]').onclick=()=>{delete this.channelEffects[id];wrap.remove();this.nativeSync();this.syncNativeAux().catch(()=>{});this.openChannelEffects(id);};apply();
}
