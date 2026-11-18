package com.onebytelabs.zlplay.project;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/** Creates portable projects that reference audio relative to the selected song folder. */
public final class ProjectAuthor {
    private ProjectAuthor() {}

    public static List<String> audioFiles(ProjectFolder folder) {
        return folder.files.keySet().stream()
            .filter(path -> path.toLowerCase(Locale.ROOT).matches(".*\\.(wav|mp3)$"))
            .sorted(String.CASE_INSENSITIVE_ORDER.thenComparing(String::compareTo))
            .collect(java.util.stream.Collectors.toList());
    }

    public static ProjectSpec draft(String title, List<String> files) throws ProjectSpec.Invalid {
        ProjectSpec project = new ProjectSpec();
        project.title = title;
        Map<String, Integer> names = new HashMap<>();
        for (String path : files) names.merge(trackName(path).toLowerCase(Locale.ROOT), 1, Integer::sum);
        for (String path : files) {
            if (path.indexOf('\\') >= 0) throw new ProjectSpec.Invalid();
            ProjectSpec.Track track = new ProjectSpec.Track();
            String name = trackName(path);
            track.name = names.get(name.toLowerCase(Locale.ROOT)) == 1
                ? name : name + " · " + path;
            ProjectSpec.Clip clip = new ProjectSpec.Clip();
            clip.file = path;
            track.clips.add(clip);
            project.tracks.add(track);
        }
        project.validate();
        return project;
    }

    private static String trackName(String path) {
        return path.substring(path.lastIndexOf('/') + 1, path.lastIndexOf('.'));
    }

    public static String manifest(ProjectSpec project) throws Exception {
        project.validate();
        JSONArray tracks = new JSONArray();
        for (ProjectSpec.Track track : project.tracks) {
            JSONArray items = new JSONArray();
            for (ProjectSpec.Clip clip : track.clips) {
                JSONObject item = new JSONObject().put("file", clip.file)
                    .put("position", clip.position).put("offset", clip.offset);
                if (clip.length > 0) item.put("length", clip.length);
                if (clip.muted) item.put("muted",true);
                if (clip.gain!=1) item.put("gain",clip.gain);
                if (clip.pan!=0) item.put("pan",clip.pan);
                items.put(item);
            }
            JSONObject saved = new JSONObject().put("name", track.name).put("group", track.group)
                .put("volume", track.volume).put("originalVolume", track.volume)
                .put("pan", track.pan).put("mute", track.mute).put("originalMute", track.mute)
                .put("solo", track.solo).put("originalSolo", track.solo).put("items", items);
            if (track.pitchEnabled != null) saved.put("pitchEnabled", track.pitchEnabled);
            tracks.put(saved);
        }
        JSONArray regions=new JSONArray();
        for(ProjectSpec.Region region:project.regions)regions.put(new JSONObject().put("name",region.name)
            .put("start",region.start).put("end",region.end).put("color",region.color));
        int original = MusicalKey.parse(project.originalKey);
        int target = MusicalKey.parse(project.targetKey);
        JSONObject signature = new JSONObject().put("numerator", project.numerator).put("denominator", project.denominator);
        JSONObject click = new JSONObject().put("source", project.projectClick ? "project" : "native")
            .put("meter", project.numerator).put("accent", Boolean.TRUE.equals(project.clickAccent)).put("accentPosition", "first");
        JSONObject settings = new JSONObject().put("groupNamingVersion", 2).put("originalKey", project.originalKey)
            .put("lastTonalKey", project.targetKey).put("click", click).put("tempoDefinitionEditable", true)
            .put("liveTranspose", original < 0 || target < 0 ? 0 : MusicalKey.delta(original, target));
        JSONObject song = new JSONObject().put("title", project.title).put("key", project.originalKey)
            .put("bpm", project.bpm).put("duration", project.duration).put("timeSignature", signature);
        return new JSONObject().put("format", "tom-certo-project").put("formatVersion", 1)
            .put("song", song).put("tracks", tracks).put("regions", regions)
            .put("mixer", new JSONObject()).put("settings", settings).toString(2);
    }

    public static String saveDocument(Context context, ProjectFolder folder, String directory, String title,
                                      String extension, String manifest) throws IOException {
        String name = title.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]+", "-").replaceAll("[. ]+$", "");
        if (name.isEmpty()) name = "ZLPlay";
        if (name.length() > 100) name = name.substring(0, name.offsetByCodePoints(0, Math.min(100, name.codePointCount(0, name.length()))));
        var resolver = context.getContentResolver();
        Uri parent = folder.directories.get(directory);
        if (parent == null) throw new IOException("Project folder unavailable");
        Uri saved = DocumentsContract.createDocument(resolver, parent, "application/octet-stream", name + extension);
        if (saved == null) throw new IOException("Cannot create project");
        if (folder.files.containsValue(saved)) throw new IOException("Provider returned an existing file");
        try {
            String filename;
            try (Cursor cursor = resolver.query(saved, new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
                if (cursor == null || !cursor.moveToFirst()) throw new IOException("Project name unavailable");
                filename = cursor.getString(0);
            }
            if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(extension)
                    || filename.contains("/") || filename.contains("\\")) {
                throw new IOException("Provider changed the project extension");
            }
            try (OutputStream output = resolver.openOutputStream(saved, "wt")) {
                if (output == null) throw new IOException("Cannot write project");
                output.write(manifest.getBytes(StandardCharsets.UTF_8));
            }
            String relative = directory + filename;
            folder.files.put(relative, saved);
            return relative;
        } catch (IOException | RuntimeException failure) {
            try { DocumentsContract.deleteDocument(resolver, saved); }
            catch (IOException | RuntimeException ignored) {}
            throw failure;
        }
    }

    public static String editedManifest(String original, String title, int bpm, int numerator, int denominator,
                                        String originalKey, String targetKey, boolean projectClick, boolean accent,
                                        List<Mix> edits) throws Exception {
        JSONObject root = new JSONObject(original);
        JSONObject song = root.getJSONObject("song");
        JSONObject settings = root.optJSONObject("settings");
        if (settings == null) {
            settings = new JSONObject();
            root.put("settings", settings);
        }
        ProjectSpec baseline = ZlplayReader.parse(original);
        song.put("title", title);
        boolean tempoChanged = Math.round(baseline.bpm) != bpm;
        if (tempoChanged) song.put("bpm", bpm);
        JSONObject signature = song.optJSONObject("timeSignature");
        if (signature == null) {
            signature = new JSONObject();
            song.put("timeSignature", signature);
        }
        signature.put("numerator", numerator).put("denominator", denominator);
        JSONObject tempo = settings.optJSONObject("originalTempo");
        if (tempo != null) {
            if (tempoChanged) {
                double delta = bpm - tempo.optDouble("bpm", baseline.bpm);
                JSONArray points = tempo.optJSONArray("points");
                if (points != null) for (int i = 0; i < points.length(); i++) {
                    JSONObject point = points.getJSONObject(i);
                    point.put("bpm", Math.max(20, Math.min(400, point.getDouble("bpm") + delta)));
                }
                tempo.put("bpm", bpm);
            }
            tempo.put("numerator", numerator).put("denominator", denominator);
        }
        if (MusicalKey.parse(originalKey) != MusicalKey.parse(baseline.originalKey)
                || MusicalKey.parse(targetKey) != MusicalKey.parse(baseline.targetKey)) {
            song.put("key", originalKey);
            settings.put("originalKey", originalKey).put("lastTonalKey", targetKey);
            int from = MusicalKey.parse(originalKey), to = MusicalKey.parse(targetKey);
            settings.put("liveTranspose", from < 0 || to < 0 ? 0 : MusicalKey.delta(from, to));
        }
        JSONObject click = settings.optJSONObject("click");
        if (click == null) {
            click = new JSONObject();
            settings.put("click", click);
        }
        click.put("source", projectClick ? "project" : "native").put("meter", numerator).put("accent", accent);
        JSONArray tracks = root.getJSONArray("tracks");
        if (tracks.length() != edits.size()) throw new ProjectSpec.Invalid();
        for (int i = 0; i < tracks.length(); i++) {
            JSONObject track = tracks.getJSONObject(i);
            ProjectSpec.Track previous = baseline.tracks.get(i);
            Mix edit = edits.get(i);
            if (edit.volume != (float) previous.volume) {
                double stored = track.optDouble("volume", 1);
                track.put("volume", stored > 0 && previous.volume > 0 ? edit.volume * stored / previous.volume : edit.volume);
            }
            if (edit.pan != (float) previous.pan) {
                double pan = track.optDouble("pan", 0) + (edit.pan - previous.pan);
                track.put("pan", Math.max(-1, Math.min(1, pan)));
            }
            if (edit.mute != previous.mute) track.put("mute", edit.mute);
            if (edit.solo != previous.solo) track.put("solo", edit.solo);
            if (edit.pitch != (previous.pitchEnabled != null ? previous.pitchEnabled : ProjectSpec.defaultPitch(previous.name))) {
                track.put("pitchEnabled", edit.pitch);
            }
        }
        ZlplayReader.parse(root.toString());
        return root.toString(2);
    }

    public static final class Mix {
        public final float volume, pan;
        public final boolean mute, solo, pitch;

        public Mix(float volume, float pan, boolean mute, boolean solo, boolean pitch) {
            this.volume = volume;
            this.pan = pan;
            this.mute = mute;
            this.solo = solo;
            this.pitch = pitch;
        }
    }
}
