import com.onebytelabs.zlplay.audio.*;
import com.onebytelabs.zlplay.project.ProjectSpec;
public final class MobileEditingTest {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static DemoMixer mixer(ProjectSpec spec){
        float[] pcm=new float[48000*2];for(int i=0;i<pcm.length;i++)pcm[i]=(float)(.12*Math.sin(2*Math.PI*1000*i/48000));
        DemoMixer mixer=new DemoMixer();mixer.load(new PcmTrack[]{new PcmTrack("Piano",48000,1,pcm){@Override public void setPitch(int steps){}}});mixer.configureProject(spec);mixer.setGain(0,.5f);mixer.setMuted(-2,true);return mixer;
    }
    private static ProjectSpec project(){ProjectSpec spec=new ProjectSpec();spec.originalKey="C";spec.duration=2;ProjectSpec.Track track=new ProjectSpec.Track();track.name="Piano";spec.tracks.add(track);return spec;}
    private static double energy(float[] out){double sum=0;for(float n:out)sum+=n*n;return sum;}
    public static void main(String[] args){
        ProjectSpec spec=project();spec.padMarkers.add(new ProjectSpec.PadMarker(.1,2));spec.padMarkers.add(new ProjectSpec.PadMarker(.3,5));
        DemoMixer pad=mixer(spec);float[] block=new float[14400];pad.play();pad.render(block,7200);check(pad.getPadNote()==2,"pad marker at the exact timeline position");
        pad.seek(.4);check(pad.getPadNote()==5,"seek resolves preceding pad marker");pad.seek(0);check(pad.getPadNote()==0,"backward seek restores original pad");pad.setTranspose(3);check(pad.getPadNote()==3,"initial pad follows transposition");pad.seek(.2);check(pad.getPadNote()==5,"pad marker follows transposition");
        ProjectSpec baseline=project(),filtered=project();filtered.tracks.get(0).eq=new double[][]{ClipEq.coefficients("lowpass",100,0,.707)};
        DemoMixer plain=mixer(baseline),eq=mixer(filtered);plain.play();eq.play();float[] a=new float[9600],b=new float[9600];
        for(int i=0;i<4;i++){plain.render(a,4800);eq.render(b,4800);}check(energy(a)>1,"test signal audible");check(energy(b)<energy(a)*.001,"channel EQ filters the full track");
        ProjectSpec trimmed=project();trimmed.busTrim[0]=.5;DemoMixer master=mixer(trimmed);master.play();for(int i=0;i<4;i++)master.render(b,4800);check(Math.abs(energy(b)/energy(a)-.25)<.001,"master trim changes output before clipping");
        filtered.tracks.get(0).eq=null;eq.configureEffects(filtered);eq.seek(.5);plain.seek(.5);eq.render(b,4800);plain.render(a,4800);check(Math.abs(energy(b)/energy(a)-1)<.001,"disabling EQ restores signal after seek");
        float[] nextSong=new float[96000];for(int i=0;i<nextSong.length;i++)nextSong[i]=(float)(.12*Math.sin(2*Math.PI*1000*i/48000));
        master.load(new PcmTrack[]{new PcmTrack("Piano",48000,1,nextSong)});master.setGain(0,.5f);master.play();for(int i=0;i<4;i++)master.render(b,4800);check(Math.abs(energy(b)/energy(a)-1)<.001,"channel effects do not leak into another imported song");
        DemoMixer nativeOnly=mixer(project());nativeOnly.setMuted(-2,false);nativeOnly.setVsLevel(0);nativeOnly.play();nativeOnly.render(a,4800);check(energy(a)>0,"native click starts enabled without an imported click track");
        nativeOnly.useProjectClick(true);for(int i=0;i<4;i++)nativeOnly.render(a,4800);check(nativeOnly.usesProjectClick()&&energy(a)<1e-10,"native click can be disabled without a project click track");
        nativeOnly.useProjectClick(false);double clickEnergy=0;for(int i=0;i<5;i++){nativeOnly.render(a,4800);clickEnergy+=energy(a);}check(!nativeOnly.usesProjectClick()&&clickEnergy>0,"native click can be re-enabled");
        DemoMixer generatedOnly=new DemoMixer();generatedOnly.load(new PcmTrack[]{new PcmTrack("Click",48000,1,new float[96000])});ProjectSpec clickProject=new ProjectSpec();clickProject.duration=2;ProjectSpec.Track clickTrack=new ProjectSpec.Track();clickTrack.name="Click";ProjectSpec.Clip generatedClip=new ProjectSpec.Clip();generatedClip.nativeClick=true;clickTrack.clips.add(generatedClip);clickProject.tracks.add(clickTrack);generatedOnly.configureProject(clickProject);check(!generatedOnly.hasProjectClick(),"Reaper generated click is not a recorded audio stem");generatedOnly.useProjectClick(true);generatedOnly.play();generatedOnly.render(a,4800);check(energy(a)==0,"native off also silences a converted Reaper generator without a recorded stem");
        System.out.println("PASS: pad changes, seek, transpose, channel EQ, bypass and master trim");
    }
}
