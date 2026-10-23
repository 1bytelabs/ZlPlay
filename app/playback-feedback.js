import {findRegion} from './arrangement.mjs';
export const clockTime=value=>{const n=Math.max(0,Math.floor(Number(value)||0));return `${Math.floor(n/60)}:${String(n%60).padStart(2,'0')}`;};
export function arrangementLabel(player){const a=player.arrangement;if(!a)return {text:'',state:'idle'};const r=a.next;if(!a.enabled&&a.rules.length)return {text:'Arranjo desativado',state:'disabled'};if(!r)return {text:a.executed?'Arranjo concluído':'',state:'done'};
 const from=findRegion(player.sections,r.from),to=findRegion(player.sections,r.to);if(from<0||to<0)return {text:`Próxima automação: ${r.from.name} → ${r.to.name} · Região ausente`,state:'invalid'};
 const armed=player.pendingSection?.automationId===r.id;return {text:`${armed?'Salto armado':'Próxima automação'}: ${player.sections[from].name} → ${player.sections[to].name}`,state:armed?'armed':'next'};
}
export function installPlaybackFeedback(Player){const p=Player.prototype,old=Object.fromEntries(Object.getOwnPropertyNames(p).filter(k=>typeof p[k]==='function').map(k=>[k,p[k]]));
 p.updatePlaybackFeedback=function(position=this.offset||0){
  const head=this.$('.vs-regions-head');if(!head)return;let label=head.querySelector('[data-arrangement-indicator]');if(!label){label=document.createElement('span');label.dataset.arrangementIndicator='';label.className='zl-arrangement-indicator';head.append(label);}const info=arrangementLabel(this);label.hidden=!info.text;if(label.textContent!==info.text){label.textContent=info.text;label.title=info.text;}label.dataset.state=info.state;
 };
 p.paint=function(position,...args){const result=old.paint.call(this,position,...args);this.updatePlaybackFeedback(position);return result;};
 p.drawSections=function(...args){const result=old.drawSections.apply(this,args);this.updatePlaybackFeedback(this.position());return result;};
 p.refreshArrangementUi=function(...args){const result=old.refreshArrangementUi.apply(this,args);this.updatePlaybackFeedback(this.position());return result;};
 setTimeout(()=>setTimeout(()=>{const s=window.__ZLPLAY__;if(!s)return;s.host.querySelectorAll('.vs-beta').forEach(el=>el.remove());const support=s.host.querySelector('.vs-head-main a');if(support){support.href='https://livepix.gg/1bytelabs';support.textContent='♡ Apoiar projeto';support.title='Apoiar o ZLPlay pelo LivePix';support.setAttribute('aria-label',support.title);support.classList.add('zl-support-project');}
 document.addEventListener('pointerdown',e=>{for(const menu of s.host.querySelectorAll('details.vs-channel-menu[open]'))if(!menu.contains(e.target))menu.open=false;},true);
 document.addEventListener('keydown',e=>{if(e.key==='Escape')s.host.querySelectorAll('details.vs-channel-menu[open]').forEach(menu=>menu.open=false);});
 },0),0);
}
