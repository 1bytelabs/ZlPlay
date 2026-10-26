import com.onebytelabs.zlplay.audio.*;
import com.onebytelabs.zlplay.project.*;
public final class ProjectTest {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static String item(String file){return "<ITEM\nPOSITION 0.5\nSOFFS 0.25\nLENGTH 1\nPLAYRATE 1 1 0\n<SOURCE WAVE\nFILE \""+file+"\"\n>\n>\n";}
 public static void main(String[] args)throws Exception{
  String source="<REAPER_PROJECT 0.1\nTEMPO 142 4 4\nMARKER 1 0 \"Verso 1\" 1 16777471 R\nMARKER 1 1 \"\" 0\nMARKER 2 1 \"Verso 1\" 1 0 R\nMARKER 2 1.5 \"\" 0\n<TRACK\nNAME \"Keys\"\nVOLPAN 0.8 -0.25\nISBUS 1 1\n>\n<TRACK\nNAME \"Piano\"\nVOLPAN 0.5 0\n"+item("Media\\piano.wav")+"ISBUS 2 -1\n>\n>";
  ProjectSpec spec=RppReader.parse(source,"Fixture");check(spec.tracks.size()==1,"folder is not an audio track");check(Math.abs(spec.tracks.get(0).volume-.4)<.0001,"inherit folder gain once");check(spec.tracks.get(0).pan==-.25,"folder pan");check(spec.tracks.get(0).clips.get(0).file.equals("Media\\piano.wav"),"Windows separators preserved");check(spec.regions.size()==2&&spec.regions.get(1).name.equals("Verso 1"),"spoken numbering preserved");check(spec.regions.get(0).color.equals("#ff0000"),"native region color");check(spec.duration==1.5&&spec.bpm==142,"tempo and timeline");
  try{RppReader.parse(source.replace("PLAYRATE 1 1 0","PLAYRATE 0.8 1 0"),"Bad");throw new AssertionError("rate accepted");}catch(ProjectSpec.Processing expected){}
  try{RppReader.parse(source.replace("SOURCE WAVE","SOURCE MIDI"),"Bad");throw new AssertionError("MIDI accepted");}catch(ProjectSpec.Processing expected){}
  float[] pcm=new float[48000*2];for(int i=0;i<pcm.length;i++)pcm[i]=(float)(i/48000.0);
  TimelineTrack timeline=new TimelineTrack("trim",new TimelineTrack.Clip[]{new TimelineTrack.Clip(new PcmTrack("source",48000,1,pcm),.5,.25,1,.5,1)},1.5);
  check(timeline.prepare(0,512),"prepare silence");check(timeline.sample(0,1)==0,"leading silence");check(Math.abs(timeline.sample(24000,1)-.125)<.00001,"offset and gain");check(timeline.sample(24000,0)==0,"item pan");check(timeline.sample(72000,1)==0,"clip trim end");
  ProjectSpec arrangement=new ProjectSpec();arrangement.regions.add(new ProjectSpec.Region("A",0,1,""));arrangement.regions.add(new ProjectSpec.Region("B",1,2,""));arrangement.jumps.add(new ProjectSpec.Jump(0,1,1.5));ProjectArrangement jumps=new ProjectArrangement(arrangement);check(jumps.target(0,false)==-1,"arm jump");check(jumps.target(47999,false)==-1,"do not jump early");check(jumps.target(48000,false)==72000,"exact end jump");check(jumps.target(48000,false)==-1,"consume once");jumps.reset();jumps.manualSeek(arrangement.regions.get(0));check(jumps.target(0,false)==-1&&jumps.target(48000,false)==-1,"manual seek suppresses source");jumps.reset();jumps.target(0,false);check(jumps.target(48000,true)==-1,"loop blocks auto jump");
  DemoMixer mixer=new DemoMixer();mixer.load(new PcmTrack[]{timeline});arrangement.duration=1.5;mixer.configureProject(arrangement);mixer.play();float[] out=new float[512*2];for(int i=0;i<94;i++)mixer.render(out,512);check(mixer.position()>=1.5,"render follows arranged jump");mixer.stop();check(mixer.position()==0,"stop resets");
  System.out.println("ProjectTest OK: RPP structure, regions, clipping, gain/pan, exact jump and manual/loop behavior");
 }
}
