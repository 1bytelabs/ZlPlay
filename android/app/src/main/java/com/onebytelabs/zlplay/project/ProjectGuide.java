package com.onebytelabs.zlplay.project;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import com.onebytelabs.zlplay.audio.WavImport;
import com.onebytelabs.zlplay.audio.SourceWaveform;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Copies guide media into the song folder so the project works on another device. */
public final class ProjectGuide {
    public static final class Audio {
        public final String path, label;
        public final double duration;
        public final SourceWaveform waveform;

        Audio(String path,String label,SourceWaveform waveform){
            this.path=path;this.label=label;this.waveform=waveform;this.duration=waveform.duration;
        }
    }

    private ProjectGuide() {}

    public static List<Audio> importFiles(Context context,ProjectFolder folder,String parent,String asset,List<Uri> external) throws Exception {
        var resolver=context.getContentResolver();
        String mediaFolder=parent+".zlplay-guide/";
        Uri destination=folder.directories.get(mediaFolder);
        if(destination==null){
            Uri parentUri=folder.directories.get(parent);
            if(parentUri==null)throw new IOException("Song folder unavailable");
            destination=DocumentsContract.createDocument(resolver,parentUri,DocumentsContract.Document.MIME_TYPE_DIR,".zlplay-guide");
            if(destination==null)throw new IOException("Cannot create guide folder");
            folder.directories.put(mediaFolder,destination);
        }
        List<Uri> created=new ArrayList<>();
        List<String> names=new ArrayList<>(),labels=new ArrayList<>();
        try{
            int count=asset==null?external.size():1;
            for(int i=0;i<count;i++){
                Uri source=asset==null?external.get(i):null;
                String label=source==null?asset.substring(asset.lastIndexOf('/')+1):name(context,source);
                String extension=label.toLowerCase(Locale.ROOT).endsWith(".mp3")?".mp3":
                    label.toLowerCase(Locale.ROOT).endsWith(".wav")?".wav":"";
                if(extension.isEmpty())throw new IOException("Unsupported guide format");
                String filename=UUID.randomUUID()+extension;
                Uri file=DocumentsContract.createDocument(resolver,destination,"application/octet-stream",filename);
                if(file==null)throw new IOException("Cannot create guide");
                created.add(file);
                try(InputStream input=source==null?context.getAssets().open(asset):resolver.openInputStream(source);
                    OutputStream output=resolver.openOutputStream(file,"wt")){
                    if(input==null||output==null)throw new IOException("Cannot access guide");
                    byte[] buffer=new byte[32768];
                    long size=0;
                    int read;
                    while((read=input.read(buffer))!=-1){
                        size+=read;
                        if(size>128L*1024*1024)throw new IOException("Guide file too large");
                        output.write(buffer,0,read);
                    }
                }
                String actual=name(context,file);
                if(!actual.endsWith(extension)||actual.contains("/")||actual.contains("\\"))throw new IOException("Provider changed guide filename");
                names.add(actual);
                labels.add(label.substring(0,label.length()-extension.length()));
            }
            WavImport.Result decoded=WavImport.readSources(context,created,(done,total)->{},64);
            try{
                List<Audio> output=new ArrayList<>();
                for(int i=0;i<created.size();i++){
                    double duration=decoded.tracks[i].duration();
                    if(!(duration>0))throw new IOException("Empty guide");
                    output.add(new Audio(".zlplay-guide/"+names.get(i),labels.get(i),SourceWaveform.from(decoded.tracks[i])));
                }
                for(int i=0;i<created.size();i++)folder.files.put(mediaFolder+names.get(i),created.get(i));
                return output;
            }finally{decoded.close();}
        }catch(Exception | OutOfMemoryError failure){
            for(Uri file:created)try{DocumentsContract.deleteDocument(resolver,file);}catch(Exception ignored){}
            throw failure;
        }
    }

    private static String name(Context context,Uri source) throws IOException {
        try(Cursor cursor=context.getContentResolver().query(source,new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null)){
            if(cursor!=null&&cursor.moveToFirst()&&cursor.getString(0)!=null)return cursor.getString(0);
        }
        throw new IOException("Audio filename unavailable");
    }
}
