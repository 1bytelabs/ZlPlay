package com.onebytelabs.zlplay.audio;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import java.io.*;
import java.util.*;

/** Imports selected documents off the audio/UI threads. Originals are never copied or modified. */
public final class WavImport {
    public interface Progress { void update(int done,int total); }
    public static final class Result {
        public final PcmTrack[] tracks;
        public final float[] peaks;
        Result(PcmTrack[] tracks,float[] peaks){this.tracks=tracks;this.peaks=peaks;}
    }
    public static Result read(Context context,List<Uri> uris,Progress progress)throws IOException {
        if(uris.isEmpty()||uris.size()>32)throw new WavReader.Unsupported();
        long budget=Math.min(64L*1024*1024,Runtime.getRuntime().maxMemory()/5);List<PcmTrack> tracks=new ArrayList<>();
        for(Uri uri:uris){
            String name="WAV "+(tracks.size()+1);
            try(Cursor cursor=context.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
                if(cursor!=null&&cursor.moveToFirst()&&!cursor.isNull(0))name=cursor.getString(0);
            }
            try(InputStream source=context.getContentResolver().openInputStream(uri)){
                if(source==null)throw new FileNotFoundException();PcmTrack track=WavReader.decode(source,name,budget);budget-=track.decodedBytes();tracks.add(track);
            }
            progress.update(tracks.size(),uris.size());
        }
        PcmTrack[] decoded=tracks.toArray(new PcmTrack[0]);double duration=0;for(PcmTrack track:decoded)duration=Math.max(duration,track.duration());
        float[] peaks=new float[1024];for(PcmTrack track:decoded)track.addPeaks(peaks,duration);
        for(int i=0;i<peaks.length;i++)peaks[i]=Math.min(1,peaks[i]);
        return new Result(decoded,peaks);
    }
}
