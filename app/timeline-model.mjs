// Page the main timeline only when playback leaves its comfortable visible range.
export function playbackScroll(position,duration,width,viewport,left){
 if(!(duration>0&&viewport>0&&width>viewport))return left;
 const x=Math.max(0,Math.min(duration,position))/duration*width,margin=Math.min(60,viewport*.12);
 if(x>=left+margin&&x<=left+viewport-margin)return left;
 return Math.max(0,Math.min(width-viewport,x-viewport*.2));
}
export function regionResizePlan(regions,region,start,end,coupled=true){
 if(!(start>=0&&end>start))return null;
 const others=regions.filter(r=>r!==region),previous=others.find(r=>Math.abs(r.end-region.start)<.001),next=others.find(r=>Math.abs(r.start-region.end)<.001);
 if(coupled){
  if((previous&&start<=previous.start)||(next&&end>=next.end))return null;
 }else if(others.some(r=>r.start<end-.0001&&r.end>start+.0001))return null;
 const changes=[{region,start,end}];
 if(coupled&&previous)changes.push({region:previous,start:previous.start,end:start});
 if(coupled&&next)changes.push({region:next,start:end,end:next.end});
 return changes;
}
export function regionBoundaries(regions,duration){
 if(!(duration>0))return [];
 return [...new Set(regions.flatMap(r=>[r.start,r.end]).filter(t=>Number.isFinite(t)&&t>=0&&t<=duration))].sort((a,b)=>a-b);
}
