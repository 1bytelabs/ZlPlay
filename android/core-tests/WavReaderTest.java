import com.onebytelabs.zlplay.audio.*;
import java.io.*;
import java.nio.*;
import java.util.*;
public final class WavReaderTest {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static byte[] wav(int rate,int channels,int encoding,int bits,float[] samples)throws Exception{
  int bytes=bits/8,data=samples.length*bytes;ByteBuffer b=ByteBuffer.allocate(44+data+(data&1)).order(ByteOrder.LITTLE_ENDIAN);
  b.put("RIFF".getBytes("US-ASCII")).putInt(36+data+(data&1)).put("WAVEfmt ".getBytes("US-ASCII")).putInt(16).putShort((short)encoding).putShort((short)channels).putInt(rate).putInt(rate*channels*bytes).putShort((short)(channels*bytes)).putShort((short)bits).put("data".getBytes("US-ASCII")).putInt(data);
  for(float f:samples){if(encoding==3)b.putFloat(f);else if(bits==8)b.put((byte)Math.round(f*127+128));else if(bits==16)b.putShort((short)Math.round(f*32767));else if(bits==24){int v=Math.round(f*8388607);b.put((byte)v).put((byte)(v>>8)).put((byte)(v>>16));}else b.putInt((int)(f*2147483647.0));}return b.array();
 }
 static byte[] extensible(byte[] basic){
  byte[] result=new byte[basic.length+24];System.arraycopy(basic,0,result,0,36);System.arraycopy(basic,36,result,60,basic.length-36);
  ByteBuffer b=ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN);b.putInt(4,result.length-8);b.putInt(16,40);short kind=b.getShort(20);b.putShort(20,(short)65534);b.putShort(36,(short)22);b.putShort(38,b.getShort(34));b.putInt(40,b.getShort(22)==1?4:3);b.putInt(44,kind);
  byte[] tail={0,0,16,0,(byte)128,0,0,(byte)170,0,56,(byte)155,113};System.arraycopy(tail,0,result,48,tail.length);return result;
 }
 static byte[] metadata(byte[] basic){byte[] result=new byte[basic.length+12];System.arraycopy(basic,0,result,0,12);System.arraycopy(basic,12,result,24,basic.length-12);ByteBuffer b=ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN);b.putInt(4,result.length-8);result[12]='J';result[13]='U';result[14]='N';result[15]='K';b.putInt(16,3);result[20]=1;result[21]=2;result[22]=3;return result;}
 static void rejects(byte[] bytes,long budget,Class<? extends Exception> kind)throws Exception{try{WavReader.decode(new ByteArrayInputStream(bytes),"Test.wav",budget);throw new AssertionError("Expected rejection");}catch(Exception e){if(!kind.isInstance(e))throw e;}}
 public static void main(String[] args)throws Exception{
  for(int bits:new int[]{8,16,24,32}){PcmTrack track=WavReader.decode(new ByteArrayInputStream(wav(48000,1,1,bits,new float[]{.5f,-.5f,0})),"tone.wav",1000);check(Math.abs(track.sample(0,0)-.5)<.01,"PCM "+bits);check(Math.abs(track.sample(1,1)+.5)<.01,"mono duplication "+bits);}
  PcmTrack f=WavReader.decode(new ByteArrayInputStream(wav(48000,2,3,32,new float[]{.25f,-.5f,.5f,-.25f})),"stereo.wav",1000);check(f.sample(0,0)==.25f&&f.sample(0,1)==-.5f,"float stereo");
  byte[] basic=wav(48000,1,1,24,new float[]{.25f,-.25f});
  check(Math.abs(WavReader.decode(new ByteArrayInputStream(extensible(basic)),"extended.wav",1000).sample(0,0)-.25)<.001,"extensible PCM");
  check(Math.abs(WavReader.decode(new ByteArrayInputStream(metadata(basic)),"metadata.wav",1000).sample(1,0)+.25)<.001,"unknown chunk and padding");
  byte[] invalidGuid=extensible(basic);invalidGuid[48]=1;rejects(invalidGuid,1000,WavReader.Unsupported.class);
  byte[] source=wav(48000,1,1,16,new float[]{.2f,.3f});rejects(source,1,WavReader.TooLarge.class);rejects(Arrays.copyOf(source,source.length-1),1000,EOFException.class);
  rejects(wav(48000,3,1,16,new float[]{0,0,0}),1000,WavReader.Unsupported.class);rejects(wav(48000,1,3,32,new float[]{Float.NaN}),1000,WavReader.Unsupported.class);
  PcmTrack resample=new PcmTrack("24k",24000,1,new float[]{0,.5f,0,-.5f});check(Math.abs(resample.sample(1,0)-.25)<.001,"linear rate conversion");
  DemoMixer mixer=new DemoMixer();PcmTrack left=new PcmTrack("Left",48000,2,new float[]{.2f,0,.2f,0,.2f,0,.2f,0});PcmTrack right=new PcmTrack("Right",48000,2,new float[]{0,.4f,0,.4f,0,.4f,0,.4f});mixer.load(new PcmTrack[]{left,right});check(mixer.trackCount()==2&&mixer.hasImported(),"import model");mixer.play();float[] block=new float[8];mixer.render(block,4);check(block[0]>0&&block[1]>block[0],"real tracks mixed on one clock");mixer.render(block,4);check(!mixer.isPlaying(),"actual duration end");mixer.stop();mixer.seek(0);mixer.setLoop(true);mixer.play();mixer.render(block,4);mixer.render(block,4);check(mixer.isPlaying(),"real audio loop");
  float[] peaks=new float[4];f.addPeaks(peaks,f.duration());check(peaks[0]>0,"source waveform");
  System.out.println("PASS: WAV PCM/float, stereo/mono, resampling, budgets, truncation, invalid data, shared-clock real audio and loop");
 }
}
