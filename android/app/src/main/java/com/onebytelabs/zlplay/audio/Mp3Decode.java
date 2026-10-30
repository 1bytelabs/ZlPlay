package com.onebytelabs.zlplay.audio;
import android.content.Context;
import android.media.*;
import android.net.Uri;
import android.os.SystemClock;
import java.io.*;
import java.nio.*;

/** Decode on import worker to private temporary PCM; render remains bounded and seekable. */
public final class Mp3Decode {
 public static final class NoSpace extends IOException {public NoSpace(){super("Not enough temporary audio storage");}}
 private static boolean cleaned;
 public static synchronized void clearOrphans(Context context){if(cleaned)return;cleaned=true;File[] files=context.getCacheDir().listFiles();if(files!=null)for(File file:files)if(file.isFile()&&file.getName().startsWith("zlplay-mp3-")&&(file.getName().endsWith(".wav")||file.getName().startsWith("zlplay-mp3-input-")&&file.getName().endsWith(".mp3")))file.delete();}
 private static File localInput(Context context,Uri uri,File directory)throws IOException{
  File local=File.createTempFile("zlplay-mp3-input-",".mp3",directory);boolean complete=false;
  try(InputStream in=context.getContentResolver().openInputStream(uri);OutputStream out=new BufferedOutputStream(new FileOutputStream(local),256*1024)){if(in==null)throw new FileNotFoundException();byte[] buffer=new byte[256*1024];long count=0,next=0;int n;while((n=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new WavReader.Cancelled();if(count>=next){if(directory.getUsableSpace()<36*1024*1024L)throw new NoSpace();next=count+4*1024*1024L;}out.write(buffer,0,n);count+=n;}complete=true;return local;}finally{if(!complete)local.delete();}
 }
 public static File decode(Context context,Uri uri)throws IOException{return FastMp3Decode.decode(context,uri);}
 public static File decodeAndroid(Context context,Uri uri)throws IOException{
  clearOrphans(context);final File cacheDirectory=context.getCacheDir();File output=File.createTempFile("zlplay-mp3-",".wav",context.getCacheDir());MediaExtractor extractor=new MediaExtractor();MediaCodec codec=null;boolean success=false;File compressed=null;
  try(RandomAccessFile wav=new RandomAccessFile(output,"rw")){
   compressed=localInput(context,uri,cacheDirectory);extractor.setDataSource(context,Uri.fromFile(compressed),null);MediaFormat format=null;
   for(int i=0;i<extractor.getTrackCount();i++){MediaFormat candidate=extractor.getTrackFormat(i);if("audio/mpeg".equals(candidate.getString(MediaFormat.KEY_MIME))){format=candidate;extractor.selectTrack(i);break;}}
   if(format==null)throw new WavReader.Unsupported();int rate=format.getInteger(MediaFormat.KEY_SAMPLE_RATE),channels=format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);if(channels<1||channels>2||rate<8000||rate>192000)throw new WavReader.Unsupported();
   if(format.containsKey(MediaFormat.KEY_DURATION)){double seconds=format.getLong(MediaFormat.KEY_DURATION)/1000000.0;if(seconds>86400)throw new WavReader.Unsupported();if(seconds*rate*channels*2>cacheDirectory.getUsableSpace()-32*1024*1024L)throw new NoSpace();}
   long trimStart=format.containsKey(MediaFormat.KEY_ENCODER_DELAY)?Math.max(0,format.getInteger(MediaFormat.KEY_ENCODER_DELAY)):0,trimEnd=format.containsKey(MediaFormat.KEY_ENCODER_PADDING)?Math.max(0,format.getInteger(MediaFormat.KEY_ENCODER_PADDING)):0;format.setInteger(MediaFormat.KEY_ENCODER_DELAY,0);format.setInteger(MediaFormat.KEY_ENCODER_PADDING,0);long skip=trimStart*channels*2;
   format.setInteger(MediaFormat.KEY_PCM_ENCODING,AudioFormat.ENCODING_PCM_16BIT);codec=MediaCodec.createDecoderByType("audio/mpeg");codec.configure(format,null,null,0);codec.start();wav.write(new byte[44]);byte[] scratch=new byte[65536];MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();boolean inputEnd=false,outputEnd=false;long bytes=0,last=SystemClock.elapsedRealtime(),nextSpaceCheck=0;
   try(BufferedOutputStream pcm=new BufferedOutputStream(new FileOutputStream(output,true),256*1024)){
   while(!outputEnd){boolean work=false;if(Thread.currentThread().isInterrupted())throw new WavReader.Cancelled();if(SystemClock.elapsedRealtime()-last>15000)throw new IOException("MP3 decoder stalled");
    for(int batch=0;batch<8&&!inputEnd;batch++){int index=codec.dequeueInputBuffer(0);if(index<0)break;if(index>=0){ByteBuffer input=codec.getInputBuffer(index);if(input==null)throw new IOException("Missing codec input");int size=extractor.readSampleData(input,0);if(size<0){codec.queueInputBuffer(index,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputEnd=true;}else{codec.queueInputBuffer(index,0,size,Math.max(0,extractor.getSampleTime()),0);extractor.advance();}work=true;last=SystemClock.elapsedRealtime();}}
    int index=codec.dequeueOutputBuffer(info,0);if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){work=true;MediaFormat decoded=codec.getOutputFormat();int newRate=decoded.getInteger(MediaFormat.KEY_SAMPLE_RATE),newChannels=decoded.getInteger(MediaFormat.KEY_CHANNEL_COUNT),encoding=decoded.containsKey(MediaFormat.KEY_PCM_ENCODING)?decoded.getInteger(MediaFormat.KEY_PCM_ENCODING):AudioFormat.ENCODING_PCM_16BIT;if(encoding!=AudioFormat.ENCODING_PCM_16BIT||newChannels!=channels||newRate!=rate)throw new WavReader.Unsupported();}
    else if(index>=0){ByteBuffer data=codec.getOutputBuffer(index);if(info.size>0){if(data==null||info.size%(channels*2)!=0)throw new IOException("Invalid decoded PCM");if(bytes+info.size>0xffffffffL-36)throw new NoSpace();if(bytes>=nextSpaceCheck){if(cacheDirectory.getUsableSpace()<36*1024*1024L)throw new NoSpace();nextSpaceCheck=bytes+4*1024*1024L;}data.position(info.offset);data.limit(info.offset+info.size);int skipped=(int)Math.min(skip,data.remaining());data.position(data.position()+skipped);skip-=skipped;int retained=data.remaining();while(data.hasRemaining()){int n=Math.min(scratch.length,data.remaining());data.get(scratch,0,n);pcm.write(scratch,0,n);}bytes+=retained;}outputEnd=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;codec.releaseOutputBuffer(index,false);work=true;last=SystemClock.elapsedRealtime();}
    if(!work)try{Thread.sleep(1);}catch(InterruptedException stopped){Thread.currentThread().interrupt();throw new WavReader.Cancelled();}
   }
   }
   bytes-=Math.min(bytes,trimEnd*channels*2);if(bytes==0||skip>0)throw new WavReader.Unsupported();wav.setLength(44+bytes);ByteBuffer header=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);header.put(new byte[]{'R','I','F','F'}).putInt((int)(36+bytes)).put(new byte[]{'W','A','V','E','f','m','t',' '}).putInt(16).putShort((short)1).putShort((short)channels).putInt(rate).putInt(rate*channels*2).putShort((short)(channels*2)).putShort((short)16).put(new byte[]{'d','a','t','a'}).putInt((int)bytes);wav.seek(0);wav.write(header.array());success=true;return output;
  }catch(MediaCodec.CodecException|IllegalArgumentException failure){throw new IOException("MP3 decoder unavailable",failure);}finally{try{if(codec!=null)codec.release();}finally{extractor.release();if(compressed!=null)compressed.delete();if(!success)output.delete();}}
 }
 public static WavFile open(File cache,String name)throws IOException{
  RandomAccessFile file=new RandomAccessFile(cache,"r");Closeable owner=()->{try{file.close();}finally{cache.delete();}};try{return new WavFile(name,file.getChannel(),owner,0,file.length());}catch(IOException|RuntimeException failure){owner.close();throw failure;}
 }
}
