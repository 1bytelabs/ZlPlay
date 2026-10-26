import com.onebytelabs.zlplay.audio.DemoMixer;
import java.util.Arrays;
public final class MixerTest {
 private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
 public static void main(String[] args){
  float[] block=new float[9600];DemoMixer mixer=new DemoMixer();mixer.render(block,4800);check(Arrays.equals(block,new float[9600]),"idle is silent");
  mixer.play();mixer.render(block,4800);check(Math.abs(mixer.position()-.1)<.00001,"shared song clock");check(energy(block)>0,"three stems produce output");
  mixer.pause();double position=mixer.position();mixer.setClick(true);mixer.render(block,4800);check(mixer.position()==position,"standalone click does not start song");check(energy(block)>0,"standalone click produces output");
  mixer.setClick(false);mixer.setPad(true);mixer.render(block,4800);check(mixer.position()==position,"pad does not start song");check(energy(block)>0,"pad produces output");
  mixer.seek(89.99);mixer.setLoop(true);mixer.play();mixer.render(block,4800);check(mixer.position()<.2&&mixer.isPlaying(),"sample-accurate loop");
  mixer.setLoop(false);mixer.seek(89.99);mixer.render(block,4800);check(!mixer.isPlaying()&&mixer.position()==90,"end stops song");
  mixer.stop();check(mixer.position()==0,"stop resets song cursor");mixer.setBpm(999);check(mixer.getBpm()==400,"BPM clamp");mixer.setGain(0,-1);check(mixer.getGain(0)==0,"gain clamp");
  for(float value:block)check(Float.isFinite(value)&&Math.abs(value)<=.9f,"bounded finite output");
  DemoMixer a=new DemoMixer(),b=new DemoMixer();a.play();b.play();float[] whole=new float[9600],part=new float[4800];a.render(whole,4800);b.render(part,2400);check(Arrays.equals(Arrays.copyOf(whole,4800),part),"block boundary invariant");b.render(part,2400);check(Arrays.equals(Arrays.copyOfRange(whole,4800,9600),part),"continuous clock across blocks");
  System.out.println("PASS: silence, multitrack clock, pause, independent click/pad, loop, end, bounds and block continuity");
 }
 private static double energy(float[] buffer){double sum=0;for(float sample:buffer)sum+=sample*sample;return sum;}
}
