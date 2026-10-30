package com.onebytelabs.zlplay.audio;

import java.util.concurrent.atomic.AtomicReferenceArray;

/** Bounded, continuous, latency-compensated pitch caches. Source reads are confined to their channel worker once pitch is engaged. */
public final class PitchBank implements AutoCloseable {
 private static final int BLOCK=8192;

 private final Channel[] channels;
 public final PcmTrack[] tracks;
 private volatile boolean closed;
 // Each timeline source belongs to one channel. setPitch is called while the mixer
 // is between render quanta; once engaged, only this channel's worker reads its source.
 // The legacy constructor argument is intentionally not used as a monitor.
 private final Thread[] workers;
 public PitchBank(PcmTrack[] sources,Object unusedLegacyLock){channels=new Channel[sources.length];tracks=new PcmTrack[sources.length];for(int i=0;i<sources.length;i++)tracks[i]=channels[i]=new Channel(sources[i]);workers=new Thread[Math.min(2,Math.max(1,sources.length))];for(int i=0;i<workers.length;i++){final int lane=i;workers[i]=new Thread(()->work(lane),"ZLPlay-Pitch-"+i);workers[i].setDaemon(true);workers[i].start();}}
 private static final class Block {final long key;final int generation;final float[] samples;Block(long k,int g,float[] s){key=k;generation=g;samples=s;}}
 private static final class Processor {long handle,next=-1;final int steps,generation;final AtomicReferenceArray<Block> cache=new AtomicReferenceArray<>(6);final float[] input=new float[BLOCK*2],output=new float[BLOCK*2];Processor(int steps,int generation){this.steps=steps;this.generation=generation;if(steps!=0)handle=NativePitch.create(steps);}int latency(){return handle==0?0:NativePitch.latency(handle);}void close(){if(handle!=0){NativePitch.destroy(handle);handle=0;}}Block find(long key){Block b=cache.get((int)(key%6));return b!=null&&b.key==key?b:null;}}
 private void work(int lane){try{while(!closed){boolean work=false;for(int i=lane;i<channels.length;i+=workers.length){Channel c=channels[i];if(closed)break;if(!c.engaged||c.failure)continue;try{work=c.fill()||work;}catch(Throwable e){c.failure=true;}}if(!work)try{Thread.sleep(2);}catch(InterruptedException stop){if(closed)break;}}}finally{for(int i=lane;i<channels.length;i+=workers.length){Channel c=channels[i];if(c.main!=null)c.main.close();if(c.hint!=null)c.hint.close();}}}
 @Override public void close(){if(closed)return;closed=true;for(Thread worker:workers)worker.interrupt();for(Channel c:channels)c.source.close();}
 private final class Channel extends PcmTrack {
  final PcmTrack source;volatile int steps,generation;volatile boolean engaged,failure;volatile long requested,hinted=-1;volatile Processor main,hint;private Block heldA,heldB;
  Channel(PcmTrack source){super(source.name,DemoMixer.RATE,2);this.source=source;}
  @Override public double duration(){return source.duration();}
  @Override public void setPitch(int value){if(value<-12||value>12)throw new IllegalArgumentException("Pitch range");if(steps==value)return;steps=value;generation++;engaged=true;failure=false;}
  @Override public boolean failed(){return failure||source.failed();}
  @Override public void hint(long frame){if(!engaged){source.hint(frame);return;}if(frame>=0)hinted=frame/BLOCK;}
  private Block find(long key){Processor p=main;Block b=p!=null&&p.generation==generation?p.find(key):null;if(b==null){p=hint;b=p!=null&&p.generation==generation?p.find(key):null;}return b;}
  @Override public boolean prepare(long frame,int count){if(!engaged)return source.prepare(frame,count);if(frame>=duration()*DemoMixer.RATE)return true;requested=Math.max(0,frame)/BLOCK;long last=Math.min(Math.max(0,(long)(duration()*DemoMixer.RATE)-1),Math.max(0,frame+count-1))/BLOCK;heldA=find(requested);heldB=last==requested?heldA:find(last);return !closed&&!failure&&heldA!=null&&heldB!=null;}
  @Override public float sample(long frame,int channel){if(!engaged)return source.sample(frame,channel);if(frame<0||frame>=duration()*DemoMixer.RATE)return 0;long key=frame/BLOCK;Block b=heldA!=null&&heldA.key==key?heldA:heldB!=null&&heldB.key==key?heldB:null;return b==null?0:b.samples[(int)(frame-key*BLOCK)*2+channel];}
  @Override public void readStereo(long frame,int count,float[] output,int offset){
   if(!engaged){source.readStereo(frame,count,output,offset);return;}
   for(int i=0;i<count;){long at=frame+i,key=at/BLOCK;int n=Math.min(count-i,BLOCK-(int)(at%BLOCK));Block b=heldA!=null&&heldA.key==key?heldA:heldB!=null&&heldB.key==key?heldB:null;if(b==null)java.util.Arrays.fill(output,offset+i*2,offset+(i+n)*2,0);else System.arraycopy(b.samples,(int)(at%BLOCK)*2,output,offset+i*2,n*2);i+=n;}
  }
  @Override public void addPeaks(float[] peaks,double duration){source.addPeaks(peaks,duration);}
  @Override public long decodedBytes(){return source.decodedBytes()+12L*BLOCK*2*4;}
  @Override public void close(){PitchBank.this.close();}
  boolean fill()throws Exception {int version=generation;Processor p=main;if(p==null||p.generation!=version){if(p!=null)p.close();if(hint!=null){hint.close();hint=null;}main=p=new Processor(steps,version);}long wanted=requested;if(p.find(wanted)==null&&(p.next<0||wanted*BLOCK<p.next-BLOCK*6L||wanted*BLOCK>p.next)){Processor future=hint;if(future!=null&&future.generation==version&&future.find(wanted)!=null){hint=p;main=p=future;}}
   for(int ahead=0;ahead<3;ahead++){long key=wanted+ahead;if(key*BLOCK>=duration()*DemoMixer.RATE)break;if(p.find(key)==null){render(p,key);return true;}}
   long destination=hinted;if(destination>=0&&Math.abs(destination-wanted)>3){Processor future=hint;if(future==null||future.generation!=version){if(future!=null)future.close();hint=future=new Processor(steps,version);}for(int ahead=0;ahead<2;ahead++){long key=destination+ahead;if(key*BLOCK>=duration()*DemoMixer.RATE)break;if(future.find(key)==null){render(future,key);return true;}}}return false;
  }
  private void read(float[] input,long frame,int count)throws Exception {long deadline=System.nanoTime()+30_000_000_000L;int copied=0;while(copied<count&&!closed){int n=Math.min(512,count-copied);if(closed)throw new InterruptedException();boolean ready=source.prepare(frame+copied,n);if(ready){source.readStereo(frame+copied,n,input,copied*2);copied+=n;}if(!ready){if(source.failed())throw new IllegalStateException("Pitch input unavailable");if(System.nanoTime()>deadline)throw new IllegalStateException("Pitch input timeout");Thread.sleep(2);}}if(closed)throw new InterruptedException();}
  private void process(Processor p,long frame,int count)throws Exception {read(p.input,frame,count);if(p.handle!=0)NativePitch.process(p.handle,p.input,p.output,count);else System.arraycopy(p.input,0,p.output,0,count*2);}
  private void render(Processor p,long key)throws Exception {long target=key*BLOCK;int latency=p.latency();if(p.next!=target){if(p.handle!=0)NativePitch.reset(p.handle);long start=Math.max(0,target-latency*2L),end=target+latency;for(long frame=start;frame<end;){int n=(int)Math.min(BLOCK,end-frame);process(p,frame,n);frame+=n;}p.next=target;}
   process(p,target+latency,BLOCK);float[] output=p.output.clone();if(!closed&&p.generation==generation)p.cache.set((int)(key%6),new Block(key,p.generation,output));p.next=target+BLOCK;
  }
 }
}
