const time=n=>`${Math.floor(Math.max(0,n)/60)}:${String(Math.floor(Math.max(0,n)%60)).padStart(2,'0')}`;
export function installInterfacePolish(Player){
 const p=Player.prototype,oldGrid=p.updateTimelineGrid,oldDraw=p.drawSections,oldPaint=p.paint;
 p.updateTimelineGrid=function(...args){const result=oldGrid.apply(this,args),ruler=this.$('.vs-timeline-ruler'),width=this.$('.vs-timeline-content')?.clientWidth||0;
  if(ruler){const signature=`${Math.round(width)}:${this.duration}:${this.tempo.bpm}`;if(ruler.dataset.sparseGrid!==signature){ruler.dataset.sparseGrid=signature;const ticks=[...ruler.children];let right=-Infinity;for(const tick of ticks){const left=parseFloat(tick.style.left)/100*width,labelWidth=Math.max(24,tick.textContent.length*8);const show=left>=right+28;tick.hidden=!show;if(show)right=left+labelWidth;}}}return result;
 };
 p.drawSections=function(...args){const result=oldDraw.apply(this,args),area=this.$('.vs-timeline-scroll');if(area&&!area.querySelector('.zl-timeline-clock')){const clock=document.createElement('div');clock.className='zl-timeline-clock';clock.innerHTML='<span data-elapsed>0:00</span><span data-remaining></span>';area.append(clock);}return result;};
 p.paint=function(position,...args){const result=oldPaint.call(this,position,...args),clock=this.$('.zl-timeline-clock');if(clock){const elapsed=time(Math.min(this.duration,position||0)),remaining='Restam '+time(Math.max(0,this.duration-(position||0)));if(clock.firstChild.textContent!==elapsed)clock.firstChild.textContent=elapsed;if(clock.lastChild.textContent!==remaining)clock.lastChild.textContent=remaining;}return result;};
}
