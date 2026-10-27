package com.onebytelabs.zlplay.audio;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReferenceArray;

/** One prefetch worker per session. Published PCM blocks are immutable and bounded. */
public final class StreamBank implements AutoCloseable {
    public static final int BLOCK_FRAMES=8192,CACHE_BLOCKS=6;
    private final List<WavFile> files;
    public final PcmTrack[] tracks;
    private final StreamTrack[] streams;
    private volatile boolean closed;
    private final Thread worker;
    public StreamBank(List<WavFile> files,List<float[]> peaks)throws IOException{
        this.files=new ArrayList<>(files);streams=new StreamTrack[files.size()];tracks=new PcmTrack[files.size()];
        byte[] scratch=new byte[BLOCK_FRAMES*8];
        try{for(int i=0;i<files.size();i++){streams[i]=new StreamTrack(files.get(i),peaks.get(i),scratch);tracks[i]=streams[i];}}
        catch(IOException|RuntimeException failure){for(WavFile file:files)file.close();throw failure;}
        worker=new Thread(this::prefetch,"ZLPlay-WAV-Prefetch");worker.setDaemon(true);worker.setPriority(Thread.NORM_PRIORITY+1);worker.start();
    }
    private void prefetch(){byte[] scratch=new byte[BLOCK_FRAMES*8];
        while(!closed){boolean work=false;
            for(StreamTrack track:streams){if(closed)break;if(track.failure!=null)continue;
                long requested=track.requested;
                for(int ahead=0;ahead<4;ahead++){long key=requested+ahead;if(key*BLOCK_FRAMES>=track.file.frames)break;if(track.find(key)!=null)continue;
                    try{Block block=track.read(key,scratch);if(!closed)track.cache.set((int)(key%CACHE_BLOCKS),block);}
                    catch(IOException failure){if(!closed)track.failure=failure;}
                    work=true;break;
                }
                long hinted=track.hinted;
                if(hinted>=0)for(int ahead=0;ahead<2;ahead++){long key=hinted+ahead;if(key*BLOCK_FRAMES>=track.file.frames||track.find(key)!=null)continue;try{Block block=track.read(key,scratch);if(!closed)track.hintCache.set((int)(key%2),block);}catch(IOException failure){if(!closed)track.failure=failure;}work=true;break;}
            }
            if(!work)try{Thread.sleep(3);}catch(InterruptedException stop){if(closed)return;}
        }
    }
    @Override public void close(){if(closed)return;closed=true;worker.interrupt();for(WavFile file:files)file.close();if(Thread.currentThread()!=worker)try{worker.join(200);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}}
    private static final class Block {
        final long key;final float[] values;
        Block(long key,float[] values){this.key=key;this.values=values;}
    }
    private final class StreamTrack extends PcmTrack {
        final WavFile file;final float[] peaks;
        final Block[] head=new Block[2];
        final AtomicReferenceArray<Block> cache=new AtomicReferenceArray<>(CACHE_BLOCKS);
        volatile long requested,hinted=-1;
        final AtomicReferenceArray<Block> hintCache=new AtomicReferenceArray<>(2);
        volatile IOException failure;
        private Block heldA,heldB;
        StreamTrack(WavFile file,float[] peaks,byte[] scratch)throws IOException{
            super(file.name,file.rate,file.channels);this.file=file;this.peaks=peaks;
            for(int i=0;i<2;i++)if((long)i*BLOCK_FRAMES<file.frames)head[i]=read(i,scratch);
        }
        Block read(long key,byte[] scratch)throws IOException{return new Block(key,file.readFrames(key*BLOCK_FRAMES,BLOCK_FRAMES,scratch));}
        Block find(long key){if(key<2)return head[(int)key];Block block=cache.get((int)(key%CACHE_BLOCKS));if(block!=null&&block.key==key)return block;block=hintCache.get((int)(key%2));return block!=null&&block.key==key?block:null;}
        @Override public void hint(long frame){long source=(long)(frame*(sampleRate/(double)DemoMixer.RATE));if(source>=0&&source<file.frames)hinted=source/BLOCK_FRAMES;}
        @Override public float previewPeak(double second){if(second<0||second>=duration())return 0;return peaks[Math.min(peaks.length-1,(int)(second/duration()*peaks.length))];}
        @Override public double duration(){return file.duration();}
        @Override public long decodedBytes(){return (long)(CACHE_BLOCKS+4)*BLOCK_FRAMES*channels*4;}
        @Override public boolean failed(){return failure!=null;}
        @Override public boolean prepare(long frame,int count){
            double first=frame*(sampleRate/(double)DemoMixer.RATE);if(first>=file.frames){heldA=null;heldB=null;return true;}
            long start=(long)first/BLOCK_FRAMES;requested=start;
            long last=Math.min(file.frames-1,(long)Math.floor((frame+Math.max(0,count-1))*(sampleRate/(double)DemoMixer.RATE))+1)/BLOCK_FRAMES;
            heldA=find(start);heldB=last==start?heldA:find(last);return !closed&&failure==null&&heldA!=null&&heldB!=null;
        }
        private float at(long sourceFrame,int channel){if(sourceFrame>=file.frames)return 0;long key=sourceFrame/BLOCK_FRAMES;Block block=heldA!=null&&heldA.key==key?heldA:heldB!=null&&heldB.key==key?heldB:null;if(block==null&&key<2)block=head[(int)key];if(block==null)return 0;int index=(int)(sourceFrame-key*BLOCK_FRAMES)*channels+(channels==1?0:channel);return block.values[index];}
        @Override public float sample(long frame,int channel){double source=frame*(sampleRate/(double)DemoMixer.RATE);long index=(long)source;if(index>=file.frames)return 0;float a=at(index,channel),b=at(Math.min(index+1,file.frames-1),channel);return a+(b-a)*(float)(source-index);}
        @Override public void addPeaks(float[] into,double duration){for(int bin=0;bin<into.length;bin++){double second=bin/(double)into.length*duration;if(second>=duration())break;int i=Math.min(peaks.length-1,(int)(second/duration()*peaks.length));into[bin]+=peaks[i]*.7f;}}
        @Override public void close(){StreamBank.this.close();}
    }
}
