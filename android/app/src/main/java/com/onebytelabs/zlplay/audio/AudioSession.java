package com.onebytelabs.zlplay.audio;

import android.content.Context;
import android.media.*;
import android.os.Process;

/** One output clock for all stems. This is a foreground-only audio proof of concept. */
public final class AudioSession implements AutoCloseable {
    private final DemoMixer mixer;
    private final AudioManager manager;
    private final AudioFocusRequest focus;
    private volatile boolean running;
    private Thread worker;private AudioDeviceCallback deviceCallback;
    private final int outputRate,outputChannels,deviceId;
    private volatile String error;
    private volatile long writtenFrames;
    private volatile double blockRms;
    private volatile int underruns;private volatile long maxRenderNanos;
    public int underruns(){return underruns;}
    public long maxRenderNanos(){return maxRenderNanos;}
    public AudioSession(Context context,DemoMixer mixer){this(context,mixer,configuredDevice(context),context.getSharedPreferences("mix",Context.MODE_PRIVATE).getInt("outputRate",48000),context.getSharedPreferences("mix",Context.MODE_PRIVATE).getInt("outputChannels",2));}
    public AudioSession(Context context,DemoMixer mixer,int device,int rate,int channels) {
        this.mixer=mixer;outputRate=rate;outputChannels=channels;deviceId=device;
        manager=(AudioManager)context.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes()).setOnAudioFocusChangeListener(change->{
                if(change<0)mixer.silence();
            }).build();
    }
    private static AudioAttributes attributes() { return new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(); }
    public synchronized boolean start() {
        if(manager.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED)return false;
        if(running)return true;
        if(worker!=null&&worker.isAlive())return false;
        running=true;error=null;if(deviceCallback!=null){manager.unregisterAudioDeviceCallback(deviceCallback);deviceCallback=null;}if(deviceId!=0){deviceCallback=new AudioDeviceCallback(){@Override public void onAudioDevicesRemoved(AudioDeviceInfo[] removed){for(AudioDeviceInfo d:removed)if(d.getId()==deviceId){error="Device disconnected";running=false;mixer.silence();}}};manager.registerAudioDeviceCallback(deviceCallback,new android.os.Handler(android.os.Looper.getMainLooper()));}
        worker=new Thread(this::stream,"ZLPlay-Audio");worker.start();return true;
    }
    public static int configuredDevice(Context context){var prefs=context.getSharedPreferences("mix",Context.MODE_PRIVATE);int id=prefs.getInt("outputDevice",0);if(id==0)return 0;String name=prefs.getString("outputDeviceName","");int type=prefs.getInt("outputDeviceType",0);AudioManager manager=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);AudioDeviceInfo found=device(manager,id);if(found!=null&&(name.isEmpty()||name.contentEquals(found.getProductName())&&type==found.getType()))return id;int match=-1;for(AudioDeviceInfo d:manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS))if(!name.isEmpty()&&name.contentEquals(d.getProductName())&&type==d.getType()){if(match!=-1)return -1;match=d.getId();}return match;}
    public static AudioDeviceInfo device(AudioManager manager,int id){for(AudioDeviceInfo d:manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS))if(d.getId()==id)return d;return null;}
    public static int[] rates(AudioDeviceInfo device){int[] reported=device==null?new int[0]:device.getSampleRates();java.util.ArrayList<Integer> values=new java.util.ArrayList<>();for(int rate:new int[]{44100,48000,88200,96000}){boolean supported=reported.length==0;for(int r:reported)supported|=rate==r;if(supported)values.add(rate);}return values.stream().mapToInt(Integer::intValue).toArray();}
    public static int[] channels(AudioDeviceInfo device){int[] counts=device==null?new int[0]:device.getChannelCounts();if(counts.length==0)return new int[]{2};return java.util.Arrays.stream(counts).filter(c->c>=1&&c<=16).distinct().sorted().toArray();}
    private static AudioTrack open(AudioManager manager,int id,int rate,int channels){if(rate<32000||rate>96000||channels<1||channels>16)throw new IllegalArgumentException("Output format");AudioDeviceInfo target=id==0?null:device(manager,id);if(id!=0&&target==null)throw new IllegalStateException("Device disconnected");AudioFormat.Builder format=new AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_FLOAT);if(channels==2)format.setChannelMask(AudioFormat.CHANNEL_OUT_STEREO);else if(channels==1)format.setChannelMask(AudioFormat.CHANNEL_OUT_MONO);else format.setChannelIndexMask((1<<channels)-1);AudioTrack out=new AudioTrack.Builder().setAudioAttributes(attributes()).setAudioFormat(format.build()).setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(Math.max(channels<=2?Math.max(0,AudioTrack.getMinBufferSize(rate,channels==1?AudioFormat.CHANNEL_OUT_MONO:AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_FLOAT))*2:0,2048*channels*4)).setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY).build();if(out.getState()!=AudioTrack.STATE_INITIALIZED){out.release();throw new IllegalStateException("Audio initialization failed");}if(target!=null&&!out.setPreferredDevice(target)){out.release();throw new IllegalStateException("Device selection rejected");}return out;}
    public static void validate(Context context,int id,int rate,int channels){AudioManager manager=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);AudioTrack probe=open(manager,id,rate,channels);probe.release();}
    public String error() { return error; }
    public long writtenFrames() { return writtenFrames; }
    public double blockRms() { return blockRms; }
    private void renderQuantum(float[] block){for(int offset=0;offset<512;offset+=256)mixer.renderRoutedInto(block,offset,256,outputChannels);}
    private void stream() {
        AudioTrack output=null;
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
            output=open(manager,deviceId,outputRate,outputChannels);
            if(output.getState()!=AudioTrack.STATE_INITIALIZED)throw new IllegalStateException("Audio initialization failed");
            float[] block=new float[512*outputChannels],input=new float[512*outputChannels],previous=new float[outputChannels],next=new float[outputChannels];int inputFrame=512;double fraction=0;boolean primed=false,outputStarted=false;int primedBlocks=0;
            while(running) {
                long renderStart=System.nanoTime();if(outputRate==DemoMixer.RATE)renderQuantum(block);else{for(int f=0;f<512;f++){if(!primed){renderQuantum(input);inputFrame=2;System.arraycopy(input,0,previous,0,outputChannels);System.arraycopy(input,outputChannels,next,0,outputChannels);primed=true;}for(int c=0;c<outputChannels;c++)block[f*outputChannels+c]=(float)(previous[c]+(next[c]-previous[c])*fraction);fraction+=DemoMixer.RATE/(double)outputRate;while(fraction>=1){fraction-=1;System.arraycopy(next,0,previous,0,outputChannels);if(inputFrame>=512){renderQuantum(input);inputFrame=0;}System.arraycopy(input,inputFrame++*outputChannels,next,0,outputChannels);}}}maxRenderNanos=Math.max(maxRenderNanos,System.nanoTime()-renderStart);underruns=output.getUnderrunCount();double energy=0;for(float sample:block)energy+=(double)sample*sample;blockRms=Math.sqrt(energy/block.length);int offset=0;
                while(running&&offset<block.length) {
                    int written=output.write(block,offset,block.length-offset,AudioTrack.WRITE_BLOCKING);
                    if(written<=0)throw new IllegalStateException("Audio write failed: "+written);
                    offset+=written;writtenFrames+=written/outputChannels;
                }
                // Fill the hardware queue before starting it; startup/JIT must not drain an empty track.
                if(!outputStarted&&++primedBlocks>=3){output.play();outputStarted=true;if(deviceId!=0){AudioDeviceInfo routed=output.getRoutedDevice();if(routed!=null&&routed.getId()!=deviceId)throw new IllegalStateException("Requested device was not routed");}}
            }
        } catch(Exception failure) { error=failure.getClass().getSimpleName();mixer.silence(); }
        finally { running=false;if(output!=null){try{output.pause();output.flush();}catch(Exception ignored){}output.release();} }
    }
    @Override public synchronized void close() {
        running=false;mixer.silence();if(deviceCallback!=null){manager.unregisterAudioDeviceCallback(deviceCallback);deviceCallback=null;}manager.abandonAudioFocusRequest(focus);
        if(worker!=null)try{worker.join(1000);}catch(InterruptedException e){Thread.currentThread().interrupt();}
    }
}
