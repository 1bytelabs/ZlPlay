package com.onebytelabs.zlplay;
import java.util.*;
import org.json.*;
final class MixerOrder {
    static List<Integer> tracks(JSONObject project){
        JSONArray tracks=project.optJSONArray("tracks");List<Integer> result=new ArrayList<>();
        for(int i=0;tracks!=null&&i<tracks.length();i++)result.add(i);
        JSONObject settings=project.optJSONObject("settings"),order=settings==null?null:settings.optJSONObject("mixerOrder");
        JSONArray names=order==null?null:order.optJSONArray("tracks"),groups=order==null?null:order.optJSONArray("groups");
        result.sort(Comparator.<Integer>comparingInt(i->sectionRank(groups,tracks.optJSONObject(i)))
            .thenComparingInt(i->rank(names,tracks.optJSONObject(i).optString("name"))).thenComparingInt(i->i));
        return result;
    }
    private static int sectionRank(JSONArray groups,JSONObject track){
        String name=track.optString("name"),group=track.optString("group","VS");
        if(com.onebytelabs.zlplay.project.ProjectSpec.isClickName(name))return 0;
        if(com.onebytelabs.zlplay.project.ProjectSpec.isGuideName(name))return 1;
        if(group.equals("VS")||group.isEmpty())return 2;
        int order=rank(groups,group);return order==Integer.MAX_VALUE?Integer.MAX_VALUE:3+order;
    }
    private static int rank(JSONArray values,String value){if(values!=null)for(int i=0;i<values.length();i++)if(value.equals(values.optString(i)))return i;return Integer.MAX_VALUE;}
}
