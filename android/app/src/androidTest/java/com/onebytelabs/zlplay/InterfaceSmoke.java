package com.onebytelabs.zlplay;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.onebytelabs.zlplay.audio.*;
import java.lang.reflect.*;

/** Navigation and independent transport regression for the responsive performance UI. */
public final class InterfaceSmoke {
 private static Object field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
 private static void call(Object target,String name,Class<?>[] types,Object... args){try{Method m=target.getClass().getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(target,args);}catch(Exception e){throw new RuntimeException(e);}}
 private static Button find(View view,String text){if(view instanceof Button&&text.contentEquals(((Button)view).getText()))return(Button)view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){Button b=find(group.getChildAt(i),text);if(b!=null)return b;}}return null;}
 private static View described(View view,String text){if(view.getContentDescription()!=null&&text.contentEquals(view.getContentDescription()))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=described(group.getChildAt(i),text);if(found!=null)return found;}}return null;}
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static Bundle run(Instrumentation test)throws Exception{
  MainActivity screen=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).putExtra("com.onebytelabs.zlplay.SKIP_RESTORE_TEST",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  try{test.waitForIdleSync();DemoMixer mixer=(DemoMixer)field(screen,"mixer");View root=screen.findViewById(android.R.id.content);View wave=(View)field(screen,"waveform");float density=screen.getResources().getDisplayMetrics().density;check(wave.getHeight()>=80*density,"Waveform retains usable height");check(((TextView)field(screen,"nextSong")).getParent()==null,"No duplicate next-song label in performance view");check(((TextView)field(screen,"clock")).getParent()==((Button)field(screen,"pad")).getParent(),"Time is in transport beside Tap and Mixer");
   test.runOnMainSync(()->{find(root,screen.getString(R.string.pad)).performClick();find(root,screen.getString(R.string.solo_click)).performClick();});Thread.sleep(200);check(mixer.isPad()&&mixer.isClick()&&!mixer.isPlaying(),"Auxiliary buttons start independently");
   test.runOnMainSync(()->find(root,screen.getString(R.string.play)).performClick());Thread.sleep(200);check(mixer.isPlaying()&&!mixer.isClick(),"Play leaves standalone click mode");test.runOnMainSync(()->find(root,screen.getString(R.string.solo_click)).performClick());check(mixer.isClick()&&mixer.isLoop(),"Solo Click loops and solos the current section");test.runOnMainSync(()->find(root,screen.getString(R.string.pause)).performClick());check(!mixer.isPlaying()&&mixer.isPad()&&mixer.isClick(),"Pause preserves independent Pad/Click");
   test.runOnMainSync(()->{find(root,screen.getString(R.string.pad)).performClick();find(root,screen.getString(R.string.solo_click)).performClick();});check(!mixer.isPad()&&!mixer.isClick(),"Second presses deactivate auxiliaries");
   for(int tab=0;tab<3;tab++){final int index=tab;test.runOnMainSync(()->call(screen,"showSettings",new Class<?>[]{int.class},index));test.waitForIdleSync();Dialog dialog=(Dialog)field(screen,"settingsDialog");check(dialog.isShowing(),"Settings tab opens "+tab);check(find(dialog.getWindow().getDecorView(),screen.getString(R.string.back))!=null,"Settings Back available");test.runOnMainSync(dialog::dismiss);}
   test.runOnMainSync(()->call(screen,"showRepertoire",new Class<?>[]{}));test.waitForIdleSync();Dialog setlist=(Dialog)field(screen,"repertoireDialog");check(find(setlist.getWindow().getDecorView(),screen.getString(R.string.import_setlist))!=null,"Setlist import available");check(find(setlist.getWindow().getDecorView(),screen.getString(R.string.open_project))!=null,"Project import available");test.runOnMainSync(setlist::dismiss);
   PcmTrack[] bank=new PcmTrack[12];for(int note=0;note<12;note++){float[] samples=new float[4800];java.util.Arrays.fill(samples,.1f);bank[note]=new PcmTrack(com.onebytelabs.zlplay.project.MusicalKey.FLATS[note],48000,1,samples);}test.runOnMainSync(()->{mixer.replacePads(bank);find(root,screen.getString(R.string.play)).performClick();});Thread.sleep(180);check(mixer.isPlaying()&&mixer.isPad()&&mixer.meter(-4)>0,"Play automatically starts audible imported pad");test.runOnMainSync(()->call(screen,"showSettings",new Class<?>[]{int.class},2));Dialog sounds=(Dialog)field(screen,"settingsDialog");for(String note:com.onebytelabs.zlplay.project.MusicalKey.FLATS){View chip=described(sounds.getWindow().getDecorView(),screen.getString(R.string.pad_note_status,note,screen.getString(R.string.pad_imported)));check(chip instanceof TextView&&!(chip instanceof Button)&&!chip.isClickable()&&!chip.isFocusable(),"Read-only imported pad indicator "+note);}test.runOnMainSync(sounds::dismiss);
   Bundle result=new Bundle();result.putString("status","PASS: usable waveform, Play/Pause, independent Pad/Click toggles, three settings categories, repertoire navigation, Play starts imported pad and 12 read-only note indicators");return result;
  }finally{test.runOnMainSync(()->screen.finish());}
 }
}
