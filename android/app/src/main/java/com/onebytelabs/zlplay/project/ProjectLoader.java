package com.onebytelabs.zlplay.project;
import android.content.Context;
import android.net.Uri;
import com.onebytelabs.zlplay.audio.*;
import java.text.Normalizer;
import java.util.*;
import org.json.JSONObject;

/** Transactional opening: keep the current session if any source fails validation. */
public final class ProjectLoader {
 public static final class Result {
  public final ProjectSpec spec;public final PcmTrack[] tracks;public final float[] peaks;public final PcmTrack guidePreview;public final int guideIndex;
  public final String manifest;
  public final Map<String,SourceWaveform> waveforms;
  Result(ProjectSpec spec,PcmTrack[] tracks,float[] peaks,PcmTrack preview,int index,String manifest,Map<String,SourceWaveform> waveforms){this.spec=spec;this.tracks=tracks;this.peaks=peaks;guidePreview=preview;guideIndex=index;this.manifest=manifest;this.waveforms=waveforms;}
  public void close(){for(PcmTrack track:tracks)track.close();if(guidePreview!=null)guidePreview.close();}
 }
 private static boolean guide(String name){String n=Normalizer.normalize(name,Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT);return n.contains("guia")||n.contains("guide");}
 public static Result read(Context context,ProjectFolder folder,String filename,WavImport.Progress progress)throws Exception{
  return read(context,folder,filename,null,progress);
 }
 public static Result read(Context context,ProjectFolder folder,String filename,String snapshot,WavImport.Progress progress)throws Exception{
  String text=snapshot==null?folder.read(context,filename):snapshot;
  boolean rpp=snapshot==null&&filename.toLowerCase(Locale.ROOT).endsWith(".rpp");
  ProjectSpec spec=rpp?RppReader.parse(text,filename.substring(filename.lastIndexOf('/')+1).replaceFirst("(?i)\\.rpp$","")):ZlplayReader.parse(text);
  JSONObject editable=new JSONObject(rpp?ProjectAuthor.manifest(spec):text);
  Map<ProjectSpec.Clip,String> generatedReferences=new IdentityHashMap<>();
  Map<ProjectSpec.Clip,JSONObject> editableItems=new IdentityHashMap<>();
  List<Uri> uris=new ArrayList<>();Map<String,Integer> streams=new HashMap<>();
  Map<ProjectSpec.Clip,Integer> indices=new IdentityHashMap<>(),previewIndices=new IdentityHashMap<>();int guideIndex=-1;
  for(int i=0;i<spec.tracks.size();i++){
   ProjectSpec.Track track=spec.tracks.get(i);
   int clipIndex=0;
   for(Iterator<ProjectSpec.Clip> clips=track.clips.iterator();clips.hasNext();){
    ProjectSpec.Clip clip=clips.next();
    JSONObject item=editable.getJSONArray("tracks").getJSONObject(i).getJSONArray("items").getJSONObject(clipIndex++);
    if(clip.file.startsWith("@click:")){
     if(clip.length<=0)throw new ProjectSpec.Processing();track.role=2;
     generatedReferences.put(clip,clip.file);editableItems.put(clip,item);
     String audio=folder.clickAudioReference(filename,track.name);
     if(audio==null){clip.nativeClick=true;spec.warnings.add("native-click");continue;}
     clip.file=audio;item.put("file",audio);
    }
    try{
     Uri uri=folder.resolve(filename,clip.file);
     // Cuts on the same source/time mapping share a bounded reader. Different
     // alignments and the guide preview retain independent read windows.
     String key=i+"|"+uri+"|"+(Math.round(clip.offset*DemoMixer.RATE)-Math.round(clip.position*DemoMixer.RATE));
     Integer index=streams.get(key);if(index==null){index=uris.size();uris.add(uri);streams.put(key,index);}
     indices.put(clip,index);
    }catch(ProjectFolder.Missing missing){
     spec.missingAudio.add(clip.file.replace('\\','/'));
     if(clip.length>0)spec.duration=Math.max(spec.duration,clip.position+clip.length);
     clips.remove();
    }
   }
   if(guideIndex<0&&track.isGuide()&&!track.clips.isEmpty())guideIndex=i;
  }
  if(spec.tracks.stream().allMatch(track->track.clips.isEmpty()))throw new ProjectFolder.Missing();
  if(guideIndex>=0)for(ProjectSpec.Clip c:spec.tracks.get(guideIndex).clips){
   if(c.nativeClick)throw new ProjectSpec.Invalid();Uri uri=uris.get(indices.get(c));
   String key="preview|"+uri+"|"+(Math.round(c.offset*DemoMixer.RATE)-Math.round(c.position*DemoMixer.RATE));
   Integer index=streams.get(key);if(index==null){index=uris.size();uris.add(uri);streams.put(key,index);}
   previewIndices.put(c,index);
  }
  Map<String,Double> generatedAudioEnds=new HashMap<>();
  for(ProjectSpec.Clip clip:generatedReferences.keySet())if(!clip.nativeClick)
   generatedAudioEnds.merge(clip.file,clip.offset+clip.length,Math::max);
  WavImport.Result sources=uris.isEmpty()?null:WavImport.readSources(context,uris,progress,128);
  try{
   if(sources!=null&&sources.temporary)spec.warnings.add("mp3-cache");Map<ProjectSpec.Clip,PcmTrack> decoded=new IdentityHashMap<>();
   for(ProjectSpec.Track track:spec.tracks)for(ProjectSpec.Clip clip:track.clips){
    PcmTrack source=clip.nativeClick?new ProjectClickTrack(clip.offset+clip.length,spec.bpm,spec.numerator):sources.tracks[indices.get(clip)];
    double available=source.duration()-clip.offset;
    if(!clip.nativeClick&&generatedReferences.containsKey(clip)&&source.duration()+.01<generatedAudioEnds.get(clip.file)){
     clip.file=generatedReferences.get(clip);clip.nativeClick=true;spec.warnings.add("native-click");
     editableItems.get(clip).put("file",clip.file);source=new ProjectClickTrack(clip.offset+clip.length,spec.bpm,spec.numerator);available=clip.length;
    }
    decoded.put(clip,source);if(available<=0)throw new ProjectSpec.Invalid();if(clip.length<0)clip.length=available;
    if(clip.length>available+.01)throw new ProjectSpec.Processing();spec.duration=Math.max(spec.duration,clip.position+clip.length);
   }
   spec.validate();PcmTrack[] tracks=new PcmTrack[spec.tracks.size()];boolean solo=spec.tracks.stream().anyMatch(t->t.solo);
   for(int i=0;i<tracks.length;i++){ProjectSpec.Track t=spec.tracks.get(i);List<TimelineTrack.Clip> clips=new ArrayList<>();for(ProjectSpec.Clip c:t.clips)clips.add(timelineClip(decoded.get(c),c));tracks[i]=new TimelineTrack(t.name,clips.toArray(new TimelineTrack.Clip[0]),spec.duration);}
   PcmTrack preview=null;if(guideIndex>=0){List<TimelineTrack.Clip> clips=new ArrayList<>();for(ProjectSpec.Clip c:spec.tracks.get(guideIndex).clips)clips.add(timelineClip(sources.tracks[previewIndices.get(c)],c));preview=new TimelineTrack(spec.tracks.get(guideIndex).name,clips.toArray(new TimelineTrack.Clip[0]),spec.duration);}
   float[] peaks=new float[1024];for(PcmTrack track:tracks)track.addPeaks(peaks,spec.duration);for(int i=0;i<peaks.length;i++)peaks[i]=Math.min(1,peaks[i]);
   Map<String,SourceWaveform> waveforms=new HashMap<>();
   for(Map.Entry<ProjectSpec.Clip,PcmTrack> entry:decoded.entrySet())
    waveforms.computeIfAbsent(entry.getKey().file,file->SourceWaveform.from(entry.getValue()));
   if(sources!=null&&decoded.values().stream().allMatch(source->source instanceof ProjectClickTrack))sources.close();
   return new Result(spec,tracks,peaks,preview,guideIndex,editable.toString(),waveforms);
  }catch(Exception|Error failure){if(sources!=null)sources.close();throw failure;}
 }
 private static TimelineTrack.Clip timelineClip(PcmTrack source,ProjectSpec.Clip clip){
  double gain=clip.muted?0:clip.gain*Math.pow(10,clip.trimDb/20);
  return new TimelineTrack.Clip(source,clip.position,clip.offset,clip.length,gain,clip.pan,clip.eq);
 }
}
