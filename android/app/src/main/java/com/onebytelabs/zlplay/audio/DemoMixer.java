package com.onebytelabs.zlplay.audio;
import com.onebytelabs.zlplay.project.ProjectSpec;

/** Shared-clock prototype mixer: synthetic fixture or imported PCM; no disk I/O in render. */
public final class DemoMixer {
    public static final int RATE = 48000;
    public static final int DURATION = RATE * 90;
    private volatile long cursor;private long auxiliary, clickFrame;
    private volatile boolean playing, loop, click, pad, buffering;
    private boolean[] muted=new boolean[3],soloed=new boolean[3];private float[] trackMeters=new float[3];
    private final boolean[] busMute=new boolean[5],busSolo=new boolean[5];private final float[] busPan=new float[5],busMeters=new float[5];private final double[] busL=new double[5],busR=new double[5];private final int[] routes={-1,-1,-1,-1,-1};
    private int bus(int kind){return -kind-1;}
    public synchronized boolean isMuted(int kind){return kind>=0?muted[kind]:busMute[bus(kind)]||specialMuted(kind);}
    public synchronized boolean isSolo(int kind){return kind>=0?soloed[kind]:busSolo[bus(kind)]||specialSolo(kind);}
    public synchronized void setMuted(int kind,boolean on){if(kind>=0)muted[kind]=on;else{busMute[bus(kind)]=on;if(!on&&(kind==-2||kind==-3))for(int i=0;i<roles.length;i++)if(roles[i]==(kind==-2?2:1))muted[i]=false;}}
    private boolean specialMuted(int kind){if(kind!=-2&&kind!=-3)return false;boolean found=false;for(int i=0;i<roles.length;i++)if(roles[i]==(kind==-2?2:1)){found=true;if(!muted[i])return false;}return found;}
    public synchronized void setSolo(int kind,boolean on){if(kind>=0)soloed[kind]=on;else{busSolo[bus(kind)]=on;if(!on&&(kind==-2||kind==-3))for(int i=0;i<roles.length;i++)if(roles[i]==(kind==-2?2:1))soloed[i]=false;}}
    private boolean specialSolo(int kind){if(kind!=-2&&kind!=-3)return false;for(int i=0;i<roles.length;i++)if(roles[i]==(kind==-2?2:1)&&soloed[i])return true;return false;}
    public synchronized float getPan(int kind){return kind>=0?pan[kind]:busPan[bus(kind)];}
    public synchronized void setChannelPan(int kind,float value){value=Math.max(-1,Math.min(1,value));if(kind>=0)pan[kind]=value;else busPan[bus(kind)]=value;}
    public float meter(int kind){return kind>=0?trackMeters[kind]:busMeters[bus(kind)];}
    /** -1 follows Master (Master itself uses stereo 1/2); 0..15 mono; 100+n stereo n/n+1. */
    public synchronized void setRoute(int bus,int route){if(bus<0||bus>4||route< -1||route>115||route>=16&&route<100)throw new IllegalArgumentException("Route");routes[bus]=route;}
    public synchronized int getRoute(int bus){return routes[bus];}
    private static double panLeft(float pan){return pan>0?1-pan:1;}
    private static double panRight(float pan){return pan<0?1+pan:1;}
    private static void output(float[] out,int base,int channels,int route,double left,double right){if(route<0){out[base]+=(float)(channels==1?(left+right)*.5:left);if(channels>1)out[base+1]+=(float)right;}else if(route>=100){int c=route-100;if(c<channels)out[base+c]+=(float)left;if(c+1<channels)out[base+c+1]+=(float)right;}else if(route<channels)out[base+route]+=(float)((left+right)*.5);}
    private volatile boolean preferProjectClick=true,accentEnabled=true;
    public synchronized void setAccent(boolean value){accentEnabled=value;}
    public boolean isAccent(){return accentEnabled;}
    public synchronized void prepareProjectChange(boolean keepPad){playing=false;click=false;loop=false;if(!keepPad)pad=false;}
    private volatile int bpm = 120,transpose,padNote;
    private int[] roles=new int[3];private boolean[] pitchEnabled={true,true,false};
    private float vsLevel=1,clickLevel=1,guideLevel=1,masterLevel=1,soloMix=1;
    private volatile long loopFrom,loopTo=DURATION;private volatile PcmTrack[] padBank;private long padFrame;private int previousPad=-1;private float noteFade=1;
    private volatile PcmTrack[] imported;
    private ProjectSpec project;private ProjectArrangement arrangement;private long manualBoundary=-1,manualTarget;private volatile int pendingRegion=-1;
    private PcmTrack guidePreview;private int guideIndex=-1;private long previewStart=-1,previewEnd,previewSource;private boolean previewReady;
    private float[] pan=new float[3];private int[] trackRoutes={-1,-1,-1};
    public synchronized int trackRoute(int index){return trackRoutes[index];}
    public synchronized void setTrackRoute(int index,int route){if(route< -1||route>115||route>=16&&route<100)throw new IllegalArgumentException("Track route");trackRoutes[index]=route;}
    private volatile long durationFrames=DURATION;
    private float[] target = { .55f, .55f, .4f }, gain = new float[3];
    public double duration(){return durationFrames/(double)RATE;}
    public boolean hasImported(){return imported!=null;}
    public synchronized int trackCount(){return target.length;}
    public synchronized String trackName(int index){return imported==null?null:imported[index].name;}
    public synchronized void load(PcmTrack[] tracks){
        if(tracks.length==0||tracks.length>32)throw new IllegalArgumentException("Invalid track count");
        PcmTrack[] sources=tracks.clone();double duration=0;for(PcmTrack t:sources)duration=Math.max(duration,t.duration());
        closeImported();project=null;arrangement=null;manualBoundary=-1;pendingRegion=-1;pan=new float[tracks.length];trackRoutes=new int[tracks.length];java.util.Arrays.fill(trackRoutes,-1);muted=new boolean[tracks.length];soloed=new boolean[tracks.length];trackMeters=new float[tracks.length];imported=sources;target=new float[tracks.length];java.util.Arrays.fill(target,.7f);gain=new float[tracks.length];durationFrames=(long)Math.ceil(duration*RATE);cursor=0;playing=false;loop=false;loopFrom=0;loopTo=durationFrames;transpose=0;roles=new int[tracks.length];pitchEnabled=new boolean[tracks.length];for(int i=0;i<tracks.length;i++){roles[i]=ProjectSpec.isClickName(tracks[i].name)?2:ProjectSpec.isGuideName(tracks[i].name)?1:0;pitchEnabled[i]=ProjectSpec.defaultPitch(tracks[i].name);}
    }
    public synchronized void configureProject(ProjectSpec spec){java.util.Arrays.fill(busMute,false);java.util.Arrays.fill(busSolo,false);java.util.Arrays.fill(busPan,0);project=spec;arrangement=new ProjectArrangement(spec);durationFrames=Math.max(durationFrames,Math.round(spec.duration*RATE));bpm=(int)Math.round(spec.bpm);for(int i=0;i<roles.length&&i<spec.tracks.size();i++){ProjectSpec.Track t=spec.tracks.get(i);muted[i]=t.mute;soloed[i]=t.solo;roles[i]=t.isClick()?2:t.isGuide()?1:0;pitchEnabled[i]=roles[i]!=2&&(t.pitchEnabled!=null?t.pitchEnabled:ProjectSpec.defaultPitch(t.name));}}
    public synchronized void configureGuidePreview(PcmTrack source,int index){guidePreview=source;guideIndex=index;}
    private void updatePreview(){previewStart=-1;previewReady=false;if(guidePreview==null||guideIndex<0||loop||!playing||project==null)return;long boundary=manualBoundary>=0?manualBoundary:arrangement==null?-1:arrangement.previewBoundary(cursor),target=manualBoundary>=0?manualTarget:arrangement==null?-1:arrangement.hint();if(boundary<0||target<=0)return;long previous=0;for(int i=0;i<project.regions.size();i++){long start=Math.round(project.regions.get(i).start*RATE);if(start<target&&start>previous)previous=start;}long source=Math.max(Math.max(0,target-8L*RATE),previous),lead=target-source;if(lead<=RATE*.03)return;previewStart=boundary-lead;previewEnd=boundary;previewSource=source;guidePreview.hint(source);if(cursor<previewEnd&&cursor>=previewStart)previewReady=guidePreview.prepare(source+cursor-previewStart,512);}
    public synchronized void setPan(int track,float value){pan[track]=Math.max(-1,Math.min(1,value));}
    private ProjectSpec.Region regionAt(long frame){if(project!=null)for(int i=project.regions.size()-1;i>=0;i--){ProjectSpec.Region r=project.regions.get(i);if(frame>=Math.round(r.start*RATE)&&frame<Math.round(r.end*RATE))return r;}return null;}
    public int pendingRegion(){return pendingRegion;}
    public synchronized void selectRegion(int index){if(project==null||index<0||index>=project.regions.size())return;ProjectSpec.Region target=project.regions.get(index),from=regionAt(cursor);if(playing&&from!=null){if(pendingRegion==index){manualBoundary=-1;pendingRegion=-1;return;}manualBoundary=Math.round(from.end*RATE);manualTarget=Math.round(target.start*RATE);pendingRegion=index;hintSources(manualTarget);}else seek(target.start);}
    private void hintSources(long frame){if(imported!=null&&frame>=0)for(PcmTrack track:imported)track.hint(frame);}
    public synchronized void closeImported(){if(guidePreview!=null){guidePreview.close();guidePreview=null;}guideIndex=-1;previewStart=-1;if(imported!=null){for(PcmTrack track:imported)track.close();imported=null;}buffering=false;}
    public boolean isBuffering(){return buffering;}
    public boolean sourceFailed(){PcmTrack[] sources=imported;if(sources!=null)for(PcmTrack track:sources)if(track.failed())return true;return false;}
    private final float[][] sourceBlocks=new float[32][1024];private final float[] previewBlock=new float[1024];private long preparedCursor;
    private boolean prepareSources(int frames){boolean ready=true;preparedCursor=cursor;if(imported!=null)for(int i=0;i<imported.length;i++){boolean needed=!click||soloMix>1e-4||roles[i]==2;if(needed){boolean available=imported[i].prepare(cursor,frames);ready=available&&ready;if(available)imported[i].readStereo(cursor,frames,sourceBlocks[i],0);}}if(previewReady&&guidePreview!=null)guidePreview.readStereo(previewSource+cursor-previewStart,frames,previewBlock,0);return ready;}
    private float clickGain, padGain, padLevel=.7f;
    public synchronized void play() { if(!playing){click=false;} if(cursor>=durationFrames){cursor=0;if(arrangement!=null)arrangement.reset();} playing=true; }
    public synchronized void pause() { playing=false; }
    public synchronized void stop() { playing=false;loop=false; cursor=0;manualBoundary=-1;pendingRegion=-1;if(arrangement!=null)arrangement.reset(); }
    public synchronized void silence() { playing=false; click=false; pad=false; }
    public synchronized void seek(double seconds) { cursor=Math.max(0,Math.min(durationFrames,(long)(seconds*RATE)));manualBoundary=-1;pendingRegion=-1;loop=false;if(arrangement!=null)arrangement.manualSeek(regionAt(cursor));hintSources(cursor); }
    public synchronized void setLoop(boolean value) {if(value&&project!=null&&regionAt(cursor)==null){loop=false;return;}loop=value;if(value){ProjectSpec.Region r=regionAt(cursor);loopFrom=r==null?0:Math.round(r.start*RATE);loopTo=r==null?durationFrames:Math.round(r.end*RATE);hintSources(loopFrom);}}
    public double loopStart(){return loopFrom/(double)RATE;}
    public double loopEnd(){return loopTo/(double)RATE;}
    public synchronized void setClick(boolean value) { if(value&&!click)clickFrame=0;click=value;if(playing)setLoop(value); }
    public synchronized void setPad(boolean value) {if(value&&!pad)padFrame=0;pad=value;}
    public synchronized PcmTrack[] replacePads(PcmTrack[] bank){if(bank!=null&&bank.length!=12)throw new IllegalArgumentException("Twelve pads required");PcmTrack[] old=padBank;padBank=bank;pad=false;padFrame=0;previousPad=-1;return old;}
    public boolean hasPads(){return padBank!=null;}
    public synchronized boolean hasPad(int note){return note>=0&&note<12&&padBank!=null&&padBank[note]!=null;}
    public int getPadNote(){return padNote;}
    public synchronized void setPadNote(int note){if(note<0||note>11)throw new IllegalArgumentException("Pad note");if(note!=padNote){previousPad=padNote;padNote=note;noteFade=0;if(padBank!=null)padBank[note].hint(padFrame);}}
    public synchronized void setVsLevel(float level){vsLevel=Math.max(0,Math.min(2,level));}
    public synchronized float getVsLevel(){return vsLevel;}
    public synchronized void setClickLevel(float level){clickLevel=Math.max(0,Math.min(2,level));}
    public synchronized float getClickLevel(){return clickLevel;}
    public synchronized void setGuideLevel(float level){guideLevel=Math.max(0,Math.min(2,level));}
    public synchronized float getGuideLevel(){return guideLevel;}
    public synchronized void setMasterLevel(float level){masterLevel=Math.max(0,Math.min(2,level));}
    public synchronized float getMasterLevel(){return masterLevel;}
    public synchronized int role(int i){return roles[i];}
    public synchronized boolean pitchEnabled(int i){return pitchEnabled[i];}
    public int getTranspose(){return transpose;}
    public synchronized void setTranspose(int steps){if(Math.abs(steps)>12)throw new IllegalArgumentException("Transpose range");for(int i=0;i<target.length;i++)if(imported!=null)imported[i].setPitch(pitchEnabled[i]?steps:0);if(guidePreview!=null&&guideIndex>=0)guidePreview.setPitch(pitchEnabled[guideIndex]?steps:0);transpose=steps;}
    public synchronized void setPitchEnabled(int i,boolean enabled){if(roles[i]==2)enabled=false;pitchEnabled[i]=enabled;if(imported!=null)imported[i].setPitch(enabled?transpose:0);if(guidePreview!=null&&i==guideIndex)guidePreview.setPitch(enabled?transpose:0);}
    public synchronized boolean hasProjectClick(){return projectClick();}
    public synchronized boolean usesProjectClick(){return preferProjectClick&&projectClick();}
    public synchronized void useProjectClick(boolean enabled){preferProjectClick=enabled&&projectClick();}
    public synchronized boolean audibleProjectClick(){for(int i=0;i<roles.length;i++)if(roles[i]==2&&!muted[i]&&target[i]>0)return true;return false;}
    private boolean projectClick(){for(int role:roles)if(role==2)return true;return false;}
    public synchronized void setPadLevel(float value) { padLevel=Math.max(0,Math.min(2,value)); }
    public synchronized float getPadLevel() { return padLevel; }
    public synchronized void setBpm(int value) { bpm=Math.max(20,Math.min(400,value)); }
    public synchronized void setGain(int index,float value) { if(index>=0&&index<target.length)target[index]=Math.max(0,Math.min(8,value)); }
    public boolean isPlaying() { return playing; }
    public boolean isLoop() { return loop; }
    public boolean isClick() { return click; }
    public boolean isPad() { return pad; }
    public int getBpm() { return bpm; }
    public double position() { return cursor/(double)RATE; }
    public synchronized float getGain(int index) { return target[index]; }
    private final double[] renderL=new double[32],renderR=new double[32];private final int[] renderBus=new int[32];
    private static float padSample(PcmTrack source,long frame,int channel){long length=Math.max(1,Math.round(source.duration()*RATE));if(frame>=length)frame%=length;long fade=Math.min(1920,length/4),remaining=length-frame;float value=source.sample(frame,channel);if(fade>0&&remaining<=fade){float blend=1-remaining/(float)fade;value=value*(1-blend)+source.sample(fade-remaining,channel)*blend;}return value;}
    private static double sine(double hz,long frame) { return Math.sin(2*Math.PI*hz*frame/RATE); }
    public synchronized void render(float[] stereo,int frames){renderRouted(stereo,frames,2);}
    public synchronized void renderRouted(float[] stereo,int frames,int channels) {renderRoutedInto(stereo,0,frames,channels);}
    public synchronized void renderRoutedInto(float[] stereo,int frameOffset,int frames,int channels) {
        if(frames<0||channels<1||channels>16||frameOffset<0||stereo.length<(frameOffset+frames)*channels)throw new IllegalArgumentException("Invalid frame count");
        boolean anySolo=false,anyBusSolo=false;for(boolean value:soloed)anySolo|=value;for(int b=0;b<5;b++)anyBusSolo|=busSolo[b];for(int j=0;j<trackMeters.length;j++)trackMeters[j]*=.75f;for(int b=0;b<5;b++)busMeters[b]*=.75f;
        final double beat=RATE*60.0/bpm*(project==null?1:4.0/project.denominator);final double padFactor=Math.pow(2,padNote/12.0);final boolean hasProjectClick=projectClick();for(int track=0;track<target.length;track++){int roleBus=roles[track]==2?1:roles[track]==1?2:4;renderBus[track]=roleBus;boolean allow=(!anyBusSolo||busSolo[roleBus]||(busSolo[0]&&trackRoutes[track]<0&&routes[roleBus]<0))&&!muted[track]&&(!anySolo||soloed[track]);double level=roles[track]==2?(preferProjectClick?clickLevel:0):roles[track]==1?((loop||click)?0:guideLevel):(trackRoutes[track]<0?vsLevel:1);renderL[track]=allow?level*panLeft(pan[track]):0;renderR[track]=allow?level*panRight(pan[track]):0;}
        if(sourceFailed()){playing=false;buffering=false;}
        if(playing&&!loop)hintSources(manualBoundary>=0?manualTarget:arrangement==null?-1:arrangement.hint());
        updatePreview();
        boolean ready=!playing||prepareSources((int)Math.min(Math.min(frames,512),Math.max(1,durationFrames-cursor)));
        buffering=playing&&!ready;
        boolean renderPad=pad||padGain>1e-6;boolean padsReady=padBank==null||!renderPad||padBank[padNote].prepare(padFrame,Math.min(frames,512)),previousReady=renderPad&&padBank!=null&&previousPad>=0&&padBank[previousPad].prepare(padFrame,Math.min(frames,512));
        for(int i=0;i<frames;i++,auxiliary++,clickFrame++) {
            if(playing&&project!=null){long target=-1;if(manualBoundary>=0&&cursor>=manualBoundary){target=manualTarget;manualBoundary=-1;pendingRegion=-1;loop=false;if(arrangement!=null)arrangement.manualSeek(regionAt(target));}else if(arrangement!=null)target=arrangement.target(cursor,loop||manualBoundary>=0);if(target>=0){cursor=target;updatePreview();ready=prepareSources((int)Math.min(Math.min(frames-i,512),Math.max(1,durationFrames-cursor)));buffering=!ready;if(!loop)hintSources(arrangement==null?-1:arrangement.hint());}}
            if(i>0&&i%512==0&&playing){updatePreview();ready=prepareSources((int)Math.min(Math.min(frames-i,512),Math.max(1,durationFrames-cursor)));buffering=!ready;}
            if(loop&&cursor>=loopTo){cursor=loopFrom;ready=prepareSources((int)Math.min(512,loopTo-cursor));buffering=playing&&!ready;}
            if(cursor>=durationFrames) { if(loop){cursor=loopFrom;ready=prepareSources((int)Math.min(Math.min(frames-i,512),durationFrames));buffering=playing&&!ready;}else{playing=false;buffering=false;} }
            for(int j=0;j<target.length;j++)gain[j]+=(target[j]-gain[j])*.002f;
            boolean useNative=(click||playing&&project!=null&&!project.tracks.isEmpty())&&(!playing||!preferProjectClick||!hasProjectClick);clickGain+=((useNative?clickLevel:0)-clickGain)*.004f;soloMix=Math.max(0,Math.min(1,soloMix+(click&&playing?-1:1)/(1.5f*RATE)));
            padGain+=((pad?padLevel:0)-padGain)*.00005f;
            int base=(frameOffset+i)*channels;for(int c=0;c<channels;c++)stereo[base+c]=0;double left=0,right=0;for(int b=0;b<5;b++){busL[b]=0;busR[b]=0;}
            if(playing&&ready) {
              if(imported!=null){for(int track=0;track<imported.length;track++){boolean preview=track==guideIndex&&previewReady&&cursor>=previewStart&&cursor<previewEnd;double scale=roles[track]==2?gain[track]:gain[track]*soloMix;int sample=(int)(cursor-preparedCursor)*2;float[] values=preview?previewBlock:sourceBlocks[track];double tl=renderL[track]==0?0:scale*renderL[track]*values[sample],tr=renderR[track]==0?0:scale*renderR[track]*values[sample+1];trackMeters[track]=Math.max(trackMeters[track],(float)Math.max(Math.abs(tl),Math.abs(tr)));int b=renderBus[track];if(trackRoutes[track]<0){busL[b]+=tl;busR[b]+=tr;}else output(stereo,base,channels,trackRoutes[track],tl,tr);}}else{
                double phase=(cursor%24000)/24000.0;
                double bass=.16*gain[0]*sine(110,cursor)*(.6+.4*Math.cos(2*Math.PI*phase));
                double keys=.09*gain[1]*(sine(220,cursor)+sine(277.1826,cursor)+sine(329.6276,cursor));
                double drums=.18*gain[2]*sine(65,cursor)*Math.exp(-phase*38);
                for(int track=0;track<3;track++){double sample=track==0?bass:track==1?keys:drums,tl=0,tr=0;if(!muted[track]&&(!anySolo||soloed[track])){tl=sample*(track==1?.65:1)*panLeft(pan[track])*vsLevel*soloMix;tr=sample*panRight(pan[track])*vsLevel*soloMix;}left+=tl;right+=tr;trackMeters[track]=Math.max(trackMeters[track],(float)Math.max(Math.abs(tl),Math.abs(tr)));}
              }
                cursor++;
            }
            long clock=playing?Math.max(0,cursor-1):clickFrame;
            double inBeat=clock%beat;
            double clickOutput=(playing&&buffering?0:1)*clickGain;int meter=project==null?4:project.numerator;double clickLeft=clickOutput==0?0:clickOutput*NativeClickSound.sample(clock,beat,meter,0,accentEnabled),clickRight=clickOutput==0?0:clickOutput*NativeClickSound.sample(clock,beat,meter,1,accentEnabled);
            double factor=padFactor,padSample=padBank==null&&padGain>0?padGain*.085*(sine(261.6256*factor,auxiliary)+.8*sine(329.6276*factor,auxiliary)+.7*sine(391.9954*factor,auxiliary)+.15*sine(523.2511*factor,auxiliary)):0,padLeft=padSample,padRight=padSample;
            if(padBank!=null&&renderPad){long padLength=Math.max(1,Math.round(padBank[padNote].duration()*RATE));if(padFrame>=padLength){padFrame=Math.min(1920,padLength/4);padsReady=padBank[padNote].prepare(padFrame,Math.min(512,frames-i));}if(i%512==0){padsReady=padBank[padNote].prepare(padFrame,Math.min(512,frames-i));if(previousPad>=0)previousReady=padBank[previousPad].prepare(padFrame,Math.min(512,frames-i));}if(padsReady){noteFade=Math.min(1,noteFade+1f/(RATE*.35f));padLeft=padGain*padSample(padBank[padNote],padFrame,0)*noteFade;padRight=padGain*padSample(padBank[padNote],padFrame,1)*noteFade;if(previousPad>=0&&noteFade<1){padLeft+=padGain*padSample(padBank[previousPad],padFrame,0)*(1-noteFade);padRight+=padGain*padSample(padBank[previousPad],padFrame,1)*(1-noteFade);}else previousPad=-1;padFrame++;}else if(previousPad>=0&&previousReady){padLeft=padGain*padBank[previousPad].sample(padFrame,0);padRight=padGain*padBank[previousPad].sample(padFrame,1);padFrame++;}else{padLeft=padRight=0;}padBank[padNote].hint((padFrame+8192)%padLength);}

            busL[4]+=left;busR[4]+=right;busL[1]+=clickLeft;busR[1]+=clickRight;busL[3]+=padLeft;busR[3]+=padRight;
            for(int b=1;b<5;b++){double bl=busL[b]*panLeft(busPan[b]),br=busR[b]*panRight(busPan[b]);if(busMute[b]||(anyBusSolo&&!busSolo[b]&&!(busSolo[0]&&routes[b]<0))||(anySolo&&(b==3||b==1)&&!busSolo[b]&&!(b==1&&specialSolo(-2))))bl=br=0;busMeters[b]=Math.max(busMeters[b],(float)Math.max(Math.abs(bl),Math.abs(br)));if(routes[b]<0){busL[0]+=bl;busR[0]+=br;}else output(stereo,base,channels,routes[b],bl,br);}
            double ml=busL[0]*masterLevel*panLeft(busPan[0]),mr=busR[0]*masterLevel*panRight(busPan[0]);if(busMute[0])ml=mr=0;busMeters[0]=Math.max(busMeters[0],(float)Math.max(Math.abs(ml),Math.abs(mr)));output(stereo,base,channels,routes[0],ml,mr);for(int c=0;c<channels;c++)stereo[base+c]=(float)Math.max(-.9,Math.min(.9,stereo[base+c]));
        }
    }
}
