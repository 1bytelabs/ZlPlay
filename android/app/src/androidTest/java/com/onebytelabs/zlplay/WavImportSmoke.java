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
 private static void fixture(File file,int rate,int channels,int seconds)throws Exception{
  int data=rate*seconds*channels*2;ByteBuffer header=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
  header.put("RIFF".getBytes("US-ASCII")).putInt(36+data).put("WAVEfmt ".getBytes("US-ASCII")).putInt(16).putShort((short)1).putShort((short)channels).putInt(rate).putInt(rate*channels*2).putShort((short)(channels*2)).putShort((short)16).put("data".getBytes("US-ASCII")).putInt(data);
  ByteBuffer block=ByteBuffer.allocate(rate*channels*2).order(ByteOrder.LITTLE_ENDIAN);
  for(int frame=0;frame<rate;frame++)for(int c=0;c<channels;c++)block.putShort((short)(Math.sin(2*Math.PI*(channels==1?660:440)*frame/rate)*5000*(c==1?0:1)));
  try(FileOutputStream out=new FileOutputStream(file)){out.write(header.array());for(int second=0;second<seconds;second++)out.write(block.array());}
 }
 private static void waitImport(MainActivity screen)throws Exception{for(int i=0;i<100;i++){Thread.sleep(100);if(!(Boolean)field(screen,"loading"))return;}throw new AssertionError("Import timed out");}
 public static Bundle run(Instrumentation test)throws Exception{
  android.app.KeyguardManager keyguard=(android.app.KeyguardManager)test.getTargetContext().getSystemService(android.content.Context.KEYGUARD_SERVICE);
  check(!keyguard.isDeviceLocked(),"Unlock the phone before foreground audio tests");
  MainActivity screen=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).putExtra("com.onebytelabs.zlplay.SKIP_RESTORE_TEST",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  File dir=new File(test.getTargetContext().getCacheDir(),"wav-smoke-"+System.nanoTime());check(dir.mkdir(),"Fixture directory");File a=new File(dir,"Left.wav"),b=new File(dir,"Mono44100.wav"),bad=new File(dir,"Invalid.wav");
  try{
   fixture(a,48000,2,600);fixture(b,44100,1,5);try(FileOutputStream out=new FileOutputStream(bad)){out.write(new byte[12]);}
   waitImport(screen);test.runOnMainSync(()->screen.importAudioUris(Arrays.asList(Uri.fromFile(a),Uri.fromFile(b))));waitImport(screen);
   DemoMixer mixer=(DemoMixer)field(screen,"mixer");check(mixer.hasImported()&&mixer.trackCount()==2&&Math.abs(mixer.duration()-600)<.001,"Decoded real WAVs");
   click(test,screen,R.string.play);Thread.sleep(500);AudioSession audio=(AudioSession)field(screen,"audio");check(audio!=null&&audio.error()==null&&audio.writtenFrames()>4800&&audio.blockRms()>.005,"Imported PCM reaches AudioTrack: error="+(audio==null?"null":audio.error())+", frames="+(audio==null?0:audio.writtenFrames())+", RMS="+(audio==null?0:audio.blockRms())+", playing="+mixer.isPlaying()+", buffering="+mixer.isBuffering()+", sourceFailed="+mixer.sourceFailed());
   click(test,screen,R.string.pause);double paused=mixer.position();Thread.sleep(250);check(mixer.position()==paused,"Pause freezes imported audio clock");
   test.runOnMainSync(()->{mixer.seek(599.9);mixer.setLoop(true);});click(test,screen,R.string.play);Thread.sleep(450);check(mixer.position()<1&&mixer.isPlaying(),"Loop follows imported duration");
   test.runOnMainSync(()->{mixer.setGain(0,0);mixer.setGain(1,0);});Thread.sleep(300);check(audio.blockRms()<.001,"Real track gains silence mix");
   test.runOnMainSync(()->{mixer.setGain(0,.7f);mixer.setGain(1,.7f);});Thread.sleep(300);check(audio.blockRms()>.005,"Real gain restore");
   click(test,screen,R.string.stop);check(mixer.position()==0&&!mixer.isPlaying(),"Stop resets imported audio");
   test.runOnMainSync(()->screen.importAudioUris(Collections.singletonList(Uri.fromFile(bad))));waitImport(screen);check(mixer.trackCount()==2&&Math.abs(mixer.duration()-600)<.001,"Failed import preserves previous project");
   check(a.length()==44+48000*600*4&&b.length()==44+44100*5*2,"Original fixture files unchanged");Bundle result=new Bundle();result.putString("status","PASS: 115 MB / 10-minute WAV streamed with 44.1k source, AudioTrack, pause/seek/loop/stop, gains and failed-import rollback");return result;
  }finally{test.runOnMainSync(screen::finish);a.delete();b.delete();bad.delete();dir.delete();}
 }
}
