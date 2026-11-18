package com.onebytelabs.zlplay;
import android.os.Bundle;
import com.onebytelabs.zlplay.project.*;
import org.json.*;
import java.util.*;
/** Verifies canonical editing data and save round trips using an in-memory fixture. */
final class EditingDataSmoke {
    private static void check(boolean value,String name){if(!value)throw new AssertionError(name);}
    static Bundle run()throws Exception{
        ProjectSpec spec=new ProjectSpec();spec.title="Synthetic editor";spec.duration=8;spec.bpm=137;spec.originalKey="C";spec.targetKey="C";
        for(String name:new String[]{"Piano","Guitar"}){ProjectSpec.Track track=new ProjectSpec.Track();track.name=name;ProjectSpec.Clip clip=new ProjectSpec.Clip();clip.file=name+".wav";clip.length=8;track.clips.add(clip);spec.tracks.add(track);}
        JSONObject root=new JSONObject(ProjectAuthor.manifest(spec));JSONObject prefs=root.getJSONObject("settings");
        JSONObject effect=new JSONObject().put("enabled",true).put("trim",-3).put("lowpass",2500);
        prefs.put("channelEffects",new JSONObject().put("track:Piano",effect).put("master",new JSONObject().put("enabled",true).put("trim",-6)));
        root.put("padMarkers",new JSONArray().put(new JSONObject().put("time",4).put("note","Gb")).put(new JSONObject().put("time",1).put("note","D")));
        root.getJSONArray("tracks").getJSONObject(0).put("group","Keys");
        prefs.put("mixerOrder",new JSONObject().put("tracks",new JSONArray().put("Guitar").put("Piano")).put("groups",new JSONArray().put("VS").put("Keys")));
        List<Integer> order=MixerOrder.tracks(root);check(order.equals(Arrays.asList(1,0)),"group/track ordering uses desktop mixerOrder");
        JSONObject anchored=new JSONObject(root.toString());JSONArray anchoredTracks=anchored.getJSONArray("tracks");
        anchoredTracks.put(new JSONObject(anchoredTracks.getJSONObject(0).toString()).put("name","Click_1"));
        anchoredTracks.put(new JSONObject(anchoredTracks.getJSONObject(0).toString()).put("name","Guide_1"));
        anchored.getJSONObject("settings").put("mixerOrder",new JSONObject().put("groups",new JSONArray().put("Keys").put("VS"))
            .put("tracks",new JSONArray().put("Guide_1").put("Piano").put("Click_1").put("Guitar")));
        check(MixerOrder.tracks(anchored).equals(Arrays.asList(2,3,1,0)),"Click/Guide fixed first, VS before user groups, despite saved order");
        ProjectSpec parsed=ZlplayReader.parse(root.toString());check(parsed.padMarkers.size()==2&&parsed.padMarkers.get(0).note==2,"pad markers are read and sorted");
        check(parsed.tracks.get(0).eq.length==1&&Math.abs(parsed.tracks.get(0).eqTrim-Math.pow(10,-3.0/20))<.00001,"track effects read");
        check(Math.abs(parsed.busTrim[0]-Math.pow(10,-6.0/20))<.00001,"master effect read");check(!parsed.warnings.contains("effects")&&!parsed.warnings.contains("pad-markers"),"supported processing does not warn");
        List<ProjectAuthor.Mix> mix=new ArrayList<>();for(ProjectSpec.Track track:parsed.tracks)mix.add(new ProjectAuthor.Mix((float)track.volume,(float)track.pan,track.mute,track.solo,true));
        JSONObject saved=new JSONObject(ProjectAuthor.editedManifest(root.toString(),parsed.title,137,4,4,"C","C",false,false,mix));
        check(saved.getJSONArray("padMarkers").toString().equals(root.getJSONArray("padMarkers").toString()),"save preserves pad markers");
        check(saved.getJSONObject("settings").getJSONObject("channelEffects").toString().equals(prefs.getJSONObject("channelEffects").toString()),"save preserves channel EQ");
        check(MixerOrder.tracks(saved).equals(order)&&saved.getJSONArray("tracks").getJSONObject(0).getString("group").equals("Keys"),"save preserves groups and order");
        prefs.getJSONObject("channelEffects").getJSONObject("track:Piano").put("enabled",false);parsed=ZlplayReader.parse(root.toString());check(parsed.tracks.get(0).eq==null&&parsed.tracks.get(0).eqTrim==1,"EQ bypass in saved project");
        Bundle result=new Bundle();result.putString("result","PASS: desktop format, pad markers, channel EQ, bypass, groups, order and save round trips");return result;
    }
}
