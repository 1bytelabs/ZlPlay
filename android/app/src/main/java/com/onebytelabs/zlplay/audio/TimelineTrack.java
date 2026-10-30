package com.onebytelabs.zlplay.audio;

/** Timeline clip placement over separately buffered sources. */
public final class TimelineTrack extends PcmTrack {
 public static final class Clip {final PcmTrack source;final long start,end,offset;final double gain,left,right;
  public Clip(PcmTrack source,double position,double offset,double length,double gain,double pan){this.source=source;this.start=Math.round(position*DemoMixer.RATE);this.offset=Math.round(offset*DemoMixer.RATE);this.end=start+Math.round(length*DemoMixer.RATE);this.gain=gain;this.left=pan>0?1-pan:1;this.right=pan<0?1+pan:1;source.hint(this.offset);}
 }
 private final Clip[] clips;private final double duration;private final float[] scratch=new float[1024];
 public TimelineTrack(String name,Clip[] clips,double duration){super(name,DemoMixer.RATE,2);this.clips=clips;this.duration=duration;}
 @Override public double duration(){return duration;}
 @Override public boolean failed(){for(Clip clip:clips)if(clip.source.failed())return true;return false;}
 @Override public boolean prepare(long frame,int count){boolean ready=true;for(Clip clip:clips){long first=Math.max(frame,clip.start),end=Math.min(frame+count,clip.end);if(first<end)ready=clip.source.prepare(clip.offset+first-clip.start,(int)(end-first))&&ready;else if(clip.start>=frame&&clip.start-frame<DemoMixer.RATE*2L)clip.source.hint(clip.offset);}return ready;}
 @Override public void hint(long frame){for(Clip clip:clips)if(frame>=clip.start&&frame<clip.end)clip.source.hint(clip.offset+frame-clip.start);}
 @Override public float sample(long frame,int channel){double sum=0;for(Clip clip:clips)if(frame>=clip.start&&frame<clip.end)sum+=clip.source.sample(clip.offset+frame-clip.start,channel)*clip.gain*(channel==0?clip.left:clip.right);return(float)sum;}
 @Override public void readStereo(long frame,int count,float[] output,int offset){
  java.util.Arrays.fill(output,offset,offset+count*2,0);
  for(Clip clip:clips){long first=Math.max(frame,clip.start),end=Math.min(frame+count,clip.end);int total=(int)(end-first);
   for(int at=0;at<total;at+=512){int n=Math.min(512,total-at);clip.source.readStereo(clip.offset+first+at-clip.start,n,scratch,0);int out=offset+(int)(first+at-frame)*2;float l=(float)(clip.gain*clip.left),r=(float)(clip.gain*clip.right);for(int i=0;i<n;i++){output[out+i*2]+=scratch[i*2]*l;output[out+i*2+1]+=scratch[i*2+1]*r;}}
  }
 }
 @Override public void addPeaks(float[] into,double total){for(int i=0;i<into.length;i++){long frame=(long)(i/(double)into.length*total*DemoMixer.RATE);for(Clip clip:clips)if(frame>=clip.start&&frame<clip.end)into[i]+=clip.source.previewPeak((clip.offset+frame-clip.start)/(double)DemoMixer.RATE)*clip.gain*.7f;}}
 @Override public long decodedBytes(){long total=0;for(Clip clip:clips)total+=clip.source.decodedBytes();return total;}
 @Override public void close(){for(Clip clip:clips)clip.source.close();}
}
