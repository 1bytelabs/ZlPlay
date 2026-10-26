package com.onebytelabs.zlplay.audio;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;
import java.nio.channels.FileChannel;
import java.util.*;

/** Opens selected documents read-only. Audio is prefetched in bounded blocks, not fully decoded. */
public final class WavImport {
    public interface Progress { void update(int done,int total); }
    public static final class NotSeekable extends IOException { public NotSeekable(){super("Document provider must support seeking");} }
    public static final class Result {
        public final PcmTrack[] tracks;
        public final float[] peaks;public final boolean temporary;
        Result(PcmTrack[] tracks,float[] peaks,boolean temporary){this.tracks=tracks;this.peaks=peaks;this.temporary=temporary;}
        public void close(){for(PcmTrack track:tracks)track.close();}
    }
    private static WavFile open(Context context,Uri uri,String name)throws IOException{
        try(AssetFileDescriptor descriptor=context.getContentResolver().openAssetFileDescriptor(uri,"r")){
            if(descriptor==null)throw new FileNotFoundException();
            ParcelFileDescriptor copy=ParcelFileDescriptor.dup(descriptor.getFileDescriptor());
            ParcelFileDescriptor.AutoCloseInputStream stream=new ParcelFileDescriptor.AutoCloseInputStream(copy);FileChannel channel=stream.getChannel();
            try{
                try{channel.position(descriptor.getStartOffset());channel.size();}catch(IOException unsupported){throw new NotSeekable();}
                return new WavFile(name,channel,stream,descriptor.getStartOffset(),descriptor.getDeclaredLength());
            }catch(IOException|RuntimeException failure){stream.close();throw failure;}
        }
    }
    public static Result read(Context context,List<Uri> uris,Progress progress)throws IOException {
        return readSources(context,uris,progress,32);
    }
    public static Result readSources(Context context,List<Uri> uris,Progress progress,int maximum)throws IOException {
        if(uris.isEmpty()||uris.size()>maximum||maximum>128)throw new WavReader.Unsupported();
        Map<Uri,File> mp3=new HashMap<>();List<WavFile> files=new ArrayList<>();List<float[]> previews=new ArrayList<>();StreamBank bank=null;
        try{
            for(Uri uri:uris){
                String name=uri.getLastPathSegment()==null?"Audio "+(files.size()+1):uri.getLastPathSegment();
                try(Cursor cursor=context.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(cursor!=null&&cursor.moveToFirst()&&!cursor.isNull(0))name=cursor.getString(0);}
                WavFile file;if(name.toLowerCase(Locale.ROOT).endsWith(".mp3")){File cached=mp3.get(uri);if(cached==null){cached=Mp3Decode.decode(context,uri);mp3.put(uri,cached);}file=Mp3Decode.open(cached,name);}else file=open(context,uri,name);files.add(file);previews.add(file.preview());progress.update(files.size(),uris.size());
            }
            bank=new StreamBank(files,previews);double duration=0;for(PcmTrack track:bank.tracks)duration=Math.max(duration,track.duration());
            float[] peaks=new float[1024];for(PcmTrack track:bank.tracks)track.addPeaks(peaks,duration);for(int i=0;i<peaks.length;i++)peaks[i]=Math.min(1,peaks[i]);
            return new Result(bank.tracks,peaks,!mp3.isEmpty());
        }catch(IOException|RuntimeException|Error failure){if(bank!=null)bank.close();else for(WavFile file:files)file.close();for(File cached:mp3.values())cached.delete();throw failure;}
    }
}
