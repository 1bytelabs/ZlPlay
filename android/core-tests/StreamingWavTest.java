import com.onebytelabs.zlplay.audio.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
public final class StreamingWavTest {
 private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 private static void create(File file)throws Exception{
  int frames=48000*600,bytes=frames*4;ByteBuffer h=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
  h.put("RIFF".getBytes("US-ASCII")).putInt(36+bytes).put("WAVEfmt ".getBytes("US-ASCII")).putInt(16).putShort((short)1).putShort((short)2).putInt(48000).putInt(192000).putShort((short)4).putShort((short)16).put("data".getBytes("US-ASCII")).putInt(bytes);
  byte[] block=new byte[StreamBank.BLOCK_FRAMES*4];ByteBuffer samples=ByteBuffer.wrap(block).order(ByteOrder.LITTLE_ENDIAN);while(samples.hasRemaining())samples.putShort((short)16384).putShort((short)-8192);
  try(RandomAccessFile out=new RandomAccessFile(file,"rw")){out.write(h.array());out.setLength(44L+bytes);for(int key:new int[]{0,1,2,3,4,300,301,302,303,304,3514,3515}){long at=(long)key*StreamBank.BLOCK_FRAMES*4;if(at>=bytes)continue;out.seek(44+at);out.write(block,0,(int)Math.min(block.length,bytes-at));}}
 }
 private static void ready(PcmTrack track,long frame)throws Exception{for(int i=0;i<600;i++){if(track.prepare(frame,512))return;if(track.failed())throw new AssertionError("Source failure");Thread.sleep(5);}throw new AssertionError("Prefetch timed out");}
 public static void main(String[] args)throws Exception{
  File file=File.createTempFile("zlplay-stream-", ".wav");StreamBank bank=null;
  try{
   create(file);FileChannel channel=FileChannel.open(file.toPath(),StandardOpenOption.READ);WavFile source=new WavFile("10 minute fixture",channel,channel,0,channel.size());
   float[] preview=source.preview();bank=new StreamBank(Collections.singletonList(source),Collections.singletonList(preview));PcmTrack track=bank.tracks[0];
   check(file.length()>100L*1024*1024&&Math.abs(track.duration()-600)<.001,"long source opened");check(track.decodedBytes()<1024*1024,"cache independent of source duration");
   DemoMixer mixer=new DemoMixer();mixer.load(bank.tracks);mixer.play();float[] pcm=new float[1024];mixer.render(pcm,512);check(pcm[0]>0&&pcm[1]<0,"prefilled intro");
   long frame=300L*StreamBank.BLOCK_FRAMES+123;mixer.seek(frame/(double)48000);mixer.render(pcm,512);check(mixer.position()*48000>=frame,"seek does not move backwards");ready(track,frame);mixer.seek(frame/(double)48000);mixer.render(pcm,512);check(pcm[0]>.1&&pcm[1]<-.05,"seeked PCM");
   mixer.pause();double paused=mixer.position();mixer.render(pcm,512);check(mixer.position()==paused,"stream pause");
   long tail=48000L*600-100;ready(track,tail);mixer.seek(tail/(double)48000);mixer.setLoop(true);mixer.play();mixer.render(pcm,512);check(mixer.position()<.02&&mixer.isPlaying(),"loop reaches prefilled start without reading in render");
   mixer.setLoop(false);mixer.seek(tail/(double)48000);mixer.render(pcm,512);check(!mixer.isPlaying()&&mixer.position()==600,"actual file end");
   source.close();mixer.seek(200);mixer.play();for(int i=0;i<300&&!mixer.sourceFailed();i++){mixer.render(pcm,512);Thread.sleep(5);}mixer.render(pcm,512);check(mixer.sourceFailed()&&!mixer.isPlaying(),"provider loss stops playback");mixer.closeImported();
   System.out.println("PASS: 10-minute WAV under 48 MiB JVM heap, bounded cache, prefetch/seek, pause, loop, EOF and source failure");
  }finally{if(bank!=null)bank.close();Files.deleteIfExists(file.toPath());}
 }
}
