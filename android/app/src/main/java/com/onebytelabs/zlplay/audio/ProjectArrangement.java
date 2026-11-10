package com.onebytelabs.zlplay.audio;

import com.onebytelabs.zlplay.project.ProjectSpec;
import java.util.Arrays;

/** Ordered jump sequence; execution state never mutates the saved project rules. */
public final class ProjectArrangement {
 private final ProjectSpec.Jump[] rules;
 private final boolean[] consumed;
 private boolean enabled,rehearsal,armed;
 private int cursor;
 private volatile int executed;

 public ProjectArrangement(ProjectSpec spec){
  rules=spec.jumps.toArray(new ProjectSpec.Jump[0]);
  consumed=new boolean[rules.length];
  enabled=spec.automationEnabled;
  rehearsal=spec.automationRehearsal;
 }
 public boolean isEnabled(){return enabled;}
 public boolean isRehearsal(){return rehearsal;}
 public int executedCount(){return executed;}
 public void setEnabled(boolean enabled){this.enabled=enabled;armed=false;}
 public void setRehearsal(boolean rehearsal){
  if(this.rehearsal!=rehearsal){this.rehearsal=rehearsal;reset();}
 }
 public void restart(){reset();}
 public void reset(){Arrays.fill(consumed,false);executed=0;seek(0);}
 public void seek(long frame){cursor=0;armed=false;advancePast(frame);}
 private void advancePast(long frame){
  while(cursor<rules.length&&(consumed[cursor]||frame>=Math.round(rules[cursor].fromEnd*DemoMixer.RATE)))cursor++;
 }
 public long previewBoundary(long frame){
  if(!enabled||cursor>=rules.length)return -1;
  ProjectSpec.Jump rule=rules[cursor];
  long end=Math.round(rule.fromEnd*DemoMixer.RATE);
  return frame>=Math.round(rule.fromStart*DemoMixer.RATE)&&frame<end?end:-1;
 }
 public long hint(){return enabled&&cursor<rules.length?Math.round(rules[cursor].target*DemoMixer.RATE):-1;}
 public long armedTarget(){return armed?hint():-1;}
 public long target(long frame,boolean blocked){
  if(blocked||!enabled){armed=false;return -1;}
  if(!armed)advancePast(frame);
  if(cursor>=rules.length)return -1;
  ProjectSpec.Jump rule=rules[cursor];
  long start=Math.round(rule.fromStart*DemoMixer.RATE),end=Math.round(rule.fromEnd*DemoMixer.RATE);
  if(frame>=start&&frame<end)armed=true;
  if(!armed||frame<end)return -1;
  armed=false;
  if(!rehearsal){consumed[cursor]=true;executed++;}
  cursor++;
  long target=Math.round(rule.target*DemoMixer.RATE);
  advancePast(target);
  return target;
 }
}
