package com.onebytelabs.zlplay;
import android.app.*;import android.content.*;import android.os.*;import android.webkit.*;import android.view.*;import java.lang.reflect.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;import java.io.*;

public final class RemoteUiSmoke {
 public static Bundle run(Instrumentation test)throws Exception {
  Activity screen=test.startActivitySync(new Intent(test.getTargetContext(),RemoteActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  try {
   test.runOnMainSync(()->{if(hasEdit(screen.findViewById(android.R.id.content)))throw new AssertionError("Manual connection field remains");});
   Method show=RemoteActivity.class.getDeclaredMethod("showRemote");show.setAccessible(true);test.runOnMainSync(()->{try{show.invoke(screen);}catch(Exception e){throw new RuntimeException(e);}});
   Field field=RemoteActivity.class.getDeclaredField("web");field.setAccessible(true);WebView web=(WebView)field.get(screen);
   for(int i=0;i<50&&!"true".equals(js(test,web,"typeof window.acceptState==='function'"));i++)Thread.sleep(100);
   String state="{protocol:1,projectId:'ui',title:'Canção de teste',duration:120,position:20,playing:true,loop:false,soloClick:false,pad:false,padNote:0,bpm:120,numerator:4,denominator:4,originalKey:0,targetKey:0,master:1,vs:1,padVolume:1,clickVolume:1,tracks:[],songs:[{id:0,name:'Canção de teste',title:'Canção de teste',artist:'Artista',key:'Ab',active:true}],regions:[{name:'Intro',start:0,end:30,color:'#c3a840'},{name:'Verso',start:30,end:60,color:'#397db1'},{name:'Refrão',start:60,end:120,color:'#24894f'}],peaks:Array.from({length:512},(_,i)=>Math.abs(Math.sin(i*.13))*.3)}";
   js(test,web,"window.testErrors=[];window.onerror=m=>window.testErrors.push(m);window.acceptState("+state+");true");Thread.sleep(400);
   String result=js(test,web,"JSON.stringify({timeline:document.getElementById('viewport').clientHeight,transport:document.getElementById('transport').clientHeight,play:document.getElementById('play').clientHeight,errors:window.testErrors.length,bottom:document.getElementById('transport').getBoundingClientRect().bottom,height:innerHeight})");
   org.json.JSONObject layout=new org.json.JSONObject(new org.json.JSONTokener(result).nextValue().toString());
   if(layout.getInt("transport")<=layout.getInt("timeline")||layout.getInt("play")<50||layout.getInt("errors")!=0||layout.getDouble("bottom")>layout.getInt("height")+1)throw new AssertionError(result);
   double before=Double.parseDouble(js(test,web,"clock.value(performance.now())"));Thread.sleep(200);double after=Double.parseDouble(js(test,web,"clock.value(performance.now())"));if(after-before<.15)throw new AssertionError("Cursor not animated locally");if(!"null".equals(js(test,web,"document.getElementById('key')")))throw new AssertionError("Music key control remains");if(!"\"Ab\"".equals(js(test,web,"document.querySelector('.songKey').textContent")))throw new AssertionError("Card destination key");
   Method exit=RemoteActivity.class.getDeclaredMethod("confirmExit");exit.setAccessible(true);test.runOnMainSync(()->{try{exit.invoke(screen);}catch(Exception e){throw new RuntimeException(e);}});Field dialog=RemoteActivity.class.getDeclaredField("exitConfirmation");dialog.setAccessible(true);AlertDialog confirmation=(AlertDialog)dialog.get(screen);if(confirmation==null||!confirmation.isShowing())throw new AssertionError("Exit confirmation absent");test.runOnMainSync(()->confirmation.getButton(AlertDialog.BUTTON_NEGATIVE).performClick());if(screen.isFinishing())throw new AssertionError("Cancel closed the remote");
   android.graphics.Bitmap image=test.getUiAutomation().takeScreenshot();File folder=new File(test.getTargetContext().getFilesDir(),"ui-review");folder.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(folder,"remote-qr.png"))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}image.recycle();
   Bundle answer=new Bundle();answer.putString("status","PASS: QR-only form, compact timeline, prominent transport, no JS errors");answer.putString("layout",layout.toString());return answer;
  }finally{test.runOnMainSync(screen::finish);}
 }
 private static boolean hasEdit(View view){if(view instanceof android.widget.EditText)return true;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)if(hasEdit(((ViewGroup)view).getChildAt(i)))return true;return false;}
 private static String js(Instrumentation test,WebView web,String code)throws Exception {CountDownLatch done=new CountDownLatch(1);AtomicReference<String> result=new AtomicReference<>();test.runOnMainSync(()->web.evaluateJavascript(code,value->{result.set(value);done.countDown();}));if(!done.await(3,TimeUnit.SECONDS))throw new AssertionError("WebView timeout");return result.get();}
}
