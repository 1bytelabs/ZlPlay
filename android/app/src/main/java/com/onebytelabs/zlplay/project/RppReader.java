package com.onebytelabs.zlplay.project;
import java.util.*;
import java.util.regex.*;

/** Structural REAPER parser: audio references only; plugin data is never executed. */
public final class RppReader {
 private static List<String> tokens(String line){List<String> out=new ArrayList<>();int i=0;while(i<line.length()){while(i<line.length()&&Character.isWhitespace(line.charAt(i)))i++;if(i>=line.length())break;StringBuilder value=new StringBuilder();if(line.charAt(i)=='"'){i++;while(i<line.length()){char c=line.charAt(i++);if(c=='"')break;if(c=='\\'&&i<line.length()&&line.charAt(i)=='"'){value.append('"');i++;}else value.append(c);}}else while(i<line.length()&&!Character.isWhitespace(line.charAt(i)))value.append(line.charAt(i++));out.add(value.toString());}return out;}
 private static double n(List<String> p,int i,double fallback)throws ProjectSpec.Invalid{return i<p.size()?ProjectSpec.number(p.get(i)):fallback;}
 private static final class Node {final ProjectSpec.Track track=new ProjectSpec.Track();int delta;}
 private static final class Mark {String name,color;double time;Mark(String n,double t,String c){name=n;time=t;color=c;}}
 public static ProjectSpec parse(String source,String title)throws Exception{
  ProjectSpec result=new ProjectSpec();result.warnings.add("rpp-rendering");result.title=title;Deque<String> stack=new ArrayDeque<>();List<Node> all=new ArrayList<>();Node node=null;ProjectSpec.Clip item=null;Map<String,Mark> open=new HashMap<>();List<Mark> markers=new ArrayList<>();boolean root=false;
  for(String raw:source.replace("\uFEFF","").split("\r?\n")){
   String line=raw.trim();if(line.isEmpty())continue;
   if(line.startsWith("<")){List<String> p=tokens(line.substring(1));if(p.isEmpty())throw new ProjectSpec.Invalid();String type=p.get(0);stack.push(type);
    if(type.equals("REAPER_PROJECT"))root=true;
    else if(type.equals("TRACK")){node=new Node();all.add(node);}
    else if(type.equals("ITEM")&&node!=null){item=new ProjectSpec.Clip();node.track.clips.add(item);}
    else if(type.equals("SOURCE")&&item!=null){if(p.size()>1&&p.get(1).equals("CLICK")){node.track.role=2;item.file="@click:"+node.track.name;}else if(p.size()<2||!p.get(1).equals("WAVE")&&!p.get(1).equals("MP3"))throw new ProjectSpec.Processing();}
    else if(Arrays.asList("VST","JS","AU","CLAP","DX").contains(type))result.warnings.add("effects");
    continue;
   }
   if(line.equals(">")){if(stack.isEmpty())throw new ProjectSpec.Invalid();String type=stack.pop();if(type.equals("ITEM"))item=null;if(type.equals("TRACK")){node=null;item=null;}continue;}
   List<String> p=tokens(line);if(p.isEmpty())continue;String key=p.get(0),parent=stack.peek();
   if("REAPER_PROJECT".equals(parent)){
    if(key.equals("TEMPO")){result.bpm=n(p,1,120);result.numerator=(int)n(p,2,4);result.denominator=(int)n(p,3,4);}
    if(key.equals("MARKER")&&p.size()>=4){String id=p.get(1),name=p.get(3);double time=n(p,2,0);long nativeColor=(long)n(p,5,0);String color=(nativeColor&0x1000000)!=0?String.format(Locale.ROOT,"#%02x%02x%02x",nativeColor&255,(nativeColor>>8)&255,(nativeColor>>16)&255):"";
     if(p.contains("R")&&!name.isEmpty()){Mark old=open.put(id,new Mark(name,time,color));if(old!=null&&time>old.time)result.regions.add(new ProjectSpec.Region(old.name,old.time,time,old.color));}
     else if(open.containsKey(id)&&name.isEmpty()){Mark old=open.remove(id);result.regions.add(new ProjectSpec.Region(old.name,old.time,time,old.color));}
     else if(!name.isEmpty())markers.add(new Mark(name,time,color));
    }
   }
   if("TEMPOENVEX".equals(parent)&&key.equals("PT"))result.warnings.add("tempo-map");
   if(node==null)continue;
   if("TRACK".equals(parent)){
    if(key.equals("NAME")&&p.size()>1)node.track.name=p.get(1);
    if(key.equals("VOLPAN")){node.track.volume=n(p,1,1);node.track.pan=n(p,2,0);}
    if(key.equals("MUTESOLO")){node.track.mute=n(p,1,0)!=0;node.track.solo=n(p,2,0)!=0;}
    if(key.equals("ISBUS"))node.delta=(int)n(p,2,0);
   }
   if(item!=null){
    if("SOURCE".equals(parent)&&key.equals("FILE")&&p.size()>1)item.file=p.get(1);
    if("ITEM".equals(parent)){
     if(key.equals("POSITION"))item.position=n(p,1,0);if(key.equals("SOFFS"))item.offset=n(p,1,0);if(key.equals("LENGTH"))item.length=n(p,1,-1);
     if(key.equals("VOLPAN")){item.gain=n(p,1,1);item.pan=n(p,2,0);}
     if(key.equals("SM"))throw new ProjectSpec.Processing();
     if(key.equals("PLAYRATE")&&(Math.abs(n(p,1,1)-1)>.00001||Math.abs(n(p,3,0))>.00001))throw new ProjectSpec.Processing();
     if((key.equals("FADEIN")||key.equals("FADEOUT"))&&Math.abs(n(p,2,0))>.00001)result.warnings.add("fades");
    }
   }
  }
  if(!root||!stack.isEmpty()||!open.isEmpty())throw new ProjectSpec.Invalid();
  Deque<Node> groups=new ArrayDeque<>();for(Node track:all){
   if(!groups.isEmpty()){Node parent=groups.peek();track.track.volume*=parent.track.volume;track.track.pan=Math.max(-1,Math.min(1,track.track.pan+parent.track.pan));track.track.mute|=parent.track.mute;track.track.solo|=parent.track.solo;}
   if(!groups.isEmpty())track.track.group=groups.peek().track.name;
   if(!track.track.clips.isEmpty())result.tracks.add(track.track);
   if(track.delta>0)groups.push(track);else for(int i=0;i<Math.abs(track.delta)&&!groups.isEmpty();i++)groups.pop();
  }
  for(ProjectSpec.Track track:result.tracks)for(ProjectSpec.Clip clip:track.clips)if(clip.length>0)result.duration=Math.max(result.duration,clip.position+clip.length);
  if(result.regions.isEmpty()){markers.sort(Comparator.comparingDouble(m->m.time));for(int i=0;i<markers.size();i++){Mark m=markers.get(i);double end=i+1<markers.size()?markers.get(i+1).time:result.duration;if(end>m.time)result.regions.add(new ProjectSpec.Region(m.name,m.time,end,m.color));}}
  result.validate();return result;
 }
}
