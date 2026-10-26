import {playbackScroll,regionBoundaries} from './timeline-model.mjs';
export function installTimelineNavigation(s){
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
   if(this.playing&&!this._regionEditing&&!this._trackEditing&&!this._timelineCursorDragging&&scroll){scroll.scrollLeft=playbackScroll(position,this.duration,timeline.clientWidth,scroll.clientWidth,scroll.scrollLeft);}
  }return result;
 };
 s.drawSections();
}
