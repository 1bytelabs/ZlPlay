import {playbackScroll,regionBoundaries} from './timeline-model.mjs';

const manualBrowseDelay = 5000;

function installManualBrowse(studio) {
 const host = studio.host;
 let gesture = null;
 let resumeAt = 0;
 let suppressClick = false;

 function browseable(target) {
  if (!studio.playing || studio._regionEditing || studio._trackEditing || studio.mappingRegions || studio.controlMapMode || studio._timelineCursorDragging) return null;
  const scroll = target.closest('.vs-timeline-scroll');
  return scroll && scroll.scrollWidth > scroll.clientWidth ? scroll : null;
 }

 host.addEventListener('pointerdown', event => {
  suppressClick = false;
  if (!event.isPrimary || event.button !== 0 || !event.target.closest('.vs-timeline,.vs-segment') || event.target.closest('.vs-timeline-playhead')) return;
  const scroll = browseable(event.target);
  if (scroll) gesture = {scroll, id: event.pointerId, x: event.clientX, left: scroll.scrollLeft, dragging: false};
 });

 document.addEventListener('pointermove', event => {
  if (!gesture || event.pointerId !== gesture.id) return;
  const delta = event.clientX - gesture.x;
  if (!gesture.dragging) {
   if (Math.abs(delta) < 5) return;
   gesture.dragging = true;
   gesture.scroll.setPointerCapture(event.pointerId);
   gesture.scroll.classList.add('zl-timeline-panning');
  }
  event.preventDefault();
  gesture.scroll.scrollLeft = gesture.left - delta;
 });

 function finish(event) {
  if (!gesture || (event.pointerId !== undefined && event.pointerId !== gesture.id)) return;
  if (gesture.dragging) {
   resumeAt = performance.now() + manualBrowseDelay;
   suppressClick = event.type === 'pointerup';
   gesture.scroll.classList.remove('zl-timeline-panning');
  }
  gesture = null;
 }

 for (const type of ['pointerup', 'pointercancel', 'lostpointercapture']) document.addEventListener(type, finish);
 window.addEventListener('blur', finish);
 host.addEventListener('click', event => {
  if (!suppressClick || event.detail === 0 || !event.target.closest('.vs-timeline-scroll')) return;
  suppressClick = false;
  event.preventDefault();
  event.stopImmediatePropagation();
 }, true);
 host.addEventListener('wheel', event => {
  if (browseable(event.target) && !event.target.closest('input,select,button')) resumeAt = performance.now() + manualBrowseDelay;
 }, {capture: true, passive: true});

 return () => !gesture && performance.now() >= resumeAt;
}

export function installTimelineNavigation(s){
 const canFollow = window.zlplayNative?.platform === 'win32' ? installManualBrowse(s) : () => true;
 const draw=s.drawSections.bind(s);
 s.drawSections=function(...args){const result=draw(...args),timeline=this.$('.vs-timeline');
  if(timeline&&this.duration>0){const layer=document.createElement('div');layer.className='zl-region-boundaries';layer.setAttribute('aria-hidden','true');
   for(const time of regionBoundaries(this.sections,this.duration)){const line=document.createElement('i');line.dataset.boundary=String(time);line.style.left=time/this.duration*100+'%';layer.append(line);}timeline.append(layer);
  }return result;
 };
 const paint=s.paint.bind(s);
 s.paint=function(position,...args){const result=paint(position,...args),scroll=this.$('.vs-timeline-scroll'),timeline=this.$('.vs-timeline');
  if(timeline){const selected=this._regionEditing?timeline.querySelector('.zl-region-selection'):null;
   const active=selected?{start:parseFloat(selected.style.left)/100*this.duration,end:(parseFloat(selected.style.left)+parseFloat(selected.style.width))/100*this.duration}:this.sections.find(r=>position>=r.start&&position<r.end);
   for(const line of timeline.querySelectorAll('[data-boundary]')){const time=Number(line.dataset.boundary);line.classList.toggle('selected-edge',!!active&&(Math.abs(time-active.start)<.001||Math.abs(time-active.end)<.001));}
   if(this.playing&&!this._regionEditing&&!this._trackEditing&&!this._timelineCursorDragging&&scroll&&canFollow()){scroll.scrollLeft=playbackScroll(position,this.duration,timeline.clientWidth,scroll.clientWidth,scroll.scrollLeft);}
  }return result;
 };
 s.drawSections();
}
