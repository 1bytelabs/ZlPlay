package com.onebytelabs.zlplay.audio;

/** Immutable decoded source. Created on the import worker, never on the audio thread. */
public class PcmTrack implements AutoCloseable {
    public final String name;
    public final int sampleRate, channels;
    private final float[] samples;
    public PcmTrack(String name,int sampleRate,int channels,float[] samples){
        if(sampleRate<8000||sampleRate>192000||channels<1||channels>2||samples.length==0||samples.length%channels!=0)throw new IllegalArgumentException("Invalid PCM source");
        this.name=name;this.sampleRate=sampleRate;this.channels=channels;this.samples=samples;
    }
    protected PcmTrack(String name,int rate,int channels){this.name=name;this.sampleRate=rate;this.channels=channels;this.samples=null;}
    public void setPitch(int semitones){if(semitones!=0)throw new UnsupportedOperationException("Pitch source unavailable");}
    public boolean prepare(long frame,int count){return true;}
    public void hint(long frame){}
    /** Interleaved stereo, bounded quantum. Called only by the owner render/prefetch thread. */
    public void readStereo(long frame,int count,float[] output,int offset){
        for(int i=0;i<count;i++){output[offset+i*2]=sample(frame+i,0);output[offset+i*2+1]=sample(frame+i,1);}
    }
    public float previewPeak(double seconds){int frame=(int)(seconds*sampleRate);if(frame<0||frame>=samples.length/channels)return 0;float value=0;for(int f=frame;f<Math.min(frame+32,samples.length/channels);f++)for(int c=0;c<channels;c++)value=Math.max(value,Math.abs(samples[f*channels+c]));return value;}
    public boolean failed(){return false;}
    @Override public void close(){}
    public long decodedBytes(){return samples.length*4L;}
    public void addPeaks(float[] peaks,double duration){
        int last=-1;float high=0;for(int frame=0;frame<samples.length/channels;frame++){
            int bin=Math.min(peaks.length-1,(int)(frame/(double)sampleRate/duration*peaks.length));
            if(bin!=last){if(last>=0)peaks[last]+=high*.7f;last=bin;high=0;}
            for(int c=0;c<channels;c++)high=Math.max(high,Math.abs(samples[frame*channels+c]));
        }if(last>=0)peaks[last]+=high*.7f;
    }
    public double duration(){return samples.length/(double)channels/sampleRate;}
    public float sample(long outputFrame,int channel){
        double at=outputFrame*(sampleRate/(double)DemoMixer.RATE);
        int frame=(int)at,count=samples.length/channels;if(frame<0||frame>=count)return 0;
        int c=channels==1?0:channel;float a=samples[frame*channels+c],b=samples[Math.min(frame+1,count-1)*channels+c];
        return a+(b-a)*(float)(at-frame);
    }
}
