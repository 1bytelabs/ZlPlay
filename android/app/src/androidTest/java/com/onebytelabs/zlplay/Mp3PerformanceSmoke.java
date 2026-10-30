package com.onebytelabs.zlplay;
import android.app.*;import android.os.*;import android.net.Uri;import android.util.Base64;import com.onebytelabs.zlplay.audio.*;import java.io.*;import java.nio.*;
/** Synthetic throughput and timing regression; no user media or grants required. */
public final class Mp3PerformanceSmoke {
 private static double compare(Instrumentation test,File source)throws Exception{
  File platform=null,fast=null;try{platform=Mp3Decode.decodeAndroid(test.getTargetContext(),Uri.fromFile(source));fast=Mp3Decode.decode(test.getTargetContext(),Uri.fromFile(source));PcmTrack a,b;try(InputStream in=new FileInputStream(platform)){a=WavReader.decode(in,"reference",2*1024*1024);}try(InputStream in=new FileInputStream(fast)){b=WavReader.decode(in,"fast",2*1024*1024);}if(Math.abs(a.duration()-b.duration())>1.0/a.sampleRate)throw new AssertionError("MP3 duration changed");double error=0,power=0;int onsetA=-1,onsetB=-1;for(int frame=0;frame<48000;frame++){float x=a.sample(frame,0),y=b.sample(frame,0);if(onsetA<0&&Math.abs(x)>.01)onsetA=frame;if(onsetB<0&&Math.abs(y)>.01)onsetB=frame;error+=(x-y)*(x-y);power+=x*x;}double relative=Math.sqrt(error/Math.max(power,1e-12));if(Math.abs(onsetB-onsetA)>1||relative>.01)throw new AssertionError("MP3 timing/PCM mismatch: "+(onsetB-onsetA)+", "+relative);return relative;}finally{if(platform!=null)platform.delete();if(fast!=null)fast.delete();}
 }
 public static Bundle run(Instrumentation test)throws Exception{
  File source=new File(test.getTargetContext().getCacheDir(),"mp3-performance.mp3"),decoded=null;
  try{ByteArrayOutputStream encoded=new ByteArrayOutputStream();try(InputStream in=test.getContext().getAssets().open("tone.mp3.b64")){byte[] block=new byte[4096];int n;while((n=in.read(block))!=-1)encoded.write(block,0,n);}byte[] mp3=Base64.decode(encoded.toByteArray(),Base64.DEFAULT);try(FileOutputStream out=new FileOutputStream(source)){for(int i=0;i<30;i++)out.write(mp3);}long start=SystemClock.elapsedRealtime();decoded=Mp3Decode.decode(test.getTargetContext(),Uri.fromFile(source));long ms=SystemClock.elapsedRealtime()-start;Bundle result=new Bundle();result.putLong("decodeMs",ms);result.putLong("pcmBytes",decoded.length());if(decoded.length()<4000000)throw new AssertionError("Incomplete 60-second decode");
   try(FileOutputStream out=new FileOutputStream(source)){out.write(mp3);}result.putDouble("plainRelativeError",compare(test,source));
   // Add an empty MPEG-1 mono 64 kbps seek frame; fixtures contain no ID3 tags/CRC.
   int frameSize=144000*64/44100+((mp3[2]>>1)&1);byte[] seek=new byte[frameSize];System.arraycopy(mp3,0,seek,0,4);for(String tag:new String[]{"Info","Xing"}){ByteBuffer header=ByteBuffer.wrap(seek).order(ByteOrder.BIG_ENDIAN);header.position(21);header.put(tag.getBytes("US-ASCII")).putInt(1).putInt(78);try(FileOutputStream out=new FileOutputStream(source)){out.write(seek);out.write(mp3);}result.putDouble(tag+"RelativeError",compare(test,source));}
   result.putString("status","PASS: complete MP3 decode; plain/Info/Xing timing, duration and PCM match platform reference");return result;
  }finally{source.delete();if(decoded!=null)decoded.delete();}
 }
}
