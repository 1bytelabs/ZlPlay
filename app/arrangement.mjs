export const regionRef=s=>({name:String(s.name),start:Number(s.start),end:Number(s.end)});
export const findRegion=(sections,ref)=>sections.findIndex(s=>Math.abs(s.start-ref?.start)<.01&&Math.abs(s.end-ref?.end)<.01);
export class Arrangement {
  constructor(data={}){this.rules=(data.rules||[]).filter(r=>r?.from&&r?.to).map((r,i)=>({id:String(r.id||`rule-${i}`),from:regionRef(r.from),to:regionRef(r.to)}));this.mode=data.mode==='rehearsal'?'rehearsal':'live';this.enabled=data.enabled!==false;this.cursor=0;this.run=0;this.executed=0;}
  get next(){return this.rules[this.cursor]||null;}
  restart(){this.cursor=0;this.run++;this.executed=0;}
  complete(id){if(this.next?.id!==id)return false;this.executed++;if(this.mode==='live')this.rules.splice(this.cursor,1);else this.cursor++;return true;}
  toJSON(){return {version:1,enabled:this.enabled,mode:this.mode,rules:this.rules};}
}
