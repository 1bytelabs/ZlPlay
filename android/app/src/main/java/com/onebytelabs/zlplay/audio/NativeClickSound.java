package com.onebytelabs.zlplay.audio;
import java.io.*;
/** Desktop Meuclick.wav and its default accent factors, shared by both click paths. */
public final class NativeClickSound {
 private static volatile PcmTrack wave;
 public static void load(InputStream input)throws IOException{wave=WavReader.decode(input,"Meuclick.wav",2*1024*1024);}
 public static float sample(long frame,double beat,int meter,int channel){PcmTrack sound=wave;long latest=(long)Math.floor(frame/beat);if(sound==null){double within=frame%beat;return(float)(.13*Math.sin(2*Math.PI*(latest%meter==0?1800:1200)*frame/DemoMixer.RATE)*Math.exp(-within/160));}float sum=0;int tails=(int)Math.ceil(sound.duration()*DemoMixer.RATE/beat);for(int back=0;back<=tails;back++){long index=latest-back;if(index<0)break;double since=frame-index*beat;boolean accent=index%meter==0;sum+=sound.sample(Math.round(since*(accent?1.16:1)),channel)*(accent?1.3f:1);}return sum;}
}
