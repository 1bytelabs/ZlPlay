export const regionRef=s=>({name:String(s.name),start:Number(s.start),end:Number(s.end)});
export const findRegion=(sections,ref)=>sections.findIndex(s=>Math.abs(s.start-ref?.start)<.01&&Math.abs(s.end-ref?.end)<.01);
export class Arrangement {
  constructor(data={}){this.rules=(data.rules||[]).filter(r=>r?.from&&r?.to).map((r,i)=>({id:String(r.id||`rule-${i}`),from:regionRef(r.from),to:regionRef(r.to)}));this.mode=data.mode==='rehearsal'?'rehearsal':'live';this.enabled=data.enabled!==false;this.cursor=0;this.run=0;this.executed=0;this.completed=[];}
  get next(){return this.rules[this.cursor]||null;}
  restart(){this.cursor=0;this.run++;this.executed=0;this.completed=[];}
  seek(position){if(this.mode==='rehearsal')this.restart();else{this.cursor=0;this.run++;}this.advancePast(position);}
  setMode(mode,position){if(this.mode===mode)return;this.mode=mode;this.restart();this.advancePast(position);}
  advancePast(position){while(this.next&&(this.next.from.end<=position||(this.mode==='live'&&this.completed.includes(this.next.id))))this.cursor++;}
  complete(id){if(this.next?.id!==id)return false;this.executed++;this.completed.push(id);this.cursor++;this.advancePast(-Infinity);return true;}
  toJSON(){return {version:1,enabled:this.enabled,mode:this.mode,rules:this.rules.map(rule=>({id:rule.id,from:regionRef(rule.from),to:regionRef(rule.to)}))};}
}
