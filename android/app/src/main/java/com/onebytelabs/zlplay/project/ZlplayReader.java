package com.onebytelabs.zlplay.project;
import org.json.*;
import java.text.Normalizer;
import java.util.*;

/** Reads the canonical desktop manifest; DSP not implemented here is explicitly detected. */
public final class ZlplayReader {
 private static double value(JSONObject o,String key,double fallback)throws ProjectSpec.Invalid{if(o==null||!o.has(key))return fallback;double n=o.optDouble(key,Double.NaN);if(!Double.isFinite(n))throw new ProjectSpec.Invalid();return n;}
 private static String normalized(String s){return Normalizer.normalize(s,Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT);}
 private static boolean guide(String name){return ProjectSpec.isGuideName(name);}
 private static boolean special(String name){return guide(name)||ProjectSpec.isClickName(name);}
 public static ProjectSpec parse(String text)throws Exception{
  JSONObject root=new JSONObject(text);if(!root.optString("format").equals("tom-certo-project")||root.optInt("formatVersion",-1)!=1)throw new ProjectSpec.Invalid();
  ProjectSpec result=new ProjectSpec();JSONObject song=root.optJSONObject("song"),settings=root.optJSONObject("settings"),mix=root.optJSONObject("mixer");if(song==null)throw new ProjectSpec.Invalid();
  result.title=song.optString("title","Project");result.bpm=value(song,"bpm",120);result.duration=value(song,"duration",0);JSONObject signature=song.optJSONObject("timeSignature");if(signature!=null){result.numerator=signature.optInt("numerator",4);result.denominator=signature.optInt("denominator",4);}
  if(Math.abs(value(settings,"liveTranspose",0))>.00001||Math.abs(value(settings,"executionFactor",1)-1)>.00001)throw new ProjectSpec.Processing();
  if(settings!=null){JSONObject fx=settings.optJSONObject("channelEffects");if(fx!=null&&fx.length()>0)result.warnings.add("effects");if(settings.optJSONObject("click")!=null)result.warnings.add("click-settings");}
  JSONArray pads=root.optJSONArray("padMarkers");if(pads!=null&&pads.length()>0)result.warnings.add("pad-markers");
  JSONArray tracks=root.optJSONArray("tracks");if(tracks==null||tracks.length()>32)throw new ProjectSpec.Invalid();Map<String,JSONObject> groups=new HashMap<>();JSONArray groupStates=mix==null?null:mix.optJSONArray("groupStates");
  if(groupStates!=null)for(int i=0;i<groupStates.length();i++){JSONArray row=groupStates.getJSONArray(i);groups.put(row.getString(0),row.getJSONObject(1));}
  for(int i=0;i<tracks.length();i++){
   JSONObject source=tracks.getJSONObject(i);ProjectSpec.Track track=new ProjectSpec.Track();track.name=source.optString("name","Track "+(i+1));track.group=source.optString("group","VS");track.volume=value(source,"volume",1);track.pan=value(source,"pan",0);track.mute=source.optBoolean("mute");track.solo=source.optBoolean("solo");
   if(source.has("output")&&!source.isNull("output"))result.warnings.add("routing");JSONArray items=source.optJSONArray("items");if(items==null)throw new ProjectSpec.Invalid();
   for(int j=0;j<items.length();j++){JSONObject item=items.getJSONObject(j);if(Math.abs(value(item,"pitch",0))>.00001||Math.abs(value(item,"playbackRate",1)-1)>.00001)throw new ProjectSpec.Processing();ProjectSpec.Clip clip=new ProjectSpec.Clip();clip.file=item.optString("file","");clip.position=value(item,"position",0);clip.offset=value(item,"offset",0);clip.length=item.isNull("length")?-1:value(item,"length",-1);track.clips.add(clip);}
   result.tracks.add(track);
  }
  boolean solo=result.tracks.stream().anyMatch(t->t.solo),groupSolo=groups.values().stream().anyMatch(g->g.optBoolean("solo"));JSONObject normalization=settings==null?null:settings.optJSONObject("normalization");
  for(ProjectSpec.Track track:result.tracks){boolean special=special(track.name);JSONObject group=groups.get(track.group);boolean muted=track.mute||(solo&&!track.solo);
   if(!solo&&special&&mix!=null&&mix.optBoolean("vsSolo"))muted=true;
   if(!special){muted|=(mix!=null&&mix.optBoolean("vsMute"))||(group!=null&&group.optBoolean("mute"));if(!solo&&groupSolo&&(group==null||!group.optBoolean("solo")))muted=true;track.volume*=value(mix,"vsVolume",1)*value(group,"volume",1);track.pan=Math.max(-1,Math.min(1,track.pan+value(mix,"vsPan",0)+value(group,"pan",0)));}
   double db=guide(track.name)?value(normalization,"guide",0):special?0:value(normalization,"vs",0);track.volume*=Math.pow(10,db/20);if(muted)track.volume=0;
  }
  JSONArray regions=root.optJSONArray("regions");if(regions!=null)for(int i=0;i<regions.length();i++){JSONObject r=regions.getJSONObject(i);result.regions.add(new ProjectSpec.Region(r.optString("name",""),value(r,"start",0),value(r,"end",0),r.optString("color","")));}
  JSONObject arrangement=root.optJSONObject("arrangement");if(arrangement!=null){result.automationEnabled=arrangement.optBoolean("enabled",true);JSONArray rules=arrangement.optJSONArray("rules");if(rules!=null)for(int i=0;i<rules.length();i++){JSONObject rule=rules.getJSONObject(i),from=rule.getJSONObject("from"),to=rule.getJSONObject("to");result.jumps.add(new ProjectSpec.Jump(value(from,"start",-1),value(from,"end",-1),value(to,"start",-1)));}}
  result.validate();return result;
 }
}
