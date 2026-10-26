package com.onebytelabs.zlplay.project;
import java.util.*;
import java.text.Normalizer;

/** Shared project model. Times are seconds at the project's original execution rate. */
public final class ProjectSpec {
 public String title="Project";public double bpm=120,duration;public int numerator=4,denominator=4;public boolean automationEnabled=true;
 public final List<Track> tracks=new ArrayList<>();public final List<Region> regions=new ArrayList<>();public final List<Jump> jumps=new ArrayList<>();public final Set<String> warnings=new LinkedHashSet<>();
 public static boolean isGuideName(String name){String n=Normalizer.normalize(name,Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT);return n.matches(".*(?:^|[^a-z])(?:guia|guide|cue|cues)(?:[^a-z]|$).*");}
 public static boolean isClickName(String name){String n=Normalizer.normalize(name,Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT);return n.matches(".*(?:click|clique|claqueta|metronom).*");}
 public static final class Track {public int role;public boolean isGuide(){return role==1||role==0&&isGuideName(name);}public boolean isClick(){return role==2||role==0&&isClickName(name);}public String name="Track",group="VS";public double volume=1,pan;public boolean mute,solo;public final List<Clip> clips=new ArrayList<>();}
 public static final class Clip {public boolean nativeClick;public String file="";public double position,offset,length=-1,gain=1,pan;}
 public static final class Region {public final String name,color;public final double start,end;public Region(String n,double s,double e,String c){name=n;start=s;end=e;color=c==null?"":c;}}
 public static final class Jump {public final double fromStart,fromEnd,target;public Jump(double s,double e,double t){fromStart=s;fromEnd=e;target=t;}}
 public static final class Invalid extends Exception {public Invalid(){super("Invalid project");}}
 public static final class Processing extends Exception {public Processing(){super("Render unsupported processing to audio first");}}
 public static double number(String value)throws Invalid{try{double n=Double.parseDouble(value);if(!Double.isFinite(n))throw new Invalid();return n;}catch(NumberFormatException bad){throw new Invalid();}}
 public void validate()throws Invalid{
  if(!Double.isFinite(duration)||duration<0||duration>86400||!Double.isFinite(bpm)||bpm<20||bpm>400||tracks.isEmpty()||tracks.size()>32)throw new Invalid();
  int clips=0;for(Track t:tracks){if(!Double.isFinite(t.volume)||t.volume<0||t.volume>8||!Double.isFinite(t.pan)||Math.abs(t.pan)>1)throw new Invalid();for(Clip c:t.clips){clips++;if(c.file.trim().isEmpty()||!Double.isFinite(c.position)||c.position<0||!Double.isFinite(c.offset)||c.offset<0||!Double.isFinite(c.length)||(c.length!=-1&&c.length<=0)||!Double.isFinite(c.gain)||c.gain<0||c.gain>8||!Double.isFinite(c.pan)||Math.abs(c.pan)>1)throw new Invalid();}}
  if(numerator<1||numerator>32||denominator<1||denominator>32)throw new Invalid();
  if(clips==0||clips>64||regions.size()>512||jumps.size()>512)throw new Invalid();regions.sort(Comparator.comparingDouble(r->r.start));double last=-1;
  for(Region r:regions){if(!Double.isFinite(r.start)||!Double.isFinite(r.end)||r.start<0||r.end<=r.start||r.start<last-.0001)throw new Invalid();last=r.end;duration=Math.max(duration,r.end);}
  if(duration>86400)throw new Invalid();
  for(Jump j:jumps){boolean from=false,to=false;for(Region r:regions){from|=Math.abs(r.start-j.fromStart)<.01&&Math.abs(r.end-j.fromEnd)<.01;to|=Math.abs(r.start-j.target)<.01;}if(!from||!to)throw new Invalid();}
 }
}
