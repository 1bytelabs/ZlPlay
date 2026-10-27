/** Shared region state badges. Playback remains entirely owned by the player. */
export function installRegionFeedback(player,language){
 const style=document.createElement('style');
 style.textContent=`.zl-region-state{position:absolute!important;right:4px;bottom:2px;display:inline-flex!important;align-items:center;gap:3px;padding:1px 4px;border-radius:4px;background:#08121ee8;font-style:normal;font-size:10px;line-height:13px;pointer-events:none;z-index:3;color:#b7f7c5;border:1px solid #6ddb8955}.zl-region-state[data-kind="jump"]{color:#e4c5ff;border-color:#bf89f766}.zl-region-state svg{width:13px;height:13px;flex:none}.zl-region-state em{font-style:normal;font-weight:650;letter-spacing:.01em}@container(max-width:85px){.zl-region-state em{display:none}.zl-region-state{padding:1px 2px;right:2px}}`;
 document.head.append(style);
 let previous='';
 function update(force=false){
  const loop=player.currentLoop&&player.currentLoopSection;
  const loopIndex=loop?player.sections.findIndex(section=>Math.abs(section.start-loop.start)<.001&&Math.abs(section.end-loop.end)<.001):-1;
  const pending=player.pendingSection?.index??-1,lang=language();
  const signature=[loopIndex,pending,lang].join('|');
  if(!force&&signature===previous)return;
  previous=signature;
  for(const [index,card]of [...player.host.querySelectorAll('.vs-segment')].entries()){
   const kind=index===pending?'jump':index===loopIndex?'loop':'';
   let badge=card.querySelector('[data-region-state]');
   if(!kind){badge?.remove();continue;}
   if(!badge){badge=document.createElement('i');badge.className='zl-region-state';badge.dataset.regionState='';card.style.containerType='inline-size';card.append(badge);}
   const label=kind==='loop'?'Loop':lang==='en'?'Jump':'Salto';
   badge.dataset.kind=kind;badge.setAttribute('aria-label',label);badge.title=label;badge.replaceChildren();
   const svg=document.createElementNS('http://www.w3.org/2000/svg','svg');svg.setAttribute('viewBox','0 0 24 24');svg.setAttribute('aria-hidden','true');
   const path=document.createElementNS(svg.namespaceURI,'path');path.setAttribute('d',kind==='loop'?'M20 7h-4m4 0-3-3M20 7A8 8 0 0 0 6 5M4 17h4m-4 0 3 3M4 17a8 8 0 0 0 14 2':'M4 19v-8a4 4 0 0 1 4-4h12m-5-5 5 5-5 5');path.setAttribute('fill','none');path.setAttribute('stroke','currentColor');path.setAttribute('stroke-width','2');path.setAttribute('stroke-linecap','round');path.setAttribute('stroke-linejoin','round');svg.append(path);
   const text=document.createElement('em');text.textContent=label;badge.append(svg,text);
  }
 }
 for(const name of ['updateRegionLoopFeedback','selectSection','paint','drawSections','stop','goToStart','pause']){
  const original=player[name];if(typeof original!=='function')continue;
  player[name]=function(...args){const value=original.apply(this,args);update(name==='drawSections');return value;};
 }
 document.addEventListener('zl-language',()=>update(true));
 update(true);
}
