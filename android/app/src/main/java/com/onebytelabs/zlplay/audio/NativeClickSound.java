package com.onebytelabs.zlplay.audio;
import java.io.*;
/** Desktop Meuclick.wav and its default accent factors, shared by both click paths. */
public final class NativeClickSound {
 private static volatile PcmTrack wave,customNormal,customAccent;
 public static void load(InputStream input)throws IOException{wave=WavReader.decode(input,"Meuclick.wav",2*1024*1024);}
 public static PcmTrack decodeCustom(InputStream input)throws IOException{PcmTrack sound=WavReader.decode(input,"Custom click",8*1024*1024);if(sound.duration()>5)throw new IOException("click-duration");return sound;}
 public static void installCustom(PcmTrack sound,boolean accent){if(accent)customAccent=sound;else customNormal=sound;}
 public static void loadCustom(InputStream input,boolean accent)throws IOException{installCustom(decodeCustom(input),accent);}
 public static void restoreDefaults(){customNormal=null;customAccent=null;}
 public static float sample(long frame,double beat,int meter,int channel){return sample(frame,beat,meter,channel,true);}
 public static float sample(long frame,double beat,int meter,int channel,boolean accents){PcmTrack sound=wave;long latest=(long)Math.floor(frame/beat);if(sound==null){double within=frame%beat;return(float)(.13*Math.sin(2*Math.PI*(accents&&latest%meter==0?1800:1200)*frame/DemoMixer.RATE)*Math.exp(-within/160));}float sum=0;double tail=Math.max(sound.duration(),Math.max(customNormal==null?0:customNormal.duration(),customAccent==null?0:customAccent.duration()));int tails=(int)Math.ceil(tail*DemoMixer.RATE/beat);for(int back=0;back<=tails;back++){long index=latest-back;if(index<0)break;double since=frame-index*beat;boolean accent=accents&&index%meter==0;PcmTrack selected=accent?customAccent:customNormal;sum+=(selected==null?sound.sample(Math.round(since*(accent?1.16:1)),channel)*(accent?1.3f:1):selected.sample(Math.round(since),channel));}return sum;}
}
