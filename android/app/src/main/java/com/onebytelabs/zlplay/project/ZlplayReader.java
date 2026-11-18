package com.onebytelabs.zlplay.project;
import com.onebytelabs.zlplay.audio.ClipEq;
import org.json.*;
import java.text.Normalizer;
import java.util.*;

/** Reads the canonical desktop manifest; DSP not implemented here is explicitly detected. */
public final class ZlplayReader {
 private static double value(JSONObject o,String key,double fallback)throws ProjectSpec.Invalid{if(o==null||!o.has(key))return fallback;double n=o.optDouble(key,Double.NaN);if(!Double.isFinite(n))throw new ProjectSpec.Invalid();return n;}
 private static String normalized(String s){return Normalizer.normalize(s,Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT);}
 private static boolean guide(String name){return ProjectSpec.isGuideName(name);}
 private static boolean special(String name){return guide(name)||ProjectSpec.isClickName(name);}
 private static void readClipEffects(ProjectSpec.Clip clip,JSONObject effects)throws ProjectSpec.Invalid{
  if(effects==null||!effects.optBoolean("enabled"))return;
  clip.trimDb=Math.max(-24,Math.min(24,value(effects,"trim",0)));
  List<double[]> filters=new ArrayList<>();
  double hp=value(effects,"highpass",0),lp=value(effects,"lowpass",0);
  if(hp>0)filters.add(ClipEq.coefficients("highpass",hp,0,.707));
  JSONArray bands=effects.optJSONArray("bands");
  int[] frequencies={120,500,2500,8000};
  if(bands!=null)for(int i=0;i<Math.min(4,bands.length());i++){
   JSONObject band=bands.optJSONObject(i);
   double gain=value(band,"gain",0);
   if(band!=null&&gain!=0)filters.add(ClipEq.coefficients(band.optString("type",i==0?"low":i==3?"high":"peak"),
    value(band,"freq",frequencies[i]),gain,value(band,"q",.707)));
  }
  if(lp>0)filters.add(ClipEq.coefficients("lowpass",lp,0,.707));
  clip.eq=filters.isEmpty()?null:filters.toArray(new double[0][]);
 }
 public static ProjectSpec parse(String text)throws Exception{
  JSONObject root=new JSONObject(text);if(!root.optString("format").equals("tom-certo-project")||root.optInt("formatVersion",-1)!=1)throw new ProjectSpec.Invalid();
  ProjectSpec result=new ProjectSpec();JSONObject song=root.optJSONObject("song"),settings=root.optJSONObject("settings"),mix=root.optJSONObject("mixer");if(song==null)throw new ProjectSpec.Invalid();
  result.originalKey=settings==null?song.optString("key",""):settings.optString("originalKey",song.optString("key",""));result.targetKey=settings==null?result.originalKey:settings.optString("lastTonalKey",result.originalKey);
  int savedShift=(int)Math.round(value(settings,"liveTranspose",0));if(Math.abs(value(settings,"liveTranspose",0)-savedShift)>.00001||Math.abs(savedShift)>12)throw new ProjectSpec.Processing();if(savedShift!=0){int base=MusicalKey.parse(result.originalKey);if(base<0)throw new ProjectSpec.Processing();if(settings==null||!settings.has("lastTonalKey"))result.targetKey=MusicalKey.FLATS[(base+savedShift+24)%12];}
  result.title=song.optString("title","Project");result.bpm=value(song,"bpm",120);result.duration=value(song,"duration",0);JSONObject signature=song.optJSONObject("timeSignature");if(signature!=null){result.numerator=signature.optInt("numerator",4);result.denominator=signature.optInt("denominator",4);}
  if(Math.abs(value(settings,"executionFactor",1)-1)>.00001)throw new ProjectSpec.Processing();
  if(settings!=null){JSONObject click=settings.optJSONObject("click");if(click!=null){result.projectClick="project".equals(click.optString("source"));if(click.has("accent"))result.clickAccent=click.optBoolean("accent");if(Boolean.TRUE.equals(result.clickAccent)&&"last".equals(click.optString("accentPosition")))result.warnings.add("click-settings");}}
  JSONArray pads=root.optJSONArray("padMarkers");
  if(pads!=null)for(int i=0;i<pads.length();i++){
   JSONObject marker=pads.getJSONObject(i);int note=MusicalKey.parse(marker.optString("note"));
   result.padMarkers.add(new ProjectSpec.PadMarker(value(marker,"time",-1),note));
  }
  JSONArray tracks=root.optJSONArray("tracks");if(tracks==null||tracks.length()>32)throw new ProjectSpec.Invalid();Map<String,JSONObject> groups=new HashMap<>();JSONArray groupStates=mix==null?null:mix.optJSONArray("groupStates");
  if(groupStates!=null)for(int i=0;i<groupStates.length();i++){JSONArray row=groupStates.getJSONArray(i);groups.put(row.getString(0),row.getJSONObject(1));}
  for(int i=0;i<tracks.length();i++){
   JSONObject source=tracks.getJSONObject(i);ProjectSpec.Track track=new ProjectSpec.Track();track.pitchEnabled=source.has("pitchEnabled")&&!source.isNull("pitchEnabled")?source.optBoolean("pitchEnabled"):null;track.name=source.optString("name","Track "+(i+1));track.group=source.optString("group","VS");track.volume=value(source,"volume",1);track.pan=value(source,"pan",0);track.mute=source.optBoolean("mute");track.solo=source.optBoolean("solo");
   if(source.has("output")&&!source.isNull("output"))result.warnings.add("routing");JSONArray items=source.optJSONArray("items");if(items==null)throw new ProjectSpec.Invalid();
   for(int j=0;j<items.length();j++){JSONObject item=items.getJSONObject(j);if(Math.abs(value(item,"pitch",0))>.00001||Math.abs(value(item,"playbackRate",1)-1)>.00001)throw new ProjectSpec.Processing();ProjectSpec.Clip clip=new ProjectSpec.Clip();clip.file=item.optString("file","");clip.position=value(item,"position",0);clip.offset=value(item,"offset",0);clip.length=item.isNull("length")?-1:value(item,"length",-1);clip.muted=item.optBoolean("muted");clip.gain=value(item,"gain",1);clip.pan=value(item,"pan",0);readClipEffects(clip,item.optJSONObject("effects"));track.clips.add(clip);}
   result.tracks.add(track);
  }
  JSONObject channelEffects=settings==null?null:settings.optJSONObject("channelEffects");
  if(channelEffects!=null){
   String[] buses={"master","click","guide","pad","vs"};
   for(int i=0;i<buses.length;i++){
    ProjectSpec.Clip fx=new ProjectSpec.Clip();readClipEffects(fx,channelEffects.optJSONObject(buses[i]));
    result.busEq[i]=fx.eq;result.busTrim[i]=Math.pow(10,fx.trimDb/20);
   }
   for(ProjectSpec.Track track:result.tracks){
    List<double[]> chain=new ArrayList<>();double trim=0;
    for(String key:new String[]{"group:"+track.group,"track:"+track.name}){
     if(key.startsWith("group:")&&special(track.name))continue;
     ProjectSpec.Clip fx=new ProjectSpec.Clip();readClipEffects(fx,channelEffects.optJSONObject(key));
     if(fx.eq!=null)Collections.addAll(chain,fx.eq);trim+=fx.trimDb;
    }
    track.eq=chain.isEmpty()?null:chain.toArray(new double[0][]);track.eqTrim=Math.pow(10,trim/20);
   }
  }
  boolean solo=result.tracks.stream().anyMatch(t->t.solo),groupSolo=groups.values().stream().anyMatch(g->g.optBoolean("solo"));JSONObject normalization=settings==null?null:settings.optJSONObject("normalization");
  for(ProjectSpec.Track track:result.tracks){boolean special=special(track.name);JSONObject group=groups.get(track.group);boolean muted=track.mute;

   if(!special){muted|=(mix!=null&&mix.optBoolean("vsMute"))||(group!=null&&group.optBoolean("mute"));track.solo|=(mix!=null&&mix.optBoolean("vsSolo"))||(group!=null&&group.optBoolean("solo"));track.volume*=value(mix,"vsVolume",1)*value(group,"volume",1);track.pan=Math.max(-1,Math.min(1,track.pan+value(mix,"vsPan",0)+value(group,"pan",0)));}
   double db=guide(track.name)?value(normalization,"guide",0):special?0:value(normalization,"vs",0);track.volume*=Math.pow(10,db/20);track.mute=muted;
  }
  JSONArray regions=root.optJSONArray("regions");if(regions!=null)for(int i=0;i<regions.length();i++){JSONObject r=regions.getJSONObject(i);result.regions.add(new ProjectSpec.Region(r.optString("name",""),value(r,"start",0),value(r,"end",0),r.optString("color","")));}
  JSONObject arrangement=root.optJSONObject("arrangement");if(arrangement!=null){result.automationEnabled=arrangement.optBoolean("enabled",true);result.automationRehearsal=!"live".equals(arrangement.optString("mode"));JSONArray rules=arrangement.optJSONArray("rules");if(rules!=null)for(int i=0;i<rules.length();i++){JSONObject rule=rules.getJSONObject(i),from=rule.getJSONObject("from"),to=rule.getJSONObject("to");result.jumps.add(new ProjectSpec.Jump(value(from,"start",-1),value(from,"end",-1),value(to,"start",-1)));}}
  result.validate();return result;
 }
}
