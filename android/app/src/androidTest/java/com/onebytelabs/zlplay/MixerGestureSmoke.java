package com.onebytelabs.zlplay;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.lang.reflect.Field;
import com.onebytelabs.zlplay.audio.*;

/** Real touchscreen drag through the phone mixer window; restores the original gain. */
public final class MixerGestureSmoke {
 private static Object field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
 private static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);}
 private static Button button(View v,String label){if(v instanceof Button&&label.contentEquals(((Button)v).getText()))return (Button)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){Button b=button(g.getChildAt(i),label);if(b!=null)return b;}}return null;}
 private static SeekBar fader(View v){if(v instanceof SeekBar)return (SeekBar)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){SeekBar b=fader(g.getChildAt(i));if(b!=null)return b;}}return null;}
 private static void touch(Instrumentation test,long down,int action,float x,float y){MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);event.setSource(InputDevice.SOURCE_TOUCHSCREEN);test.sendPointerSync(event);event.recycle();}
 public static Bundle run(Instrumentation test)throws Exception{
  KeyguardManager lock=(KeyguardManager)test.getTargetContext().getSystemService(Context.KEYGUARD_SERVICE);check(!lock.isDeviceLocked(),"Unlock the phone before mixer tests");
  MainActivity screen=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));SeekBar[] slider={null};int[] original={-1};
  try{
   for(int i=0;i<100;i++){boolean[] loading={true};test.runOnMainSync(()->{try{loading[0]=(Boolean)field(screen,"loading");}catch(Exception e){throw new RuntimeException(e);}});if(!loading[0])break;Thread.sleep(100);}
   DemoMixer mixer=(DemoMixer)field(screen,"mixer");test.runOnMainSync(()->button(screen.findViewById(android.R.id.content),screen.getString(R.string.pad)).performClick());
   Thread.sleep(300);test.runOnMainSync(()->button(screen.findViewById(android.R.id.content),screen.getString(R.string.mixer)).performClick());test.waitForIdleSync();
   Dialog dialog=(Dialog)field(screen,"mixerDialog");check(dialog!=null&&dialog.isShowing(),"Phone mixer is a separate window");
   LinearLayout panel=(LinearLayout)field(screen,"mixerPanel");check(panel.getRootView()!=screen.findViewById(android.R.id.content).getRootView(),"Mixer removed from transport scroll");
   int[] where=new int[2],size=new int[2];test.runOnMainSync(()->{slider[0]=fader(dialog.getWindow().getDecorView());original[0]=slider[0].getProgress();slider[0].setProgress(20);View area=(View)slider[0].getParent();area.getLocationOnScreen(where);size[0]=area.getWidth();size[1]=area.getHeight();});
   float x=where[0]+size[0]/2f,start=where[1]+size[1]*.8f,end=where[1]+size[1]*.2f;long down=SystemClock.uptimeMillis();touch(test,down,MotionEvent.ACTION_DOWN,x,start);
   for(int i=1;i<=12;i++){Thread.sleep(16);touch(test,down,MotionEvent.ACTION_MOVE,x,start+(end-start)*i/12);}
   touch(test,down,MotionEvent.ACTION_UP,x,end);test.waitForIdleSync();int[] changed={0};test.runOnMainSync(()->changed[0]=slider[0].getProgress());check(changed[0]>=60,"Fader changed continuously during a real drag");check(mixer.getGain(0)>=.6f,"Drag reached mixer gain");check(mixer.isPad(),"Audio continues while mixer is open");
   test.runOnMainSync(()->{slider[0].setProgress(original[0]);button(dialog.getWindow().getDecorView(),screen.getString(R.string.back)).performClick();});test.waitForIdleSync();
   check(!dialog.isShowing()&&panel.getVisibility()==View.GONE,"Back returns to transport and hides inline phone mixer");check(mixer.isPad(),"Back does not stop audio");Bundle result=new Bundle();result.putString("status","PASS: separate phone mixer, real fader drag to "+changed[0]+"%, gain delivery, audio continuity and Back");return result;
  }finally{test.runOnMainSync(()->{if(slider[0]!=null&&original[0]>=0)slider[0].setProgress(original[0]);try{((DemoMixer)field(screen,"mixer")).silence();}catch(Exception ignored){}screen.finish();});}
 }
}
