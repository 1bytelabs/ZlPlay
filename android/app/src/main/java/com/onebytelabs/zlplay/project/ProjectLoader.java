package com.onebytelabs.zlplay.project;
import android.content.Context;
import android.net.Uri;
import com.onebytelabs.zlplay.audio.*;
import java.text.Normalizer;
import java.util.*;

/** Transactional opening: keep the current session if any source fails validation. */
public final class ProjectLoader {
 public static final class Result {
  public final ProjectSpec spec;public final PcmTrack[] tracks;public final float[] peaks;public final PcmTrack guidePreview;public final int guideIndex;
  Result(ProjectSpec spec,PcmTrack[] tracks,float[] peaks,PcmTrack preview,int index){this.spec=spec;this.tracks=tracks;this.peaks=peaks;guidePreview=preview;guideIndex=index;}
  public void close(){for(PcmTrack track:tracks)track.close();if(guidePreview!=null)guidePreview.close();}
 }
 private static boolean guide(String name){String n=Normalizer.normalize(name,Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT);return n.contains("guia")||n.contains("guide");}
 public static Result read(Context context,ProjectFolder folder,String filename,WavImport.Progress progress)throws Exception{
  String text=folder.read(context,filename);ProjectSpec spec=filename.toLowerCase(Locale.ROOT).endsWith(".rpp")?RppReader.parse(text,filename.substring(filename.lastIndexOf('/')+1).replaceFirst("(?i)\\.rpp$","")):ZlplayReader.parse(text);
  List<Uri> uris=new ArrayList<>();Map<ProjectSpec.Clip,Integer> indices=new IdentityHashMap<>();int guideIndex=-1;
  for(int i=0;i<spec.tracks.size();i++){ProjectSpec.Track track=spec.tracks.get(i);if(guideIndex<0&&track.isGuide())guideIndex=i;for(ProjectSpec.Clip clip:track.clips){try{Uri uri=folder.resolve(filename,clip.file);indices.put(clip,uris.size());uris.add(uri);}catch(ProjectFolder.Missing missing){if(!clip.file.startsWith("@click:"))throw missing;clip.nativeClick=true;spec.warnings.add("native-click");if(clip.length<=0)throw new ProjectSpec.Processing();}}}
  int previewStart=uris.size();if(guideIndex>=0)for(ProjectSpec.Clip c:spec.tracks.get(guideIndex).clips){if(c.nativeClick)throw new ProjectSpec.Invalid();uris.add(uris.get(indices.get(c)));}
  WavImport.Result sources=uris.isEmpty()?null:WavImport.readSources(context,uris,progress,128);
  try{
   if(sources!=null&&sources.temporary)spec.warnings.add("mp3-cache");Map<ProjectSpec.Clip,PcmTrack> decoded=new IdentityHashMap<>();
   for(ProjectSpec.Track track:spec.tracks)for(ProjectSpec.Clip clip:track.clips){PcmTrack source=clip.nativeClick?new ProjectClickTrack(clip.offset+clip.length,spec.bpm,spec.numerator):sources.tracks[indices.get(clip)];decoded.put(clip,source);double available=source.duration()-clip.offset;if(available<=0)throw new ProjectSpec.Invalid();if(clip.length<0)clip.length=available;if(clip.length>available+.01)throw new ProjectSpec.Processing();spec.duration=Math.max(spec.duration,clip.position+clip.length);}
   spec.validate();PcmTrack[] tracks=new PcmTrack[spec.tracks.size()];boolean solo=spec.tracks.stream().anyMatch(t->t.solo);
   for(int i=0;i<tracks.length;i++){ProjectSpec.Track t=spec.tracks.get(i);List<TimelineTrack.Clip> clips=new ArrayList<>();for(ProjectSpec.Clip c:t.clips)clips.add(new TimelineTrack.Clip(decoded.get(c),c.position,c.offset,c.length,c.gain,c.pan));tracks[i]=new TimelineTrack(t.name,clips.toArray(new TimelineTrack.Clip[0]),spec.duration);}
   PcmTrack preview=null;if(guideIndex>=0){List<TimelineTrack.Clip> clips=new ArrayList<>();for(ProjectSpec.Clip c:spec.tracks.get(guideIndex).clips)clips.add(new TimelineTrack.Clip(sources.tracks[previewStart++],c.position,c.offset,c.length,c.gain,c.pan));preview=new TimelineTrack(spec.tracks.get(guideIndex).name,clips.toArray(new TimelineTrack.Clip[0]),spec.duration);}
   float[] peaks=new float[1024];for(PcmTrack track:tracks)track.addPeaks(peaks,spec.duration);for(int i=0;i<peaks.length;i++)peaks[i]=Math.min(1,peaks[i]);return new Result(spec,tracks,peaks,preview,guideIndex);
  }catch(Exception|Error failure){if(sources!=null)sources.close();throw failure;}
 }
}
