(function(root){
class RemoteClock {
 constructor(){this.position=0;this.at=0;this.running=false;this.duration=0;this.signature='';this.lastReported=0;this.lastSample=0;this.pending=-1;}
 value(now){return Math.max(0,Math.min(this.duration,this.position+(this.running?Math.max(0,now-this.at)/1000:0)));}
 update(state,now){
  const position=Number(state.position)||0,duration=Number(state.duration)||0;
  const target=Math.min(duration,position+(state.playing?Math.min(.5,(Number(state.ageMs)||0)/1000+(Number(state.roundTripMs)||0)/2000):0));
  const signature=[state.projectId,state.playing,state.bpm,duration].join('|'),predicted=this.value(now),error=target-predicted;
  const discontinuity=position<this.lastReported-.08||(this.lastSample&&state.sampledAt&&Math.abs((position-this.lastReported)-(state.sampledAt-this.lastSample)/1000)>.2)||(this.pending>=0&&state.pending===-1);
  this.position=discontinuity||signature!==this.signature||!state.playing||Math.abs(error)>.75?target:predicted+Math.max(-.025,Math.min(.025,error*.12));
  this.at=now;this.duration=duration;this.running=!!state.playing;this.signature=signature;this.lastReported=position;this.lastSample=Number(state.sampledAt)||0;this.pending=state.pending??-1;
 }
 freeze(now){this.position=this.value(now);this.at=now;this.running=false;}
}
root.RemoteClock=RemoteClock;if(typeof module!=='undefined')module.exports={RemoteClock};
})(globalThis);
