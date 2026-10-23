(function(){
  function setup(){const host=document.querySelector('#zosma-vs-study');if(!host)return;
  const actions=host.querySelector('.vs-head-actions');
  if(actions){
    let fs=actions.querySelector('[data-mobile-fullscreen]');
    if(!fs){fs=document.createElement('button');fs.type='button';fs.className='vs-mode';fs.dataset.mobileFullscreen='';fs.textContent='Tela cheia';fs.onclick=()=>document.fullscreenElement?document.exitFullscreen():host.requestFullscreen?.()}
    if(!actions.dataset.zlControlsOrdered){const map=actions.querySelector('[data-control-map]'),mapActions=actions.querySelector('[data-map-actions]'),mode=actions.querySelector('[data-mode]'),beta=actions.querySelector('.vs-beta');if(map)actions.append(map);if(mapActions)actions.append(mapActions);actions.append(fs);if(mode)actions.append(mode);if(beta)actions.append(beta);actions.dataset.zlControlsOrdered='1'}
  }
  }
  setup();new MutationObserver(setup).observe(document.body,{childList:true,subtree:true});
})();
