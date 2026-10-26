package com.onebytelabs.zlplay.audio;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;

/** Read-only seekable WAV. Accessed only by import/prefetch workers, never by render(). */
public final class WavFile implements AutoCloseable {
    public final String name;
    public final int rate,channels,bits,encoding,align;
    public final long dataOffset,frames;
    private final SeekableByteChannel channel;
    private final Closeable owner;
    private volatile boolean closed;
    public WavFile(String name,SeekableByteChannel channel,Closeable owner,long base,long length)throws IOException{
        this.name=name;this.channel=channel;this.owner=owner;
        long available=channel.size()-base;if(base<0||available<12)throw new EOFException("WAV header missing");
        if(length>=0)available=Math.min(available,length);channel.position(base);byte[] h=read(12);
        if(!WavReader.tag(h,0,"RIFF")||!WavReader.tag(h,8,"WAVE"))throw new WavReader.Unsupported();
        long end=base+8+WavReader.uint(h,4);if(end>base+available)throw new EOFException("Truncated WAV");WavReader.Format format=null;long offset=-1,size=0;
        while(channel.position()+8<=end){byte[] chunk=read(8);long bytes=WavReader.uint(chunk,4),next=channel.position()+bytes+(bytes&1);if(next>end)throw new EOFException("Truncated chunk");
            if(WavReader.tag(chunk,0,"fmt ")){if(bytes<16||bytes>4096)throw new WavReader.Unsupported();format=WavReader.parseFormat(read((int)bytes));}
            else if(WavReader.tag(chunk,0,"data")){if(format==null||bytes==0||bytes%format.align!=0)throw new WavReader.Unsupported();offset=channel.position();size=bytes;break;}
            channel.position(next);
        }
        if(offset<0||format==null)throw new WavReader.Unsupported();
        rate=format.rate;channels=format.channels;bits=format.bits;encoding=format.encoding;align=format.align;dataOffset=offset;frames=size/align;
    }
    private byte[] read(int size)throws IOException{byte[] data=new byte[size];readInto(data,size);return data;}
    private void readInto(byte[] data,int size)throws IOException{ByteBuffer b=ByteBuffer.wrap(data,0,size);while(b.hasRemaining()){if(closed||Thread.currentThread().isInterrupted())throw new WavReader.Cancelled();int n=channel.read(b);if(n<0)throw new EOFException("WAV read failed");}}
    public float[] readFrames(long start,int count,byte[] scratch)throws IOException{
        int n=(int)Math.min(count,Math.max(0,frames-start));if(start<0||n*align>scratch.length)throw new IOException("Invalid WAV range");
        channel.position(dataOffset+start*align);readInto(scratch,n*align);float[] result=new float[n*channels];int bytes=bits/8;
        for(int i=0;i<result.length;i++)result[i]=WavReader.pcm(scratch,i*bytes,encoding,bits);return result;
    }
    public double duration(){return frames/(double)rate;}
    public float[] preview()throws IOException{
        float[] peaks=new float[256];byte[] scratch=new byte[256*align];
        for(int i=0;i<peaks.length;i++){
            if(Thread.currentThread().isInterrupted())throw new WavReader.Cancelled();
            long start=Math.min(frames-1,(long)(i/(double)peaks.length*frames));float[] values=readFrames(start,256,scratch);float peak=0;for(float value:values)peak=Math.max(peak,Math.abs(value));peaks[i]=peak;
        }return peaks;
    }
    @Override public void close(){if(closed)return;closed=true;try{owner.close();}catch(IOException ignored){}try{channel.close();}catch(IOException ignored){}}
}
