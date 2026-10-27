package com.onebytelabs.zlplay;
import android.app.*;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import com.onebytelabs.zlplay.audio.*;
import java.lang.reflect.Field;

/** Device regression: a real Pad button click must send nonzero PCM to AudioTrack. */
public final class PadSmokeTest extends Instrumentation {
    private boolean queueFinal;private boolean manyTracks;private boolean remoteUi;private Bundle remoteArgs;private boolean cleanupMode;private boolean queueMode;private boolean routingMode;private boolean featuresMode;private boolean visualMode;private boolean interfaceMode;private boolean setlistMode;private boolean wavMode,mixerMode,projectMode,benchmarkMode;
    @Override public void onCreate(Bundle args){super.onCreate(args);queueFinal="queue-final".equals(args.getString("mode"));manyTracks="many-tracks".equals(args.getString("mode"));remoteUi="remote-ui".equals(args.getString("mode"));if("remote".equals(args.getString("mode")))remoteArgs=args;cleanupMode="cleanup".equals(args.getString("mode"));queueMode="queue".equals(args.getString("mode"));routingMode="routing".equals(args.getString("mode"));featuresMode="features".equals(args.getString("mode"));visualMode="visual".equals(args.getString("mode"));interfaceMode="interface".equals(args.getString("mode"));setlistMode="setlist".equals(args.getString("mode"));benchmarkMode="benchmark".equals(args.getString("mode"));projectMode="project".equals(args.getString("mode"));wavMode="wav".equals(args.getString("mode"));mixerMode="mixer".equals(args.getString("mode"));start();}
    private static Object field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private Button find(View view,String label){if(view instanceof Button&&label.contentEquals(((Button)view).getText()))return (Button)view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){Button b=find(group.getChildAt(i),label);if(b!=null)return b;}}return null;}
    @Override public void onStart(){if(queueFinal){try{finish(Activity.RESULT_OK,QueueFinalSmoke.run(this));}catch(Throwable e){Bundle failed=new Bundle();failed.putString("error",e.toString());finish(Activity.RESULT_CANCELED,failed);}return;}if(manyTracks){try{finish(Activity.RESULT_OK,ManyTracksSmoke.run(this));}catch(Throwable e){Bundle failed=new Bundle();failed.putString("error",e.toString());finish(Activity.RESULT_CANCELED,failed);}return;}if(remoteUi){try{finish(Activity.RESULT_OK,RemoteUiSmoke.run(this));}catch(Throwable e){Bundle failed=new Bundle();failed.putString("error",e.toString());finish(Activity.RESULT_CANCELED,failed);}return;}if(remoteArgs!=null){try{finish(Activity.RESULT_OK,RemoteSmoke.run(remoteArgs));}catch(Throwable e){Bundle failed=new Bundle();failed.putString("error",e.toString());finish(Activity.RESULT_CANCELED,failed);}return;}if(cleanupMode){long now=android.os.SystemClock.uptimeMillis();MotionEvent cancel=MotionEvent.obtain(now-1000,now,MotionEvent.ACTION_CANCEL,400,400,0);cancel.setSource(InputDevice.SOURCE_TOUCHSCREEN);getUiAutomation().injectInputEvent(cancel,true);cancel.recycle();Bundle done=new Bundle();done.putString("status","Cancelled unfinished test gesture");finish(Activity.RESULT_OK,done);return;}if(queueMode){try{finish(Activity.RESULT_OK,QueueSmoke.run(this));}catch(Throwable e){Bundle failure=new Bundle();failure.putString("error",e.toString());finish(Activity.RESULT_CANCELED,failure);}return;}if(routingMode){try{finish(Activity.RESULT_OK,RoutingSmoke.run(this));}catch(Throwable e){Bundle failure=new Bundle();failure.putString("error",e.toString());finish(Activity.RESULT_CANCELED,failure);}return;}if(featuresMode){try{finish(Activity.RESULT_OK,PlaybackFeaturesSmoke.run(this));}catch(Throwable e){Bundle failure=new Bundle();failure.putString("error",e.toString());finish(Activity.RESULT_CANCELED,failure);}return;}if(visualMode){try{finish(Activity.RESULT_OK,VisualReview.run(this));}catch(Throwable e){Bundle failure=new Bundle();failure.putString("error",e.toString());finish(Activity.RESULT_CANCELED,failure);}return;}if(interfaceMode){try{finish(Activity.RESULT_OK,InterfaceSmoke.run(this));}catch(Throwable e){Bundle failure=new Bundle();failure.putString("error",e.toString());finish(Activity.RESULT_CANCELED,failure);}return;}if(setlistMode){try{finish(Activity.RESULT_OK,SetlistSmoke.run(this));}catch(Throwable e){Bundle failure=new Bundle();failure.putString("error",e.toString());finish(Activity.RESULT_CANCELED,failure);}return;}if(benchmarkMode){try{finish(Activity.RESULT_OK,Mp3PerformanceSmoke.run(this));}catch(Throwable e){Bundle failure=new Bundle();failure.putString("error",e.toString());finish(Activity.RESULT_CANCELED,failure);}return;}if(projectMode){try{finish(Activity.RESULT_OK,ProjectSmoke.run(this));}catch(Throwable e){Bundle failure=new Bundle();failure.putString("error",e.toString());finish(Activity.RESULT_CANCELED,failure);}return;}if(mixerMode){try{finish(Activity.RESULT_OK,MixerGestureSmoke.run(this));}catch(Throwable e){Bundle failure=new Bundle();failure.putString("error",e.toString());finish(Activity.RESULT_CANCELED,failure);}return;}if(wavMode){try{finish(Activity.RESULT_OK,WavImportSmoke.run(this));}catch(Throwable e){Bundle failure=new Bundle();failure.putString("error",e.toString());finish(Activity.RESULT_CANCELED,failure);}return;}Bundle result=new Bundle();Activity activity=null;
        try {
            activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).putExtra("com.onebytelabs.zlplay.SKIP_RESTORE_TEST",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            final Activity screen=activity;final DemoMixer mixer=(DemoMixer)field(screen,"mixer");
            runOnMainSync(mixer::silence);
            runOnMainSync(()->{Button button=find(screen.findViewById(android.R.id.content),screen.getString(R.string.pad));if(button==null)throw new AssertionError("Pad button missing");button.performClick();});
            runOnMainSync(()->mixer.setPadLevel(.7f));
            Thread.sleep(1800);
            AudioSession audio=(AudioSession)field(screen,"audio");
            if(!mixer.isPad()||mixer.isPlaying()||mixer.isClick())throw new AssertionError("Pad must run alone");
            if(audio==null||audio.error()!=null||audio.writtenFrames()<48000||audio.blockRms()<.005)throw new AssertionError("No Pad PCM delivered to AudioTrack");
            result.putString("stream","Pad-only RMS="+audio.blockRms()+", writtenFrames="+audio.writtenFrames());
            runOnMainSync(()->mixer.setPadLevel(0));
            Thread.sleep(3000);
            if(audio.blockRms()>.001)throw new AssertionError("Pad volume zero did not silence output");
            runOnMainSync(()->mixer.setPadLevel(.7f));
            Thread.sleep(1800);
            if(audio.blockRms()<.005)throw new AssertionError("Pad volume restore failed");
            runOnMainSync(()->{Button button=find(screen.findViewById(android.R.id.content),screen.getString(R.string.pad));button.performClick();});
            Thread.sleep(3000);
            if(mixer.isPad()||audio.blockRms()>.001)throw new AssertionError("Pad stop did not fade to silence");
            result.putString("status","PASS: pad-only AudioTrack, volume zero/restore and fade-out");
            runOnMainSync(screen::finish);finish(Activity.RESULT_OK,result);
        }catch(Throwable e){result.putString("error",e.toString());if(activity!=null){Activity failed=activity;runOnMainSync(failed::finish);}finish(Activity.RESULT_CANCELED,result);}
    }
}
