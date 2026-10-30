package com.onebytelabs.zlplay;

import android.os.Bundle;
import com.onebytelabs.zlplay.remote.RemoteClient;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.json.JSONObject;

/** Synthetic HTTPS fixture only; never accesses user audio or a real pairing code. */
public final class RemoteSmoke {
 public static Bundle run(Bundle args)throws Exception {
  android.graphics.Bitmap image=android.graphics.BitmapFactory.decodeFile(args.getString("qrImage"));
  if(image==null)throw new AssertionError("QR image");int w=image.getWidth(),h=image.getHeight();int[] pixels=new int[w*h];image.getPixels(pixels,0,w,0,0,w,h);
  String text=new com.google.zxing.MultiFormatReader().decode(new com.google.zxing.BinaryBitmap(new com.google.zxing.common.HybridBinarizer(new com.google.zxing.RGBLuminanceSource(w,h,pixels)))).getText();
  com.onebytelabs.zlplay.remote.QrInvitation invite=com.onebytelabs.zlplay.remote.QrInvitation.parse(text);
  String address="127.0.0.1:47839",certificate=RemoteClient.probe(address);
  if(!certificate.equals(invite.certificate))throw new AssertionError("Certificate mismatch");
  CountDownLatch paired=new CountDownLatch(1),played=new CountDownLatch(1),paused=new CountDownLatch(1),lost=new CountDownLatch(1);
  CountDownLatch mixed=new CountDownLatch(1),queued=new CountDownLatch(1),jumped=new CountDownLatch(1);
  AtomicReference<String> latest=new AtomicReference<>("");AtomicReference<Exception> failure=new AtomicReference<>();
  RemoteClient client=new RemoteClient(address,"android-synthetic-test",new RemoteClient.Listener(){
   public void state(JSONObject state){latest.set("playing="+state.optBoolean("playing")+",position="+state.optDouble("position")+",pending="+state.optInt("pending",-1));if(state.optBoolean("playing")&&state.optInt("pending",-1)==0)queued.countDown();if(queued.getCount()==0&&state.optInt("pending",-1)==-1&&state.optDouble("position")<1&&state.optBoolean("playing"))jumped.countDown();if(state.optBoolean("playing"))played.countDown();else if(played.getCount()==0)paused.countDown();JSONObject track=state.optJSONArray("tracks").optJSONObject(0);if(track!=null&&Math.abs(track.optDouble("gain")-.5)<.01&&track.optBoolean("mute")&&track.optBoolean("solo")&&state.optInt("bpm")==123&&state.optBoolean("loop"))mixed.countDown();}
   public void disconnected(){lost.countDown();}
  });
  try {
   client.pair(invite.key,certificate,paired::countDown,e->{failure.set(e);paired.countDown();});
   if(!paired.await(12,TimeUnit.SECONDS)||failure.get()!=null)throw new AssertionError("Pair failed",failure.get());
   client.send(new JSONObject().put("type","play"));
   if(!played.await(8,TimeUnit.SECONDS))throw new AssertionError("Play not acknowledged");
   client.send(new JSONObject().put("type","pause"));
   if(!paused.await(8,TimeUnit.SECONDS))throw new AssertionError("Pause not acknowledged");
   client.send(new JSONObject().put("type","gain").put("track",0).put("value",.5));
   client.send(new JSONObject().put("type","mute").put("track",0).put("value",true));
   client.send(new JSONObject().put("type","solo").put("track",0).put("value",true));
   client.send(new JSONObject().put("type","bpm").put("value",123));
   client.send(new JSONObject().put("type","region").put("value",1));
   client.send(new JSONObject().put("type","loop").put("value",true));
   if(!mixed.await(8,TimeUnit.SECONDS))throw new AssertionError("Mixer/BPM/region loop not acknowledged");
   client.send(new JSONObject().put("type","loop").put("value",false));
   client.send(new JSONObject().put("type","play"));Thread.sleep(500);
   client.send(new JSONObject().put("type","region").put("value",0));
   if(!queued.await(6,TimeUnit.SECONDS)||!jumped.await(12,TimeUnit.SECONDS))throw new AssertionError("Scheduled region jump failed: queued="+queued.getCount()+", jumped="+jumped.getCount()+" "+latest.get());
   client.send(new JSONObject().put("type","stop"));
   Thread.sleep(700);
   if(args.getBoolean("disconnect")&&!lost.await(8,TimeUnit.SECONDS))throw new AssertionError("Disconnection not reported");
   Bundle result=new Bundle();result.putString("status","PASS: QR decode, pinned TLS, transport, mixer gain/mute/solo, BPM, region loop and scheduled jump");return result;
  }finally{client.close();}
 }
}
