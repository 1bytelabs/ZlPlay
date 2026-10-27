package com.onebytelabs.zlplay.audio;
import android.content.*;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.database.Cursor;
import com.onebytelabs.zlplay.project.MusicalKey;
import java.io.*;
import java.nio.channels.FileChannel;
import java.util.*;

/** App-owned sound imports. A complete pad set is validated before the active directory changes. */
public final class SoundLibrary {
 private final Context context;private final DemoMixer mixer;private final File root;
 public SoundLibrary(Context context,DemoMixer mixer){this.context=context.getApplicationContext();this.mixer=mixer;root=new File(context.getFilesDir(),"sounds");if(!root.exists())root.mkdirs();}
 private SharedPreferences prefs(){return context.getSharedPreferences("sounds",Context.MODE_PRIVATE);}
 public boolean hasPads(){return mixer.hasPads();}
 public String clickName(boolean accent){return prefs().getString(accent?"accentName":"normalName","");}
 private String name(Uri uri){String name=uri.getLastPathSegment();try(Cursor c=context.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())name=c.getString(0);}return name==null?"Audio":name;}
 private File owned(String name){if(name==null||!name.matches("(?:pads|click)-[a-f0-9-]+(?:\\.wav)?"))return null;return new File(root,name);}
 private static void close(PcmTrack[] tracks){if(tracks!=null)for(PcmTrack t:tracks)t.close();}
 public void restore()throws IOException {File dir=owned(prefs().getString("padsDirectory",""));if(dir!=null&&dir.isDirectory())close(mixer.replacePads(preparePads(dir).tracks));for(boolean accent:new boolean[]{false,true}){File file=owned(prefs().getString(accent?"accentFile":"normalFile",""));if(file!=null&&file.isFile())try(InputStream in=new FileInputStream(file)){NativeClickSound.loadCustom(in,accent);}}}
 private WavImport.Result preparePads(File directory)throws IOException {List<Uri> sources=new ArrayList<>();for(String note:MusicalKey.FLATS)sources.add(Uri.fromFile(new File(directory,note+".wav")));return WavImport.readSources(context,sources,(a,b)->{},12);}
 private File normalize(Uri uri,File target)throws IOException {String name=name(uri);File decoded=null;try{InputStream source;if(name.toLowerCase(Locale.ROOT).endsWith(".mp3")){decoded=Mp3Decode.decode(context,uri);source=new FileInputStream(decoded);}else source=context.getContentResolver().openInputStream(uri);if(source==null)throw new FileNotFoundException();try(InputStream in=source;OutputStream out=new BufferedOutputStream(new FileOutputStream(target))){byte[] block=new byte[65536];long bytes=0;int n;while((n=in.read(block))!=-1){bytes+=n;if(bytes>128L*1024*1024)throw new WavReader.TooLarge();if(target.getParentFile().getUsableSpace()<16L*1024*1024)throw new IOException("sound-storage");out.write(block,0,n);}}return target;}finally{if(decoded!=null)decoded.delete();}}
 private static double duration(File file)throws IOException {FileInputStream in=new FileInputStream(file);try(WavFile wav=new WavFile(file.getName(),in.getChannel(),in,0,file.length())){return wav.duration();}}
 public void importPads(List<Uri> uris,WavImport.Progress progress)throws IOException {if(uris.size()!=12)throw new IOException("pad-count");Uri[] notes=new Uri[12];for(Uri uri:uris){int note=MusicalKey.padFilename(name(uri));if(note<0||notes[note]!=null)throw new IOException("pad-names");notes[note]=uri;}File stage=new File(root,"pads-"+UUID.randomUUID());if(!stage.mkdir())throw new IOException("sound-storage");boolean committed=false;try{for(int i=0;i<12;i++){File file=normalize(notes[i],new File(stage,MusicalKey.FLATS[i]+".wav"));if(duration(file)<=0||duration(file)>60)throw new IOException("pad-duration");progress.update(i+1,12);}WavImport.Result prepared=preparePads(stage);File old=owned(prefs().getString("padsDirectory",""));if(!prefs().edit().putString("padsDirectory",stage.getName()).commit()){prepared.close();throw new IOException("sound-storage");}close(mixer.replacePads(prepared.tracks));committed=true;if(old!=null&&!old.equals(stage))removeSet(old);}finally{if(!committed)removeSet(stage);}}
 private void removeSet(File dir){if(dir==null||!dir.getParentFile().equals(root)||!dir.getName().startsWith("pads-"))return;for(String note:MusicalKey.FLATS)new File(dir,note+".wav").delete();dir.delete();}
 public void importClick(Uri uri,boolean accent)throws IOException {File file=new File(root,"click-"+UUID.randomUUID()+".wav");boolean committed=false;try{normalize(uri,file);if(duration(file)>5)throw new IOException("click-duration");PcmTrack sound;try(InputStream in=new FileInputStream(file)){sound=NativeClickSound.decodeCustom(in);}String key=accent?"accentFile":"normalFile";File old=owned(prefs().getString(key,""));if(!prefs().edit().putString(key,file.getName()).putString(accent?"accentName":"normalName",name(uri)).commit())throw new IOException("sound-storage");NativeClickSound.installCustom(sound,accent);committed=true;if(old!=null)old.delete();}finally{if(!committed)file.delete();}}
 public void restoreClick(){NativeClickSound.restoreDefaults();for(String key:new String[]{"normalFile","accentFile"}){File old=owned(prefs().getString(key,""));if(old!=null)old.delete();}prefs().edit().remove("normalFile").remove("accentFile").remove("normalName").remove("accentName").apply();}
}
