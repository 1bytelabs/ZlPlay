package com.onebytelabs.zlplay.audio;
import com.onebytelabs.zlplay.project.ProjectSpec;

/** Ordered jump sequence; execution state never mutates the saved project rules. */
public final class ProjectArrangement {
 private final ProjectSpec.Jump[] rules;private final boolean enabled;private int cursor;private boolean armed;private long suppressStart=-1,suppressEnd;
 public ProjectArrangement(ProjectSpec spec){rules=spec.jumps.toArray(new ProjectSpec.Jump[0]);enabled=spec.automationEnabled;}
 public void reset(){cursor=0;armed=false;suppressStart=-1;}
 public void manualSeek(ProjectSpec.Region region){armed=false;suppressStart=region==null?-1:Math.round(region.start*DemoMixer.RATE);suppressEnd=region==null?0:Math.round(region.end*DemoMixer.RATE);}
 public long previewBoundary(long frame){if(!enabled||cursor>=rules.length||suppressStart>=0&&frame>=suppressStart&&frame<suppressEnd)return -1;ProjectSpec.Jump r=rules[cursor];return frame>=Math.round(r.fromStart*DemoMixer.RATE)&&frame<Math.round(r.fromEnd*DemoMixer.RATE)?Math.round(r.fromEnd*DemoMixer.RATE):-1;}
 public long hint(){return enabled&&cursor<rules.length?Math.round(rules[cursor].target*DemoMixer.RATE):-1;}
 public long target(long frame,boolean blocked){
  if(blocked){armed=false;return -1;}if(suppressStart>=0){if(frame>=suppressStart&&frame<suppressEnd)return -1;suppressStart=-1;}
  if(!enabled||cursor>=rules.length)return -1;ProjectSpec.Jump rule=rules[cursor];long start=Math.round(rule.fromStart*DemoMixer.RATE),end=Math.round(rule.fromEnd*DemoMixer.RATE);
  if(frame>=start&&frame<end)armed=true;if(armed&&frame>=end){armed=false;cursor++;return Math.round(rule.target*DemoMixer.RATE);}return -1;
 }
}
