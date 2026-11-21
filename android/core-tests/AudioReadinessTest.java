import com.onebytelabs.zlplay.audio.*;

/** Output retries preparation without consuming the clock or fabricating PCM. */
public final class AudioReadinessTest {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static final class Deferred extends PcmTrack {
        boolean ready;int reads;int pitch;
        Deferred(String name){super(name,48000,2);}
        @Override public double duration(){return 10;}
        @Override public boolean prepare(long frame,int count){return ready;}
        @Override public float sample(long frame,int channel){return .1f;}
        @Override public void readStereo(long frame,int count,float[] out,int offset){reads++;super.readStereo(frame,count,out,offset);}
        @Override public void setPitch(int value){pitch=value;ready=false;}
    }
    public static void main(String[] args){
        DemoMixer mixer=new DemoMixer();Deferred first=new Deferred("Keys"),second=new Deferred("Bass");
        mixer.load(new PcmTrack[]{first,second});mixer.play();first.ready=true;
        check(!mixer.prepareAudio(256),"all musical sources must be ready together");
        check(first.reads==0&&second.reads==0&&mixer.position()==0,"checking readiness never renders or advances time");
        second.ready=true;check(mixer.prepareAudio(256),"ready group can render");
        float[] block=new float[512];mixer.render(block,256);double position=mixer.position();
        check(position>0&&block[510]>0,"ready sources produce nonzero PCM");
        mixer.setTranspose(3);check(first.pitch==3&&second.pitch==3&&!mixer.prepareAudio(256),"new pitch generation waits together");
        check(mixer.position()==position&&mixer.sourceStarvationFrames()==0,"preparation does not insert silent frames");
        mixer.pause();check(mixer.prepareAudio(256),"pause releases a waiting output worker");
        first.ready=second.ready=true;mixer.play();check(mixer.prepareAudio(256),"ready transposition resumes");
        mixer.render(block,256);check(mixer.position()>position&&mixer.sourceStarvationFrames()==0,"resumed musical clock is continuous");
        Deferred pad=new Deferred("Pad");PcmTrack[] pads=new PcmTrack[12];java.util.Arrays.fill(pads,pad);mixer.replacePads(pads);mixer.setPad(true);
        check(mixer.prepareAudio(256),"pending auxiliary pad does not block musical stems");
        mixer.pause();check(!mixer.prepareAudio(256),"standalone pad waits for its source instead of emitting a gap");
        pad.ready=true;check(mixer.prepareAudio(256),"standalone pad ready");
        mixer.render(block,256);check(mixer.padStarvationFrames()==0,"ready pad renders without starvation");
        pad.ready=false;mixer.silence();
        check(mixer.prepareAudio(256),"stopping pad is not held up by its unavailable fading tail");
        mixer.setPad(true);mixer.setClick(true);check(mixer.prepareAudio(256),"pending pad does not interrupt standalone click");
        mixer.silence();
        mixer.replacePads(null);check(mixer.prepareAudio(256),"removing pad bank unblocks preparation");
        System.out.println("PASS: cache readiness, atomic pitch sources, unchanged clock, pause and standalone pad preparation");
    }
}
