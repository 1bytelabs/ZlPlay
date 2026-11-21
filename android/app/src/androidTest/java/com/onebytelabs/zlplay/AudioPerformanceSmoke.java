package com.onebytelabs.zlplay;

import android.app.*;
import android.content.Intent;
import android.os.*;
import com.onebytelabs.zlplay.audio.*;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.*;
import java.util.*;
import org.json.*;

/** Diagnostic comparison with actual AudioTrack writes; no microphone or user audio. */
final class AudioPerformanceSmoke {
    private static Object field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private static File fixture(Activity context)throws Exception{
        File file=new File(context.getCacheDir(),"audio-performance-synthetic.wav");
        int seconds=32,rate=48000,bytes=seconds*rate*4;
        ByteBuffer header=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes()).putInt(bytes+36).put("WAVEfmt ".getBytes()).putInt(16).putShort((short)1).putShort((short)2).putInt(rate).putInt(rate*4).putShort((short)4).putShort((short)16).put("data".getBytes()).putInt(bytes);
        byte[] block=new byte[rate*4];ByteBuffer pcm=ByteBuffer.wrap(block).order(ByteOrder.LITTLE_ENDIAN);
        for(int i=0;i<rate;i++){short value=(short)(1800*Math.sin(2*Math.PI*220*i/rate));pcm.putShort(value).putShort(value);}
        try(OutputStream out=new BufferedOutputStream(new FileOutputStream(file))){out.write(header.array());for(int i=0;i<seconds;i++)out.write(block);}
        return file;
    }
    private static StreamBank stream(File file,int count)throws Exception{
        List<WavFile> files=new ArrayList<>();List<float[]> peaks=new ArrayList<>();
        for(int i=0;i<count;i++){FileInputStream in=new FileInputStream(file);files.add(new WavFile("Instrument "+i,in.getChannel(),in,0,file.length()));peaks.add(new float[]{.05f});}
        return new StreamBank(files,peaks);
    }
    private static JSONObject phase(Instrumentation test,DemoMixer mixer,AudioSession audio,String name)throws Exception{
        return phase(test,mixer,audio,name,40);
    }
    private static JSONObject phase(Instrumentation test,DemoMixer mixer,AudioSession audio,String name,int polls)throws Exception{
        long frames=audio.writtenFrames(),renders=audio.renderCalls(),render=audio.renderNanos(),writes=audio.writeCalls(),write=audio.writeNanos();
        int underruns=audio.underruns(),minQueue=Integer.MAX_VALUE;long source=mixer.sourceStarvationFrames(),pad=mixer.padStarvationFrames();
        long sourceWait=audio.sourceWaitNanos();
        long start=SystemClock.elapsedRealtime();double position=mixer.position(),minRms=1;int buffering=0;
        for(int i=0;i<polls;i++){test.runOnMainSync(()->mixer.setMasterLevel(.6f));Thread.sleep(100);minQueue=Math.min(minQueue,audio.queuedFrames());minRms=Math.min(minRms,audio.blockRms());if(mixer.isBuffering())buffering++;}
        if(audio.error()!=null||audio.writtenFrames()==frames||mixer.sourceFailed())throw new AssertionError("Audio failed: "+name+" "+audio.error());
        JSONObject measurement=new JSONObject().put("phase",name).put("wallMs",SystemClock.elapsedRealtime()-start).put("positionSeconds",mixer.position()-position)
            .put("outputFrames",audio.writtenFrames()-frames).put("underruns",audio.underruns()-underruns).put("minQueueFrames",minQueue).put("bufferFrames",audio.bufferFrames())
            .put("meanRenderMs",(audio.renderNanos()-render)/1e6/Math.max(1,audio.renderCalls()-renders)).put("maxRenderMs",audio.maxRenderNanos()/1e6).put("maxLockWaitMs",audio.maxMixerWaitNanos()/1e6)
            .put("meanWriteMs",(audio.writeNanos()-write)/1e6/Math.max(1,audio.writeCalls()-writes)).put("sourceStarvedFrames",mixer.sourceStarvationFrames()-source)
            .put("padStarvedFrames",mixer.padStarvationFrames()-pad).put("bufferingPolls",buffering).put("minBlockRms",minRms);
        measurement.put("sourceWaitMs",(audio.sourceWaitNanos()-sourceWait)/1e6).put("maxSourceWaitMs",audio.maxSourceWaitNanos()/1e6);
        Bundle update=new Bundle();update.putString("phase",measurement.toString());test.sendStatus(0,update);return measurement;
    }
    static Bundle run(Instrumentation test,Bundle options)throws Exception{
        Activity screen=test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).putExtra("com.onebytelabs.zlplay.SKIP_RESTORE_TEST",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        File file=null;StreamBank pads=null;JSONArray results=new JSONArray();
        try{
            DemoMixer mixer=(DemoMixer)field(screen,"mixer");
            java.lang.reflect.Method ready=MainActivity.class.getDeclaredMethod("ready");ready.setAccessible(true);
            java.lang.reflect.Method renderMixer=MainActivity.class.getDeclaredMethod("renderMixer");renderMixer.setAccessible(true);
            test.runOnMainSync(()->{try{if(!((Boolean)ready.invoke(screen)))throw new AssertionError("Audio focus unavailable");}catch(ReflectiveOperationException e){throw new RuntimeException(e);}});
            AudioSession audio=(AudioSession)field(screen,"audio");
            test.runOnMainSync(mixer::silence);Thread.sleep(1000);file=fixture(screen);pads=stream(file,12);PcmTrack[] padTracks=pads.tracks;
            if("true".equals(options.getString("padOnly"))){
                int polls=Math.max(400,Math.min(3000,Integer.parseInt(options.getString("seconds","90"))*5));
                test.runOnMainSync(()->{mixer.replacePads(padTracks);mixer.setPad(true);});Thread.sleep(1000);
                results.put(phase(test,mixer,audio,"pad-long-loop",polls));
                test.runOnMainSync(()->mixer.setPadNote(5));results.put(phase(test,mixer,audio,"pad-long-note-change",polls));
                Bundle result=new Bundle();result.putString("status","DIAGNOSTIC: continuous streamed pad including file wraps and note change");result.putString("measurements",results.toString());return result;
            }
            test.runOnMainSync(()->{mixer.replacePads(padTracks);mixer.setPad(true);});Thread.sleep(1000);results.put(phase(test,mixer,audio,"pad-only"));
            test.runOnMainSync(()->{mixer.setPadNote(5);});results.put(phase(test,mixer,audio,"pad-note-change"));
            test.runOnMainSync(()->{mixer.setPad(false);mixer.setClick(true);});Thread.sleep(1500);results.put(phase(test,mixer,audio,"click-only"));
            test.runOnMainSync(mixer::silence);
            for(int count:new int[]{2,10}){
                StreamBank bank=stream(file,count);PitchBank pitch=null;
                try{
                    pitch=new PitchBank(bank.tracks,mixer);PcmTrack[] tracks=pitch.tracks;
                    test.runOnMainSync(()->{mixer.load(tracks);for(int i=0;i<count;i++)mixer.setGain(i,.12f);try{renderMixer.invoke(screen);}catch(ReflectiveOperationException e){throw new RuntimeException(e);}mixer.play();});
                    results.put(phase(test,mixer,audio,count+"-original"));
                    test.runOnMainSync(()->mixer.setTranspose(3));results.put(phase(test,mixer,audio,count+"-pitch-up"));
                    test.runOnMainSync(()->mixer.setTranspose(-4));results.put(phase(test,mixer,audio,count+"-pitch-down"));
                    test.runOnMainSync(()->{mixer.pause();mixer.seek(0);mixer.setTranspose(5);});Thread.sleep(1000);test.runOnMainSync(mixer::play);
                    results.put(phase(test,mixer,audio,count+"-pitch-after-pause"));
                    test.runOnMainSync(()->{mixer.stop();mixer.closeImported();});
                }finally{if(pitch!=null)pitch.close();bank.close();}
            }
            Bundle result=new Bundle();result.putString("status","DIAGNOSTIC: real output, pad/click, 2/10 stems and live/stopped pitch changes");result.putString("measurements",results.toString());return result;
        }finally{test.runOnMainSync(screen::finish);Thread.sleep(200);if(pads!=null)pads.close();if(file!=null)file.delete();}
    }
}
