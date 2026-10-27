package com.onebytelabs.zlplay;
import android.app.*;import android.content.*;import android.os.*;import com.onebytelabs.zlplay.audio.*;import com.onebytelabs.zlplay.project.*;import java.io.*;import java.nio.*;import java.lang.reflect.*;import java.util.*;

/** Repeatable real-device 25-stem streaming/UI stress using one generated PCM fixture. */
public final class ManyTracksSmoke {
 private static Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
 public static Bundle run(Instrumentation test)throws Exception{
  MainActivity screen=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).putExtra("com.onebytelabs.zlplay.SKIP_RESTORE_TEST",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  File file=new File(screen.getCacheDir(),"many-tracks-fixture.wav");StreamBank bank=null;PitchBank pitches=null;
  try{
   int rate=48000,seconds=90;byte[] data=new byte[rate*seconds*2+44];ByteBuffer h=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);h.put("RIFF".getBytes()).putInt(data.length-8).put("WAVEfmt ".getBytes()).putInt(16).putShort((short)1).putShort((short)1).putInt(rate).putInt(rate*2).putShort((short)2).putShort((short)16).put("data".getBytes()).putInt(data.length-44);for(int i=0;i<rate*seconds;i++)h.putShort((short)(Math.sin(2*Math.PI*220*i/rate)*100));try(FileOutputStream out=new FileOutputStream(file)){out.write(data);}
   List<WavFile> files=new ArrayList<>();List<float[]> peaks=new ArrayList<>();for(int i=0;i<25;i++){FileInputStream in=new FileInputStream(file);files.add(new WavFile("Stem "+i,in.getChannel(),in,0,file.length()));peaks.add(new float[]{.1f,.2f,.1f});}bank=new StreamBank(files,peaks);PcmTrack[] timeline=new PcmTrack[25];for(int i=0;i<25;i++)timeline[i]=new TimelineTrack("Stem "+i,new TimelineTrack.Clip[]{new TimelineTrack.Clip(bank.tracks[i],0,0,90,1,0)},90);
   DemoMixer mixer=(DemoMixer)field(screen,"mixer");pitches=new PitchBank(timeline,mixer);PcmTrack[] tracks=pitches.tracks;test.runOnMainSync(()->{mixer.load(tracks);mixer.play();});
   Method ready=MainActivity.class.getDeclaredMethod("ready");ready.setAccessible(true);test.runOnMainSync(()->{try{ready.invoke(screen);}catch(Exception e){throw new RuntimeException(e);}});Thread.sleep(1000);
   long begin=SystemClock.elapsedRealtime(),worst=0;double start=mixer.position();for(int i=0;i<40;i++){long at=SystemClock.elapsedRealtime();test.runOnMainSync(()->mixer.setGain(12,.6f));worst=Math.max(worst,SystemClock.elapsedRealtime()-at);Thread.sleep(100);}
   double elapsed=(SystemClock.elapsedRealtime()-begin)/1000.0,progress=mixer.position()-start;AudioSession audio=(AudioSession)field(screen,"audio");if(audio.error()!=null||mixer.sourceFailed()||progress<elapsed*.90)throw new AssertionError("Streaming did not keep up: progress="+progress+" elapsed="+elapsed+" error="+audio.error()+", maxUi="+worst);
   Bundle result=new Bundle();result.putString("status","PASS: 25 real streamed stems and concurrent UI controls");result.putString("timing","progress="+progress+", wall="+elapsed+", maxUiCommandMs="+worst);return result;
  }finally{test.runOnMainSync(screen::finish);Thread.sleep(250);if(pitches!=null)pitches.close();if(bank!=null)bank.close();file.delete();}
 }
}
