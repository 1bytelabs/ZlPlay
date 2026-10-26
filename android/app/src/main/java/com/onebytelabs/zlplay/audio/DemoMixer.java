package com.onebytelabs.zlplay.audio;
import com.onebytelabs.zlplay.project.ProjectSpec;

/** Shared-clock prototype mixer: synthetic fixture or imported PCM; no disk I/O in render. */
public final class DemoMixer {
    public static final int RATE = 48000;
    public static final int DURATION = RATE * 90;
    private long cursor, auxiliary, clickFrame;
    private boolean playing, loop, click, pad, buffering;
    private int bpm = 120;
    private PcmTrack[] imported;
    private ProjectSpec project;private ProjectArrangement arrangement;private long manualBoundary=-1,manualTarget;private int pendingRegion=-1;
    private float[] pan=new float[3];
    private long durationFrames=DURATION;
    private float[] target = { .55f, .55f, .4f }, gain = new float[3];
    public synchronized double duration(){return durationFrames/(double)RATE;}
    public synchronized boolean hasImported(){return imported!=null;}
    public synchronized int trackCount(){return target.length;}
    public synchronized String trackName(int index){return imported==null?null:imported[index].name;}
    public synchronized void load(PcmTrack[] tracks){
        if(tracks.length==0||tracks.length>32)throw new IllegalArgumentException("Invalid track count");
        PcmTrack[] sources=tracks.clone();double duration=0;for(PcmTrack t:sources)duration=Math.max(duration,t.duration());
        closeImported();project=null;arrangement=null;manualBoundary=-1;pendingRegion=-1;pan=new float[tracks.length];imported=sources;target=new float[tracks.length];java.util.Arrays.fill(target,.7f);gain=new float[tracks.length];durationFrames=(long)Math.ceil(duration*RATE);cursor=0;playing=false;
    }
    public synchronized void configureProject(ProjectSpec spec){project=spec;arrangement=new ProjectArrangement(spec);durationFrames=Math.max(durationFrames,Math.round(spec.duration*RATE));bpm=(int)Math.round(spec.bpm);}
    public synchronized void setPan(int track,float value){pan[track]=Math.max(-1,Math.min(1,value));}
    private ProjectSpec.Region regionAt(long frame){if(project!=null)for(int i=0;i<project.regions.size();i++){ProjectSpec.Region r=project.regions.get(i);if(frame>=Math.round(r.start*RATE)&&frame<Math.round(r.end*RATE))return r;}return null;}
    public synchronized int pendingRegion(){return pendingRegion;}
    public synchronized void selectRegion(int index){if(project==null||index<0||index>=project.regions.size())return;ProjectSpec.Region target=project.regions.get(index),from=regionAt(cursor);if(playing&&from!=null){if(pendingRegion==index){manualBoundary=-1;pendingRegion=-1;return;}manualBoundary=Math.round(from.end*RATE);manualTarget=Math.round(target.start*RATE);pendingRegion=index;hintSources(manualTarget);}else seek(target.start);}
    private void hintSources(long frame){if(imported!=null&&frame>=0)for(PcmTrack track:imported)track.hint(frame);}
    public synchronized void closeImported(){if(imported!=null){for(PcmTrack track:imported)track.close();imported=null;}buffering=false;}
    public synchronized boolean isBuffering(){return buffering;}
    public synchronized boolean sourceFailed(){if(imported!=null)for(PcmTrack track:imported)if(track.failed())return true;return false;}
    private boolean prepareSources(int frames){boolean ready=true;if(imported!=null)for(PcmTrack track:imported)ready=track.prepare(cursor,frames)&&ready;return ready;}
    private float clickGain, padGain, padLevel=.7f;
    public synchronized void play() { if(cursor>=durationFrames){cursor=0;if(arrangement!=null)arrangement.reset();} playing=true; }
    public synchronized void pause() { playing=false; }
    public synchronized void stop() { playing=false; cursor=0;manualBoundary=-1;pendingRegion=-1;if(arrangement!=null)arrangement.reset(); }
    public synchronized void silence() { playing=false; click=false; pad=false; }
    public synchronized void seek(double seconds) { cursor=Math.max(0,Math.min(durationFrames,(long)(seconds*RATE)));manualBoundary=-1;pendingRegion=-1;if(arrangement!=null)arrangement.manualSeek(regionAt(cursor));hintSources(cursor); }
    public synchronized void setLoop(boolean value) { loop=value; }
    public synchronized void setClick(boolean value) { if(value&&!click)clickFrame=0; click=value; }
    public synchronized void setPad(boolean value) { pad=value; }
    public synchronized void setPadLevel(float value) { padLevel=Math.max(0,Math.min(1,value)); }
    public synchronized float getPadLevel() { return padLevel; }
    public synchronized void setBpm(int value) { bpm=Math.max(20,Math.min(400,value)); }
    public synchronized void setGain(int index,float value) { if(index>=0&&index<target.length)target[index]=Math.max(0,Math.min(8,value)); }
    public synchronized boolean isPlaying() { return playing; }
    public synchronized boolean isLoop() { return loop; }
    public synchronized boolean isClick() { return click; }
    public synchronized boolean isPad() { return pad; }
    public synchronized int getBpm() { return bpm; }
    public synchronized double position() { return cursor/(double)RATE; }
    public synchronized float getGain(int index) { return target[index]; }
    private static double sine(double hz,long frame) { return Math.sin(2*Math.PI*hz*frame/RATE); }
    public synchronized void render(float[] stereo,int frames) {
        if(frames<0||stereo.length<frames*2)throw new IllegalArgumentException("Invalid frame count");
        final double beat=RATE*60.0/bpm;
        if(sourceFailed()){playing=false;buffering=false;}
        if(playing&&!loop)hintSources(manualBoundary>=0?manualTarget:arrangement==null?-1:arrangement.hint());
        boolean ready=!playing||prepareSources((int)Math.min(Math.min(frames,512),Math.max(1,durationFrames-cursor)));
        buffering=playing&&!ready;
        for(int i=0;i<frames;i++,auxiliary++,clickFrame++) {
            if(playing&&project!=null){long target=-1;if(manualBoundary>=0&&cursor>=manualBoundary){target=manualTarget;manualBoundary=-1;pendingRegion=-1;if(arrangement!=null)arrangement.manualSeek(regionAt(target));}else if(arrangement!=null)target=arrangement.target(cursor,loop||manualBoundary>=0);if(target>=0){cursor=target;ready=prepareSources((int)Math.min(Math.min(frames-i,512),Math.max(1,durationFrames-cursor)));buffering=!ready;if(!loop)hintSources(arrangement==null?-1:arrangement.hint());}}
            if(i>0&&i%512==0&&playing){ready=prepareSources((int)Math.min(Math.min(frames-i,512),Math.max(1,durationFrames-cursor)));buffering=!ready;}
            if(cursor>=durationFrames) { if(loop){cursor=0;ready=prepareSources((int)Math.min(Math.min(frames-i,512),durationFrames));buffering=playing&&!ready;}else{playing=false;buffering=false;} }
            for(int j=0;j<target.length;j++)gain[j]+=(target[j]-gain[j])*.002f;
            clickGain+=((click?1:0)-clickGain)*.004f;
            padGain+=((pad?padLevel:0)-padGain)*.00005f;
            double left=0,right=0;
            if(playing&&ready) {
              if(imported!=null){for(int track=0;track<imported.length;track++){left+=gain[track]*(pan[track]>0?1-pan[track]:1)*imported[track].sample(cursor,0);right+=gain[track]*(pan[track]<0?1+pan[track]:1)*imported[track].sample(cursor,1);}}else{
                double phase=(cursor%24000)/24000.0;
                double bass=.16*gain[0]*sine(110,cursor)*(.6+.4*Math.cos(2*Math.PI*phase));
                double keys=.09*gain[1]*(sine(220,cursor)+sine(277.1826,cursor)+sine(329.6276,cursor));
                double drums=.18*gain[2]*sine(65,cursor)*Math.exp(-phase*38);
                left=bass+keys*.65+drums;right=bass+keys+drums;
              }
                cursor++;
            }
            long clock=playing?Math.max(0,cursor-1):clickFrame;
            double inBeat=clock%beat;
            double clickSample=(playing&&buffering?0:1)*clickGain*.13*sine(((long)(clock/beat)%4==0)?1800:1200,clock)*Math.exp(-inBeat/160.0);
            double padSample=padGain*.085*(sine(261.6256,auxiliary)+.8*sine(329.6276,auxiliary)+.7*sine(391.9954,auxiliary)+.15*sine(523.2511,auxiliary));
            stereo[i*2]=(float)Math.max(-.9,Math.min(.9,left+clickSample+padSample));
            stereo[i*2+1]=(float)Math.max(-.9,Math.min(.9,right+clickSample+padSample));
        }
    }
}
