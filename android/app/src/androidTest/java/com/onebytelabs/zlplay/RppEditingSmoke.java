package com.onebytelabs.zlplay;
import android.content.Context;
import android.net.Uri;
import java.io.File;
import java.nio.*;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import com.onebytelabs.zlplay.project.*;
import org.json.JSONObject;
/** Real loader regression using one generated WAV and 156 synthetic RPP cuts. */
final class RppEditingSmoke {
 private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static void run(Context context)throws Exception{
  File directory=new File(context.getCacheDir(),"rpp-edit-regression");if(!directory.isDirectory()&&!directory.mkdirs())throw new java.io.IOException("fixture directory");
  int frames=48000;ByteBuffer wav=ByteBuffer.allocate(44+frames*2).order(ByteOrder.LITTLE_ENDIAN);
  wav.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36+frames*2).put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short)1).putShort((short)1).putInt(48000).putInt(96000).putShort((short)2).putShort((short)16).put("data".getBytes(StandardCharsets.US_ASCII)).putInt(frames*2);
  for(int i=0;i<frames;i++)wav.putShort((short)(3000*Math.sin(2*Math.PI*440*i/48000)));
  File audio=new File(directory,"Sine.wav"),rpp=new File(directory,"Fixture.rpp");Files.write(audio.toPath(),wav.array());
  StringBuilder source=new StringBuilder("<REAPER_PROJECT 0.1\nTEMPO 125 4 4\nMARKER 1 0 \"Intro\" 1 0 R\nMARKER 1 0.4 \"\" 0\nMARKER 2 0.4 \"Verse\" 1 0 R\nMARKER 2 1 \"\" 0\n");
  for(int t=0;t<10;t++){
   source.append("<TRACK\nNAME \"").append(t==0?"Click_1":t==1?"Guide_1":"Piano "+t).append("\"\n");
   for(int c=0;c<(t<4?15:16);c++)source.append("<ITEM\nPOSITION ").append(c/16.0).append("\nSOFFS ").append(c/16.0).append("\nLENGTH 0.0625\nVOLPAN ").append(t==2&&c==0?"0.5 0.25":"1 0").append("\nMUTE ").append(t==2&&c==1?1:0).append("\n<SOURCE WAVE\nFILE \"Sine.wav\"\n>\n>\n");
   source.append(">\n");
  }
  source.append(">\n");Files.write(rpp.toPath(),source.toString().getBytes(StandardCharsets.UTF_8));
  ProjectFolder folder=new ProjectFolder(Uri.fromFile(directory));folder.files.put("Fixture.rpp",Uri.fromFile(rpp));folder.files.put("Sine.wav",Uri.fromFile(audio));
  int[] readers={0};String editable;
  ProjectLoader.Result result=ProjectLoader.read(context,folder,"Fixture.rpp",(done,total)->readers[0]=total);
  try{
   check(result.spec.tracks.size()==10,"RPP retains its ten audio tracks");
   check(readers[0]==11,"156 cuts use ten aligned readers plus one independent guide preview");
   check(result.manifest!=null,"RPP exposes an editable session");editable=result.manifest;
   ProjectSpec saved=ZlplayReader.parse(editable);
   check(saved.tracks.stream().mapToInt(t->t.clips.size()).sum()==156,"editable session retains all 156 cuts");
   check(saved.regions.size()==2&&saved.regions.get(0).name.equals("Intro"),"editable session retains regions");
   check(saved.tracks.get(2).clips.get(0).gain==.5&&saved.tracks.get(2).clips.get(0).pan==.25,"item gain/pan retained");
   check(saved.tracks.get(2).clips.get(1).muted,"item mute retained");
  }finally{result.close();}
  ProjectFolder nativeFolder=new ProjectFolder(Uri.fromFile(directory));nativeFolder.files.putAll(folder.files);
  nativeFolder.files.put("copy/Click.wav",Uri.fromFile(audio));nativeFolder.files.put("other/Click.wav",Uri.fromFile(audio));
  JSONObject nativeSession=new JSONObject(editable);
  JSONObject clickTrack=nativeSession.getJSONArray("tracks").getJSONObject(0);
  for(int i=0;i<clickTrack.getJSONArray("items").length();i++)clickTrack.getJSONArray("items").getJSONObject(i).put("file","@click:Click");
  ProjectLoader.Result generated=ProjectLoader.read(context,nativeFolder,"Fixture.rpp",nativeSession.toString(),(done,total)->{});
  try{check(generated.spec.missingAudio.isEmpty(),"generated click is not a missing/ambiguous audio file");check(generated.spec.tracks.get(0).isClick()&&generated.spec.tracks.get(0).clips.get(0).nativeClick,"native click source survives an editable session");}finally{generated.close();}
  nativeFolder.files.put("Meuclick2.wav",Uri.fromFile(audio));
  check(nativeFolder.clickAudioReference("Fixture.rpp","Click_1")==null,"one-shot custom sample names do not count as click stems");
  nativeFolder.files.put("Click_1.wav",Uri.fromFile(audio));
  ProjectLoader.Result recorded=ProjectLoader.read(context,nativeFolder,"Fixture.rpp",nativeSession.toString(),(done,total)->{});
  try{check(!recorded.spec.tracks.get(0).clips.get(0).nativeClick&&recorded.spec.tracks.get(0).clips.get(0).file.equals("Click_1.wav"),"a named click audio stem replaces the Reaper generator");check(new JSONObject(recorded.manifest).getJSONArray("tracks").getJSONObject(0).getJSONArray("items").getJSONObject(0).getString("file").equals("Click_1.wav"),"editable click stem reference is preserved");}finally{recorded.close();}
  byte[] shortWav=java.util.Arrays.copyOf(wav.array(),44+960);ByteBuffer small=ByteBuffer.wrap(shortWav).order(ByteOrder.LITTLE_ENDIAN);small.putInt(4,36+960);small.putInt(40,960);File sample=new File(directory,"short.wav");Files.write(sample.toPath(),shortWav);
  nativeFolder.files.put("Click_1.wav",Uri.fromFile(sample));
  ProjectLoader.Result shortClick=ProjectLoader.read(context,nativeFolder,"Fixture.rpp",nativeSession.toString(),(done,total)->{});
  try{check(shortClick.spec.missingAudio.isEmpty()&&shortClick.spec.tracks.get(0).clips.stream().allMatch(clip->clip.nativeClick),"a one-shot sample cannot become an incomplete click track");}finally{shortClick.close();}
  ProjectLoader.Result reopened=ProjectLoader.read(context,folder,"Fixture.rpp",editable,(done,total)->{});
  try{check(reopened.spec.regions.size()==2&&reopened.spec.tracks.size()==10,"embedded setlist session reopens over an RPP source");}finally{reopened.close();}
 }
}
