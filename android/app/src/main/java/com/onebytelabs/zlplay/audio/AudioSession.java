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
    private Thread worker;
    private volatile String error;
    public AudioSession(Context context,DemoMixer mixer) {
        this.mixer=mixer;
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
        running=true;error=null;
        worker=new Thread(this::stream,"ZLPlay-Audio");worker.start();return true;
    }
    public String error() { return error; }
    private void stream() {
        AudioTrack output=null;
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
            int minimum=AudioTrack.getMinBufferSize(DemoMixer.RATE,AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_FLOAT);
            if(minimum<=0)throw new IllegalStateException("Audio format unavailable");
            output=new AudioTrack.Builder().setAudioAttributes(attributes())
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(DemoMixer.RATE).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).build())
                .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(Math.max(minimum*2,4096))
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY).build();
            if(output.getState()!=AudioTrack.STATE_INITIALIZED)throw new IllegalStateException("Audio initialization failed");
            float[] block=new float[1024];output.play();
            while(running) {
                mixer.render(block,512);int offset=0;
                while(running&&offset<block.length) {
                    int written=output.write(block,offset,block.length-offset,AudioTrack.WRITE_BLOCKING);
                    if(written<=0)throw new IllegalStateException("Audio write failed: "+written);
                    offset+=written;
                }
            }
        } catch(Exception failure) { error=failure.getClass().getSimpleName();mixer.silence(); }
        finally { running=false;if(output!=null){try{output.pause();output.flush();}catch(Exception ignored){}output.release();} }
    }
    @Override public synchronized void close() {
        running=false;mixer.silence();manager.abandonAudioFocusRequest(focus);
        if(worker!=null)try{worker.join(1000);}catch(InterruptedException e){Thread.currentThread().interrupt();}
    }
}
