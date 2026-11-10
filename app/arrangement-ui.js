import {editorExit} from './editor-session.js';
import {Arrangement,regionRef,findRegion} from './arrangement.mjs';
const esc=s=>String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
export function installArrangement(Player){
 const p=Player.prototype,old=Object.fromEntries(Object.getOwnPropertyNames(p).filter(k=>typeof p[k]==='function').map(k=>[k,p[k]]));
 p.projectPayload=function(){const data=old.projectPayload.call(this);data.arrangement=(this.arrangement||new Arrangement()).toJSON();data.settings.liveTranspose=Number(this.liveTranspose)||0;return data;};
 p.loadProjectManifest=async function(files,manifest,...args){this._arrangementManifest=manifest;try{return await old.loadProjectManifest.call(this,files,manifest,...args)}finally{this._arrangementManifest=null}};
 p.finishLoadedProject=function(...args){this.arrangement=new Arrangement(this._arrangementManifest?.arrangement);const result=old.finishLoadedProject.apply(this,args);const delta=Math.max(-12,Math.min(12,Number(this._arrangementManifest?.settings?.liveTranspose)||0));if(delta){this.liveTranspose=delta;const base=Number(this.$('[data-live-key]')?.value||0);this.$('[data-live-key]').value=String((base+delta+12)%12);this.padNote=this.padAutoNote();this.updatePadUi();this.nativeSync();}this.refreshArrangementUi();return result;};
 p.refreshArrangementUi=function(){const a=this.arrangement||new Arrangement();for(const mode of this.host.querySelectorAll('[data-arrangement-mode]')){mode.textContent=a.mode==='rehearsal'?'Ensaio':'Ao vivo';mode.classList.toggle('on',a.mode==='rehearsal');mode.title='Modo das automações: clique para alternar ensaio / ao vivo';}for(const b of this.host.querySelectorAll('[data-arrangement]'))b.textContent=`Arranjo${a.rules.length?' · '+a.rules.length:''}`;this._renderArrangement?.();};
 p.updateArrangement=function(){const a=this.arrangement;if(!this.playing||!a?.enabled||this.pendingSection||this.currentLoop||this.loop||this._nativeSeeking)return;const at=this.position(),cursor=a.cursor;a.advancePast(at);if(a.cursor!==cursor)this.refreshArrangementUi();const r=a.next;if(!r)return;const from=findRegion(this.sections,r.from),to=findRegion(this.sections,r.to);if(from<0||to<0)return;const source=this.sections[from];if(at<source.start||at>=source.end-.04)return;
  old.selectSection.call(this,to,true);if(this.pendingSection){this.pendingSection.automationId=r.id;this.pendingSection.jumpId=`auto:${r.id}:${a.run}:${a.cursor}`;this.nativeSync();}
 };
 p.tick=function(){this.updateArrangement();return old.tick.call(this);};
 p.seek=function(position,internal=false){
  if(!internal){
   this.pendingSection=null;
   this.cancelPendingGuide();
   this.host.querySelectorAll('.vs-segment.pending').forEach(el=>el.classList.remove('pending'));
   this.arrangement?.seek(Math.max(0,Math.min(this.duration,position)));
   this.refreshArrangementUi();
  }
  return old.seek.call(this,position);
 };
 p.selectSection=function(index,seek=false){const pending=this.pendingSection,result=old.selectSection.call(this,index,seek);if(!seek&&pending){if(pending.automationId)this.arrangement?.complete(pending.automationId);else this.arrangement?.seek(pending.section.start);this.refreshArrangementUi();}if(seek&&this.pendingSection){this._manualJumpId=(this._manualJumpId||0)+1;this.pendingSection.jumpId=`manual:${this._manualJumpId}`};return result;};
 p.stop=function(...args){const result=old.stop.apply(this,args);this.arrangement?.restart();this.refreshArrangementUi();return result;};
 p.goToStart=function(...args){const result=old.goToStart.apply(this,args);this.arrangement?.seek(0);this.refreshArrangementUi();return result;};
 p.setArrangementMode=function(mode){
  const a=this.arrangement||=new Arrangement();
  if(a.mode===mode)return;
  if(this.pendingSection?.automationId){
   this.pendingSection=null;this.cancelPendingGuide();
   this.host.querySelectorAll('.vs-segment.pending').forEach(el=>el.classList.remove('pending'));
   this.nativeSync();
  }
  a.setMode(mode,this.position());this.refreshArrangementUi();
 };
 p.openArrangement=function(){if(document.querySelector('.zl-arrangement-modal'))return;this.arrangement||=new Arrangement();const session=this.captureEditSession();const a=this.arrangement,wrap=document.createElement('div');wrap.className='zl-audio-modal zl-arrangement-modal';wrap.innerHTML='<section class="zl-audio-box"><header><strong>Arranjo · saltos automáticos</strong><button data-close>×</button></header><div data-arrangement-body></div></section>';(document.fullscreenElement||document.body).append(wrap);const body=wrap.querySelector('[data-arrangement-body]');
  const render=()=>{const busy=this.playing;const options=this.sections.map((s,i)=>`<option value="${i}">${esc(s.name)} · ${Math.floor(s.start/60)}:${String(Math.floor(s.start%60)).padStart(2,'0')}</option>`).join('');body.innerHTML=`<div class="zl-arrangement-options"><label><input data-auto-enabled aria-label="Saltos automáticos" type="checkbox" ${a.enabled?'checked':''}></label><label>Modo <select data-auto-mode><option value="live" ${a.mode==='live'?'selected':''}>Ao vivo</option><option value="rehearsal" ${a.mode==='rehearsal'?'selected':''}>Ensaio</option></select></label></div><p>${a.mode==='live'?'Cada automação executa uma vez por passagem. Stop ou trocar de modo rearma as regras.':'Volte o playhead para repetir os saltos.'} Somente a próxima regra pode disparar.</p><div class="zl-audio-pair"><label>Região de origem<select data-auto-from ${busy?'disabled':''}>${options}</select></label><label>Destino<select data-auto-to ${busy?'disabled':''}>${options}</select></label></div><button data-auto-add ${busy||this.sections.length<2?'disabled':''}>+ Adicionar automação</button><p data-auto-error></p><ol class="zl-arrangement-list">${a.rules.map((r,i)=>`<li><span class="${a.completed.includes(r.id)?'zl-auto-executed':i===a.cursor?'zl-auto-next':''}">${i===a.cursor?'Próxima · ':a.completed.includes(r.id)?'Executada · ':''}${esc(r.from.name)} → ${esc(r.to.name)}${findRegion(this.sections,r.from)<0||findRegion(this.sections,r.to)<0?' · Região ausente':''}</span><button data-auto-remove="${i}" ${busy?'disabled':''}>Remover</button></li>`).join('')||'<p>Nenhuma automação cadastrada.</p>'}</ol><p>O salto acontece no final da origem, com a preparação da guia do destino. Salve o projeto para guardar o arranjo.</p>`;
  body.querySelector('[data-auto-enabled]').onchange=e=>{a.enabled=e.target.checked;if(!a.enabled&&this.pendingSection?.automationId){this.pendingSection=null;this.nativeSync();}this.refreshArrangementUi();};
  body.querySelector('[data-auto-mode]').onchange=e=>{this.setArrangementMode(e.target.value);};
  body.querySelector('[data-auto-add]').onclick=()=>{const from=this.sections[+body.querySelector('[data-auto-from]').value],to=this.sections[+body.querySelector('[data-auto-to]').value];if(from===to){body.querySelector('[data-auto-error]').textContent='Escolha um destino diferente da origem.';return;}a.rules.push({id:crypto.randomUUID(),from:regionRef(from),to:regionRef(to)});this.refreshArrangementUi();};body.querySelectorAll('[data-auto-remove]').forEach(b=>b.onclick=()=>{const i=+b.dataset.autoRemove;a.rules.splice(i,1);if(i<a.cursor)a.cursor--;this.refreshArrangementUi();});};
  this._renderArrangement=render;wrap.querySelector('[data-close]').onclick=()=>{this._renderArrangement=null;wrap.remove();};editorExit(wrap,this,session);render();
 };
 setTimeout(()=>setTimeout(()=>{const s=window.__ZLPLAY__;if(!s)return;for(const selector of ['.vs-head-actions','.vs-library-navigation']){const parent=s.$(selector);const button=document.createElement('button');button.className='vs-mode';button.dataset.arrangement='';button.onclick=()=>s.openArrangement();parent.append(button);const mode=document.createElement('button');mode.className='vs-mode';mode.dataset.arrangementMode='';mode.onclick=()=>{s.setArrangementMode(s.arrangement?.mode==='live'?'rehearsal':'live');};parent.append(mode);}s.arrangement||=new Arrangement();s.refreshArrangementUi();},0),0);
}
