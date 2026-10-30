import com.onebytelabs.zlplay.audio.*;

/** Bulk reads must preserve resampling, clip offsets, overlaps and silence at EOF. */
public final class BlockReadTest {
    private static void equal(float expected,float actual){if(Math.abs(expected-actual)>2e-6f)throw new AssertionError(expected+" != "+actual);}
    public static void main(String[] args){
        float[] pcm=new float[44100*2];
        for(int i=0;i<44100;i++){pcm[i*2]=(float)Math.sin(i*.013);pcm[i*2+1]=(float)Math.cos(i*.017);}
        PcmTrack source=new PcmTrack("stereo",44100,2,pcm);
        TimelineTrack track=new TimelineTrack("overlaps",new TimelineTrack.Clip[]{
            new TimelineTrack.Clip(source,.1,.05,.7,.7,-.3),
            new TimelineTrack.Clip(source,.4,.15,.5,.3,.2)
        },1.1);
        for(PcmTrack t:new PcmTrack[]{source,track})for(long frame:new long[]{0,4700,4799,4800,8190,19000,38399,43190,47990,52790}){
            float[] out=new float[1030];java.util.Arrays.fill(out,-99);
            if(!t.prepare(frame,512))throw new AssertionError("prepare");
            t.readStereo(frame,512,out,4);
            equal(-99,out[3]);equal(-99,out[1028]);
            for(int i=0;i<512;i++)for(int c=0;c<2;c++)equal(t.sample(frame+i,c),out[4+i*2+c]);
        }
        PcmTrack mono=new PcmTrack("mono",48000,1,new float[]{.1f,.2f,.3f});float[] out=new float[10];
        mono.readStereo(0,5,out,0);for(int i=0;i<5;i++){equal(mono.sample(i,0),out[i*2]);equal(out[i*2],out[i*2+1]);}
        System.out.println("PASS: block PCM matches scalar samples for resampling, stereo/mono, offsets, overlaps, quantum boundaries and EOF");
    }
}
