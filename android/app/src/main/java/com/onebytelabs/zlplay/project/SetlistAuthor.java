package com.onebytelabs.zlplay.project;

import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

/** Writes the same relative folder/source and order fields used by the desktop. */
public final class SetlistAuthor {
    private SetlistAuthor() {}

    public static String create(List<String> projects) throws Exception {
        JSONArray songs = new JSONArray();
        JSONArray order = new JSONArray();
        Set<String> folders = new HashSet<>();
        if (projects.isEmpty() || projects.size() > 256) throw new ProjectSpec.Invalid();
        for (String project : projects) {
            String[] parts = project.split("/", -1);
            if (parts.length != 2 || parts[0].isEmpty() || parts[0].equals(".") || parts[0].equals("..")
                    || project.indexOf('\\') >= 0 || !parts[1].toLowerCase(Locale.ROOT).matches(".*\\.(zlplay|rpp)$")
                    || !folders.add(parts[0].toLowerCase(Locale.ROOT))) throw new ProjectSpec.Invalid();
            String folder = parts[0];
            String filename = parts[1];
            songs.put(song(folder,filename));
            order.put(folder);
        }
        return new JSONObject().put("type", "zlplay-library").put("version", 1)
            .put("songs", songs).put("order", order).toString(2);
    }

    public static JSONObject song(String folder,String filename) throws Exception {
        return new JSONObject().put("id",folder).put("name",folder).put("folder",folder)
            .put("source",filename).put(filename.toLowerCase(Locale.ROOT).endsWith(".rpp")?"rpp":"project",filename);
    }

    public static String saveEdits(String original, List<String> songIds, Map<String, String> snapshots,
                                   Map<String, String> projects, Map<String, String> additions) throws Exception {
        JSONObject root = new JSONObject(original);
        root.put("order", new JSONArray(songIds));
        JSONArray songs = root.getJSONArray("songs");
        Set<String> retained = new HashSet<>(songIds);
        for (int i = songs.length()-1; i >= 0; i--) if (!retained.contains(id(songs.getJSONObject(i)))) songs.remove(i);
        for (String id : songIds) {
            String added = additions.get(id);
            if (added != null) songs.put(new JSONObject(added));
        }
        for (int i = 0; i < songs.length(); i++) {
            JSONObject song = songs.getJSONObject(i);
            String id = id(song);
            String snapshot = snapshots.get(id);
            if (snapshot == null) continue;
            JSONObject session = new JSONObject(snapshot);
            JSONObject settings = session.getJSONObject("settings");
            String project = projects.get(id);
            String filename = project.substring(project.lastIndexOf('/') + 1);
            if (!filename.equals(".zlplay-session.zlplay")) {
                String source = song.optString("source", "");
                if (source.isEmpty() || song.isNull("source")) {
                    source = song.optString("project", "");
                    if (source.isEmpty() || song.isNull("project")) source = song.optString("rpp", "");
                    if (source.isEmpty() || source.equals("null")) source = filename;
                    song.put("source", source);
                }
                song.put("sessionSource", filename);
            }
            song.put("originalKey", settings.optString("originalKey", session.getJSONObject("song").optString("key")));
            song.put("tonalKey", settings.optString("lastTonalKey", ""));
            song.put("sessionProject", session);
        }
        String text = root.toString(2);
        if (text.getBytes(StandardCharsets.UTF_8).length > 4 * 1024 * 1024) throw new ProjectSpec.Invalid();
        return text;
    }
    private static String id(JSONObject song){
        String name=song.isNull("name")?song.optString("folder"):song.optString("name");
        return song.isNull("id")?name:song.optString("id");
    }
}
