package com.onebytelabs.zlplay;
import android.app.*;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.Button;
import com.onebytelabs.zlplay.audio.*;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.*;
import java.util.*;

/** Uses temporary WAV fixtures; exercises the real import callback and AudioTrack on device. */
public final class WavImportSmoke {
 private static Object field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
 private static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);}
 private static Button find(View v,String text){if(v instanceof Button&&text.contentEquals(((Button)v).getText()))return (Button)v;if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int i=0;i<group.getChildCount();i++){Button b=find(group.getChildAt(i),text);if(b!=null)return b;}}return null;}
 private static void click(Instrumentation test,Activity screen,int text){test.runOnMainSync(()->{Button b=find(screen.findViewById(android.R.id.content),screen.getString(text));if(b==null)throw new AssertionError("Control missing");b.performClick();});}
 private static void fixture(File file,int rate,int channels)throws Exception{
  int frames=rate*5,bytes=frames*channels*2;ByteBuffer b=ByteBuffer.allocate(44+bytes).order(ByteOrder.LITTLE_ENDIAN);
  b.put("RIFF".getBytes("US-ASCII")).putInt(36+bytes).put("WAVEfmt ".getBytes("US-ASCII")).putInt(16).putShort((short)1).putShort((short)channels).putInt(rate).putInt(rate*channels*2).putShort((short)(channels*2)).putShort((short)16).put("data".getBytes("US-ASCII")).putInt(bytes);
  for(int f=0;f<frames;f++)for(int c=0;c<channels;c++)b.putShort((short)(Math.sin(2*Math.PI*(channels==1?660:440)*f/rate)*5000*(c==1?0:1)));
  try(FileOutputStream out=new FileOutputStream(file)){out.write(b.array());}
 }
 private static void waitImport(MainActivity screen)throws Exception{for(int i=0;i<100;i++){Thread.sleep(100);if(!(Boolean)field(screen,"loading"))return;}throw new AssertionError("Import timed out");}
 public static Bundle run(Instrumentation test)throws Exception{
  MainActivity screen=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  File dir=new File(test.getTargetContext().getCacheDir(),"wav-smoke-"+System.nanoTime());check(dir.mkdir(),"Fixture directory");File a=new File(dir,"Left.wav"),b=new File(dir,"Mono44100.wav"),bad=new File(dir,"Invalid.wav");
  try{
   fixture(a,48000,2);fixture(b,44100,1);try(FileOutputStream out=new FileOutputStream(bad)){out.write(new byte[12]);}
   waitImport(screen);test.runOnMainSync(()->screen.importAudioUris(Arrays.asList(Uri.fromFile(a),Uri.fromFile(b))));waitImport(screen);
   DemoMixer mixer=(DemoMixer)field(screen,"mixer");check(mixer.hasImported()&&mixer.trackCount()==2&&Math.abs(mixer.duration()-5)<.001,"Decoded real WAVs");
   click(test,screen,R.string.play);Thread.sleep(500);AudioSession audio=(AudioSession)field(screen,"audio");check(audio!=null&&audio.error()==null&&audio.writtenFrames()>4800&&audio.blockRms()>.005,"Imported PCM reaches AudioTrack");
   click(test,screen,R.string.pause);double paused=mixer.position();Thread.sleep(250);check(mixer.position()==paused,"Pause freezes imported audio clock");
   test.runOnMainSync(()->{mixer.seek(4.9);mixer.setLoop(true);});click(test,screen,R.string.play);Thread.sleep(450);check(mixer.position()<1&&mixer.isPlaying(),"Loop follows imported duration");
   test.runOnMainSync(()->{mixer.setGain(0,0);mixer.setGain(1,0);});Thread.sleep(300);check(audio.blockRms()<.001,"Real track gains silence mix");
   test.runOnMainSync(()->{mixer.setGain(0,.7f);mixer.setGain(1,.7f);});Thread.sleep(300);check(audio.blockRms()>.005,"Real gain restore");
   click(test,screen,R.string.stop);check(mixer.position()==0&&!mixer.isPlaying(),"Stop resets imported audio");
   test.runOnMainSync(()->screen.importAudioUris(Collections.singletonList(Uri.fromFile(bad))));waitImport(screen);check(mixer.trackCount()==2&&Math.abs(mixer.duration()-5)<.001,"Failed import preserves previous project");
   check(a.length()==44+48000*5*4&&b.length()==44+44100*5*2,"Original fixture files unchanged");Bundle result=new Bundle();result.putString("status","PASS: two real WAVs, 48k/44.1k conversion, AudioTrack, pause/loop/stop, gains and failed-import rollback");return result;
  }finally{test.runOnMainSync(screen::finish);a.delete();b.delete();bad.delete();dir.delete();}
 }
}
