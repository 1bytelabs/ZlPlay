package com.onebytelabs.zlplay.project;
import android.content.Context;
import android.net.Uri;
import com.onebytelabs.zlplay.audio.*;
import java.util.*;

/** Transactional opening: keep the current session if any source fails validation. */
public final class ProjectLoader {
 public static final class Result {
  public final ProjectSpec spec;public final PcmTrack[] tracks;public final float[] peaks;
  Result(ProjectSpec spec,PcmTrack[] tracks,float[] peaks){this.spec=spec;this.tracks=tracks;this.peaks=peaks;}
  public void close(){for(PcmTrack track:tracks)track.close();}
 }
 public static Result read(Context context,ProjectFolder folder,String filename,WavImport.Progress progress)throws Exception{
  String text=folder.read(context,filename);ProjectSpec spec=filename.toLowerCase(Locale.ROOT).endsWith(".rpp")?RppReader.parse(text,filename.substring(filename.lastIndexOf('/')+1).replaceFirst("(?i)\\.rpp$","")):ZlplayReader.parse(text);
  List<Uri> uris=new ArrayList<>();for(ProjectSpec.Track track:spec.tracks)for(ProjectSpec.Clip clip:track.clips)uris.add(folder.resolve(filename,clip.file));
  WavImport.Result sources=WavImport.readSources(context,uris,progress,64);
  try{
   if(sources.temporary)spec.warnings.add("mp3-cache");
   int index=0;for(ProjectSpec.Track track:spec.tracks)for(ProjectSpec.Clip clip:track.clips){PcmTrack source=sources.tracks[index++];double available=source.duration()-clip.offset;if(available<=0)throw new ProjectSpec.Invalid();if(clip.length<0)clip.length=available;if(clip.length>available+.01)throw new ProjectSpec.Processing();spec.duration=Math.max(spec.duration,clip.position+clip.length);}
   spec.validate();PcmTrack[] tracks=new PcmTrack[spec.tracks.size()];index=0;
   boolean solo=spec.tracks.stream().anyMatch(t->t.solo);for(int i=0;i<tracks.length;i++){ProjectSpec.Track t=spec.tracks.get(i);if(t.mute||(solo&&!t.solo))t.volume=0;List<TimelineTrack.Clip> clips=new ArrayList<>();for(ProjectSpec.Clip c:t.clips)clips.add(new TimelineTrack.Clip(sources.tracks[index++],c.position,c.offset,c.length,c.gain,c.pan));tracks[i]=new TimelineTrack(t.name,clips.toArray(new TimelineTrack.Clip[0]),spec.duration);}
   float[] peaks=new float[1024];for(PcmTrack track:tracks)track.addPeaks(peaks,spec.duration);for(int i=0;i<peaks.length;i++)peaks[i]=Math.min(1,peaks[i]);
   return new Result(spec,tracks,peaks);
  }catch(Exception|Error failure){sources.close();throw failure;}
 }
}
