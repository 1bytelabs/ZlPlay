package com.onebytelabs.zlplay;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.AdapterView;
import com.onebytelabs.zlplay.project.ProjectSpec;
import com.onebytelabs.zlplay.project.ProjectGuide;
import com.onebytelabs.zlplay.project.ZlplayReader;
import com.onebytelabs.zlplay.audio.SourceWaveform;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.BiConsumer;
import org.json.JSONArray;
import org.json.JSONObject;

final class ProjectEditDialog {
    private final Activity context;
    private JSONObject project;
    private final String original;
    interface Transport {
        double position();
        void seek(double position);
        void toggle();
        void stop();
        boolean playing();
    }
    private final Transport transport;
    private final Consumer<String> apply;
    private final Dialog dialog;
    private final EditorTimeline timeline;
    private final LinearLayout actions;
    private final TextView selectionLabel;
    private final Button undo, redo;
    private final ArrayDeque<String> undoHistory = new ArrayDeque<>(), redoHistory = new ArrayDeque<>();
    private final Map<String, SourceWaveform> waves;
    private final SourceWaveform mixWave;
    private final boolean regionMode;
    private final double duration;
    private final BiConsumer<String,Consumer<List<ProjectGuide.Audio>>> importGuide;
    private boolean importingGuide;
    private String currentState;
    private EditorTimeline.Item selection;

    static void regions(Activity context,String manifest,double duration,Transport transport,float[] mix,
                        Map<String,SourceWaveform> waves,Consumer<String> apply,
                        BiConsumer<String,Consumer<List<ProjectGuide.Audio>>> importGuide) throws Exception {
        new ProjectEditDialog(context,manifest,true,duration,transport,mix,waves,apply,importGuide).show();
    }

    static void tracks(Activity context,String manifest,double duration,Transport transport,
                       Map<String,SourceWaveform> waves,Consumer<String> apply,
                       BiConsumer<String,Consumer<List<ProjectGuide.Audio>>> importGuide) throws Exception {
        new ProjectEditDialog(context,manifest,false,duration,transport,null,waves,apply,importGuide).show();
    }

    static String withResolvedLengths(String manifest,ProjectSpec loaded) throws Exception {
        JSONObject json=new JSONObject(manifest);
        JSONArray tracks=json.getJSONArray("tracks");
        for(int i=0;i<tracks.length();i++){
            JSONArray items=tracks.getJSONObject(i).getJSONArray("items");
            List<ProjectSpec.Clip> clips=loaded.tracks.get(i).clips;
            if(clips.size()!=items.length())continue;
            for(int j=0;j<items.length();j++)if(items.getJSONObject(j).isNull("length"))
                items.getJSONObject(j).put("length",clips.get(j).length);
        }
        JSONObject song=json.getJSONObject("song");
        if(song.optDouble("duration",0)<=0)song.put("duration",loaded.duration);
        return json.toString();
    }

    private ProjectEditDialog(Activity context,String manifest,boolean regions,double duration,Transport transport,
                              float[] mix,Map<String,SourceWaveform> waves,Consumer<String> apply,
                              BiConsumer<String,Consumer<List<ProjectGuide.Audio>>> importGuide) throws Exception {
        this.context=context;
        this.project=new JSONObject(manifest);
        this.original=project.toString();
        this.regionMode=regions;
        this.duration=duration;
        this.transport=transport;
        this.apply=apply;
        this.importGuide=importGuide;
        this.waves=new HashMap<>(waves);
        this.mixWave=mix==null?null:new SourceWaveform(duration,mix);
        currentState=original;
        dialog=new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setOnKeyListener((ignored,key,event)->{
            if(event.getAction()!=KeyEvent.ACTION_UP)return false;
            if(key==KeyEvent.KEYCODE_BACK){close();return true;}
            if(selection!=null&&selection.track>=0&&key==KeyEvent.KEYCODE_S){splitSelected();return true;}
            if(selection!=null&&(key==KeyEvent.KEYCODE_DEL||key==KeyEvent.KEYCODE_FORWARD_DEL)){deleteSelected();return true;}
            return false;
        });
        timeline=new EditorTimeline(context,new EditorTimeline.Listener(){
            @Override public void select(EditorTimeline.Item item){setSelection(item);}
            @Override public void open(EditorTimeline.Item item){setSelection(item);openSelection();}
            @Override public void seek(double seconds){transport.seek(seconds);timeline.setPosition(transport.position(),false);}
            @Override public void change(EditorTimeline.Item item,double start,double end,double offset){moveSelection(item,start,end,offset);}
        });
        LinearLayout editor=new LinearLayout(context);
        editor.setOrientation(LinearLayout.VERTICAL);
        editor.setBackgroundColor(0xff0b111c);
        LinearLayout header=strip(editor);
        control(header, context.getString(R.string.project_editor_close), "back", this::close, false);
        LinearLayout title=new LinearLayout(context);
        title.setOrientation(LinearLayout.VERTICAL);
        TextView heading=label(context.getString(regions?R.string.edit_regions:R.string.edit_tracks),16,0xfff0f4fc);
        TextView song=label(project.getJSONObject("song").optString("title"),11,0xffa8b8d1);
        title.addView(heading);title.addView(song);
        header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        undo=control(header,context.getString(R.string.project_editor_undo),"↶",()->restoreHistory(undoHistory,redoHistory),false);
        redo=control(header,context.getString(R.string.project_editor_redo),"↷",()->restoreHistory(redoHistory,undoHistory),false);
        control(header,context.getString(R.string.apply_project_edits),null,this::applyEdits,true);
        addTransport(editor);
        HorizontalScrollView toolsScroll=new HorizontalScrollView(context);
        toolsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tools=new LinearLayout(context);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        tools.setPadding(dp(12),0,dp(8),0);
        if(regionMode){
            control(tools,context.getString(R.string.add_project_region),null,()->editRegion(-1),false);
            control(tools,context.getString(R.string.project_pad_change),null,()->editPadMarker(-1),false);
            control(tools,context.getString(R.string.edit_project_jumps),null,this::editJumps,false);
        }
        control(tools,context.getString(R.string.project_add_guide),null,this::showGuideMenu,false);
        toolsScroll.addView(tools);
        editor.addView(toolsScroll,new LinearLayout.LayoutParams(-1,dp(43)));
        editor.addView(timeline,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout footer=strip(editor);
        selectionLabel=label("",12,0xffd8e3f7);
        footer.addView(selectionLabel,new LinearLayout.LayoutParams(dp(128),-2));
        HorizontalScrollView scroll=new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        actions=new LinearLayout(context);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        scroll.addView(actions);
        footer.addView(scroll,new LinearLayout.LayoutParams(0,-1,1));
        dialog.setContentView(editor);
    }

    private void show(){
        draw();
        dialog.show();
        Window window=dialog.getWindow();
        window.setBackgroundDrawable(new ColorDrawable(0xff0b111c));
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setDimAmount(1);
        window.setLayout(-1,-1);
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private void addTransport(LinearLayout panel){
        LinearLayout row=strip(panel);
        Button play=control(row,context.getString(R.string.play),"play",transport::toggle,false);
        control(row,context.getString(R.string.stop),"stop",transport::stop,false);
        TextView clock=label("",13,0xffc0d6ef);
        row.addView(clock,new LinearLayout.LayoutParams(0,-2,1));
        control(row,context.getString(R.string.project_editor_zoom_out),"−",()->timeline.zoomBy(.67),false);
        control(row,context.getString(R.string.project_editor_zoom_in),"+",()->timeline.zoomBy(1.5),false);
        control(row,context.getString(R.string.project_editor_fit),null,timeline::fit,false);
        Button snap=control(row,context.getString(R.string.project_editor_snap),"magnet",()->{},false);
        snap.setOnClickListener(view->{snap.setSelected(!snap.isSelected());timeline.setSnap(snap.isSelected());});
        row.addOnAttachStateChangeListener(new android.view.View.OnAttachStateChangeListener(){
            final Runnable update=new Runnable(){
                @Override public void run(){
                    if(!row.isAttachedToWindow())return;
                    double at=transport.position();
                    timeline.setPosition(at,transport.playing());
                    boolean playing=transport.playing();
                    if(play.isSelected()!=playing){
                        play.setSelected(playing);
                        play.setText(playing?"Ⅱ":"▶");
                        play.setContentDescription(context.getString(playing?R.string.pause:R.string.play));
                    }
                    clock.setText(clock(at)+"  /  "+clock(duration));
                    row.postDelayed(this,65);
                }
            };
            @Override public void onViewAttachedToWindow(android.view.View view){row.post(update);}
            @Override public void onViewDetachedFromWindow(android.view.View view){row.removeCallbacks(update);}
        });
    }

    private void draw(){
        String state=project.toString();
        if(!state.equals(currentState)){
            push(undoHistory,currentState);
            redoHistory.clear();
            currentState=state;
        }
        updateTimeline();
        updateActions();
    }

    private JSONArray regions(){return project.optJSONArray("regions");}

    private void updateTimeline(){
        List<EditorTimeline.Lane> lanes=new ArrayList<>();
        double extent=duration;
        JSONObject song=project.optJSONObject("song"),signature=song.optJSONObject("timeSignature");
        double bpm=song.optDouble("bpm",120);
        int numerator=signature==null?4:signature.optInt("numerator",4);
        int denominator=signature==null?4:signature.optInt("denominator",4);
        if(regionMode){
            List<EditorTimeline.Item> markers=new ArrayList<>();
            JSONArray regions=regions();
            if(regions!=null)for(int i=0;i<regions.length();i++){
                JSONObject region=regions.optJSONObject(i);
                int color=0xff386a96;
                try{color=Color.parseColor(region.optString("color"));}catch(IllegalArgumentException ignored){}
                markers.add(new EditorTimeline.Item(-1,i,color,region.optString("name"),region.optDouble("start"),
                    region.optDouble("end"),0,null,true,false));
            }
            lanes.add(new EditorTimeline.Lane(context.getString(R.string.project_editor_regions_lane),0xffc080ff,48,markers));
            List<EditorTimeline.Item> wave=new ArrayList<>();
            wave.add(new EditorTimeline.Item(-2,0,0xff1e3953,"",0,duration,0,mixWave,false,false));
            lanes.add(new EditorTimeline.Lane(context.getString(R.string.project_editor_mix_lane),0xff62bdff,68,wave));
        }
        if(regionMode){
            List<EditorTimeline.Item> pads=new ArrayList<>();
            JSONArray markers=project.optJSONArray("padMarkers");
            if(markers!=null)for(int i=0;i<markers.length();i++){
                JSONObject marker=markers.optJSONObject(i);
                double time=marker.optDouble("time");
                pads.add(new EditorTimeline.Item(-3,i,0xff765538,"Pad: "+marker.optString("note"),
                    time,Math.min(duration,time+60/bpm),0,null,true,false));
            }
            lanes.add(new EditorTimeline.Lane(context.getString(R.string.project_pad_changes),0xffffc47b,40,pads));
        }
        JSONArray tracks=project.optJSONArray("tracks");
        boolean hasGuide=false;
        int[] colors={0xff2b5d98,0xff375d85,0xff594694,0xff235a71,0xff7c466c,0xff315f59};
        for(int i:MixerOrder.tracks(project)){
            JSONObject track=tracks.optJSONObject(i);
            String name=track.optString("name");
            boolean guide=ProjectSpec.isGuideName(name);
            if(regionMode&&!guide)continue;
            hasGuide|=guide;
            int color=guide?0xff5a4086:ProjectSpec.isClickName(name)?0xff276374:colors[i%colors.length];
            List<EditorTimeline.Item> clips=new ArrayList<>();
            JSONArray items=track.optJSONArray("items");
            for(int j=0;j<items.length();j++){
                JSONObject item=items.optJSONObject(j);
                String file=item.optString("file");
                SourceWaveform source=waves.get(file);
                double start=item.optDouble("position",0),offset=item.optDouble("offset",0);
                double length=item.optDouble("length",source==null?0:source.duration-offset);
                if(length<=0)continue;
                extent=Math.max(extent,start+length);
                String title=item.optString("displayName",file.substring(Math.max(file.lastIndexOf('/'),file.lastIndexOf('\\'))+1));
                clips.add(new EditorTimeline.Item(i,j,color,title,start,start+length,offset,source,true,
                    item.optBoolean("muted"),!guide));
            }
            lanes.add(new EditorTimeline.Lane(name,color,64,clips));
        }
        if(regionMode&&!hasGuide)lanes.add(new EditorTimeline.Lane(context.getString(R.string.project_editor_guide_lane),
            0xffb687ff,64,new ArrayList<>()));
        EditorTimeline.Item selected=null;
        for(EditorTimeline.Lane lane:lanes)for(EditorTimeline.Item item:lane.items)if(item.matches(selection))selected=item;
        selection=selected;
        double tail=regionMode?0:Math.min(6,duration*.12);
        timeline.show(lanes,selection,Math.min(86400,Math.max(extent,duration+tail)),bpm,numerator,denominator);
    }

    private void setSelection(EditorTimeline.Item item){
        selection=item;
        updateTimeline();
        updateActions();
    }

    private void updateActions(){
        undo.setEnabled(!undoHistory.isEmpty());
        redo.setEnabled(!redoHistory.isEmpty());
        undo.setAlpha(undo.isEnabled()?1:.35f);
        redo.setAlpha(redo.isEnabled()?1:.35f);
        selectionLabel.setText(selection==null?context.getString(R.string.project_editor_no_selection):selection.text);
        actions.removeAllViews();
        if(selection!=null){
            if(selection.track>=0){
                control(actions,context.getString(R.string.project_editor_split),null,this::splitSelected,false);
                control(actions,context.getString(R.string.project_editor_duplicate),null,this::duplicateSelected,false);
                control(actions,context.getString(selection.muted?R.string.project_editor_unmute:R.string.project_clip_mute),null,this::toggleSelectedMute,false);
                control(actions,context.getString(R.string.project_clip_eq),null,()->{
                    JSONObject clip=trackItems(selection.track).optJSONObject(selection.index);
                    editClipEq(clip);
                },false);
            }
            control(actions,context.getString(R.string.project_editor_details),null,this::openSelection,false);
            control(actions,context.getString(R.string.project_editor_delete),null,this::deleteSelected,false);
        }
        if(selection==null)selectionLabel.setText(context.getString(R.string.project_editor_no_selection));
    }

    private void showGuideMenu(){
        LinearLayout panel=column();
        panel.addView(label(context.getString(R.string.project_guide_language),12,0xffb1c3dc));
        Spinner language=spinner(new String[]{"Português","English","Español"},0,choice->{});
        panel.addView(language,new LinearLayout.LayoutParams(-1,dp(44)));
        panel.addView(label(context.getString(R.string.project_guide_voice),12,0xffb1c3dc));
        Spinner voice=spinner(new String[]{context.getString(R.string.project_guide_male),
            context.getString(R.string.project_guide_female)},0,choice->{});
        panel.addView(voice,new LinearLayout.LayoutParams(-1,dp(44)));
        Spinner phrases=new StyledSpinner(context);
        phrases.setContentDescription(context.getString(R.string.project_add_guide));
        panel.addView(phrases,new LinearLayout.LayoutParams(-1,dp(44)));
        List<String> paths=new ArrayList<>();
        Runnable refresh=()->{
            try{
                String root="guide-banks/"+new String[]{"pt","en","es"}[language.getSelectedItemPosition()]
                    +"/"+(voice.getSelectedItemPosition()+1);
                paths.clear();
                for(String category:new String[]{"sections","cues"}){
                    String directory=root+"/"+category;
                    String[] files=context.getAssets().list(directory);
                    if(files!=null)for(String filename:files)if(filename.endsWith(".mp3"))paths.add(directory+"/"+filename);
                }
                paths.sort(java.util.Comparator.comparing(path->path.substring(path.lastIndexOf('/')+1),String.CASE_INSENSITIVE_ORDER));
                List<String> names=new ArrayList<>();
                for(String path:paths)names.add(path.substring(path.lastIndexOf('/')+1,path.length()-4));
                ArrayAdapter<String> adapter=new ArrayAdapter<>(context,android.R.layout.simple_spinner_item,names);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                phrases.setAdapter(adapter);
            }catch(Exception failure){invalid();}
        };
        AdapterView.OnItemSelectedListener update=new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> parent,View view,int position,long id){refresh.run();}
            @Override public void onNothingSelected(AdapterView<?> parent){}
        };
        language.setOnItemSelectedListener(update);
        voice.setOnItemSelectedListener(update);
        refresh.run();
        AlertDialog sheet=new AlertDialog.Builder(context).setTitle(R.string.project_editor_guide_lane).setView(panel)
            .setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.project_add_guide,(view,which)->{
                int choice=phrases.getSelectedItemPosition();
                if(choice>=0&&choice<paths.size())chooseGuide(paths.get(choice));
            }).create();
        addButton(panel,R.string.project_add_external_guide,()->{sheet.dismiss();chooseGuide(null);});
        sheet.show();
    }

    private void openSelection(){
        if(selection==null)return;
        if(selection.track==-3)editPadMarker(selection.index);
        else if(selection.track<0)editRegion(selection.index);
        else editClip(trackItems(selection.track),selection.index);
    }

    private JSONArray trackItems(int track){return project.optJSONArray("tracks").optJSONObject(track).optJSONArray("items");}

    private void moveSelection(EditorTimeline.Item item,double start,double end,double offset){
        try{
            if(item.track==-3){
                project.getJSONArray("padMarkers").getJSONObject(item.index).put("time",Math.max(0,Math.min(duration,start)));
            }else if(item.track<0){
                start=Math.min(start,duration-(end-start));
                end=Math.min(duration,end);
                JSONArray regions=regions();
                JSONObject previous=regions.getJSONObject(item.index),edited=new JSONObject(previous.toString());
                for(int i=0;i<regions.length();i++)if(i!=item.index){
                    JSONObject other=regions.getJSONObject(i);
                    if(Math.abs(other.optDouble("start")-start)<.01&&Math.abs(other.optDouble("end")-end)<.01)
                        throw new IllegalArgumentException();
                }
                edited.put("start",start).put("end",end);
                updateRegionRules(previous,edited);
                regions.put(item.index,edited);
            }else{
                trackItems(item.track).getJSONObject(item.index).put("position",start).put("offset",offset).put("length",end-start);
            }
            draw();
        }catch(Exception failure){invalid();}
    }

    private void splitSelected(){
        if(selection==null||selection.track<0)return;
        splitClip(trackItems(selection.track),selection.index);
    }

    private boolean splitClip(JSONArray clips,int index){
        try{
            JSONObject clip=clips.getJSONObject(index);
            double split=transport.position(),relative=split-clip.optDouble("position",0),size=clip.getDouble("length");
            if(relative<.01||size-relative<.01||clipCount()>=ProjectSpec.MAX_CLIPS)throw new IllegalArgumentException();
            JSONObject next=new JSONObject(clip.toString());
            next.put("position",split).put("offset",clip.optDouble("offset",0)+relative).put("length",size-relative);
            clip.put("length",relative);
            clips.put(next);
            draw();
            return true;
        }catch(Exception failure){invalid();return false;}
    }

    private void duplicateSelected(){
        if(selection==null||selection.track<0)return;
        duplicateClip(trackItems(selection.track),selection.index);
    }

    private boolean duplicateClip(JSONArray clips,int index){
        try{
            if(clipCount()>=ProjectSpec.MAX_CLIPS)throw new IllegalArgumentException();
            clips.put(new JSONObject(clips.getJSONObject(index).toString()).put("position",transport.position()));
            draw();
            return true;
        }catch(Exception failure){invalid();return false;}
    }

    private void toggleSelectedMute(){
        try{
            JSONObject item=trackItems(selection.track).getJSONObject(selection.index);
            item.put("muted",!item.optBoolean("muted"));
            draw();
        }catch(Exception failure){invalid();}
    }

    private void deleteSelected(){
        EditorTimeline.Item item=selection;
        if(item==null)return;
        if(item.track>=0&&clipCount()<2){invalid();return;}
        confirm(item.track==-3?R.string.project_pad_delete:item.track<0?R.string.delete_project_region:R.string.project_delete_clip,()->{
            if(item.track==-3){
                project.optJSONArray("padMarkers").remove(item.index);
            }else if(item.track<0){
                updateRegionRules(regions().optJSONObject(item.index),null);
                regions().remove(item.index);
            }else trackItems(item.track).remove(item.index);
            selection=null;
            draw();
        });
    }

    private void applyEdits(){
        if(importingGuide)return;
        try{
            String edited=project.toString();
            ZlplayReader.parse(edited);
            dialog.dismiss();
            if(!edited.equals(original))apply.accept(edited);
        }catch(Exception failure){invalid();}
    }

    private void close(){
        if(importingGuide)return;
        if(currentState.equals(original))dialog.dismiss();
        else confirm(R.string.discard_project_edits,dialog::dismiss);
    }

    private void push(ArrayDeque<String> history,String state){
        if(history.size()>=8)history.removeLast();
        history.addFirst(state);
    }

    private void restoreHistory(ArrayDeque<String> from,ArrayDeque<String> to){
        if(importingGuide||from.isEmpty())return;
        try{
            push(to,currentState);
            currentState=from.removeFirst();
            project=new JSONObject(currentState);
            selection=null;
            draw();
        }catch(Exception failure){invalid();}
    }

    private void editPadMarker(int index){
        try{
            JSONArray markers=project.optJSONArray("padMarkers");
            JSONObject previous=index<0?null:markers.getJSONObject(index);
            LinearLayout fields=column();
            fields.addView(label(context.getString(R.string.pad_note),12,0xffb5c8df));
            String[] notes=com.onebytelabs.zlplay.project.MusicalKey.FLATS;
            int note=previous==null?0:com.onebytelabs.zlplay.project.MusicalKey.parse(previous.optString("note"));
            Spinner choice=spinner(notes,Math.max(0,note),position->{});
            fields.addView(choice);
            EditText time=number(fields,R.string.project_start,previous==null?transport.position():previous.optDouble("time"));
            accept(form(R.string.project_pad_change,fields),()->{
                double at=parse(time);
                if(at<0||at>duration)throw new IllegalArgumentException();
                JSONArray list=project.optJSONArray("padMarkers");
                if(list==null){list=new JSONArray();project.put("padMarkers",list);}
                if(index<0&&list.length()>=512)throw new IllegalArgumentException();
                for(int i=0;i<list.length();i++)if(i!=index&&Math.abs(list.getJSONObject(i).optDouble("time")-at)<.001)throw new IllegalArgumentException();
                JSONObject marker=previous==null?new JSONObject().put("id",java.util.UUID.randomUUID().toString()):new JSONObject(previous.toString());
                marker.put("time",at).put("note",notes[choice.getSelectedItemPosition()]);
                if(index<0)list.put(marker);else list.put(index,marker);
            });
        }catch(Exception failure){invalid();}
    }

    private void editRegion(int index){
        JSONObject previous=index<0?null:regions().optJSONObject(index);
        double initial=previous==null?Math.max(0,Math.min(transport.position(),duration-.1)):previous.optDouble("start");
        LinearLayout fields=column();
        String[] presets=context.getResources().getStringArray(R.array.editor_region_names);
        int[] palette={0xff408db7,0xff356eb6,0xff357d73,0xff793fbc,0xffa23f65,0xff7354a6,
            0xffa46e37,0xff577a9b,0xff3c8791,0xff8f57a2,0xff6b873e,0xffa74b55};
        int[] chosenColor={previous==null?palette[1]:0xff338bc5};
        if(previous!=null)try{chosenColor[0]=Color.parseColor(previous.optString("color"));}catch(IllegalArgumentException ignored){}
        EditText name=field(fields,R.string.project_region_name,previous==null?"":previous.optString("name"),false);
        String[] presetIds=context.getResources().getStringArray(R.array.editor_region_ids);
        String[] presetColors=context.getResources().getStringArray(R.array.editor_region_colors);
        String[] choices=new String[presets.length+1];
        choices[0]=context.getString(R.string.project_region_preset);
        System.arraycopy(presets,0,choices,1,presets.length);
        final String[] chosenPreset={previous==null?null:previous.optString("presetId",null)};
        Spinner presetMenu=spinner(choices,0,position->{
            if(position==0)return;
            name.setText(presets[position-1]);
            chosenPreset[0]=presetIds[position-1];
            chosenColor[0]=Color.parseColor(presetColors[position-1]);
        });
        fields.addView(presetMenu,0);
        LinearLayout times=new LinearLayout(context);
        LinearLayout fromField=column(),toField=column();
        fromField.setPadding(0,0,dp(8),0);toField.setPadding(dp(8),0,0,0);
        EditText start=number(fromField,R.string.project_start,initial);
        EditText end=number(toField,R.string.project_end,previous==null?Math.min(duration,initial+4):previous.optDouble("end"));
        times.addView(fromField,new LinearLayout.LayoutParams(0,-2,1));
        times.addView(toField,new LinearLayout.LayoutParams(0,-2,1));
        fields.addView(times);
        fields.addView(label(context.getString(R.string.project_region_color),12,0xffb2c5df));
        LinearLayout swatches=new LinearLayout(context);
        for(int color:palette){
            TextView swatch=label("●",25,color);
            swatch.setGravity(Gravity.CENTER);
            swatch.setContentDescription(context.getString(R.string.project_region_color)+" "+String.format(Locale.ROOT,"#%06X",color&0xffffff));
            swatch.setOnClickListener(view->{
                chosenColor[0]=color;
                for(int i=0;i<swatches.getChildCount();i++)swatches.getChildAt(i).setAlpha(swatches.getChildAt(i)==swatch?1:.35f);
            });
            swatches.addView(swatch,new LinearLayout.LayoutParams(0,dp(35),1));
        }
        fields.addView(swatches);
        AlertDialog form=form(R.string.edit_regions,fields);
        if(previous!=null)addButton(fields,R.string.delete_project_region,()->confirm(R.string.delete_project_region,()->{
            updateRegionRules(previous,null);
            regions().remove(index);
            form.dismiss();
            draw();
        }));
        accept(form,()->{
            double from=parse(start),to=parse(end);
            String value=name.getText().toString().trim();
            if(value.isEmpty()||from<0||to<=from||to>duration+.00001)throw new IllegalArgumentException();
            JSONArray regions=regions();
            if(regions==null){regions=new JSONArray();project.put("regions",regions);}
            for(int i=0;i<regions.length();i++){
                JSONObject other=regions.getJSONObject(i);
                if(i!=index&&Math.abs(other.optDouble("start")-from)<.01&&Math.abs(other.optDouble("end")-to)<.01)
                    throw new IllegalArgumentException();
            }
            if(index<0&&regions.length()>=512)throw new IllegalArgumentException();
            JSONObject replacement=previous==null?new JSONObject():new JSONObject(previous.toString());
            if(previous==null||!value.equals(previous.optString("name")))replacement.remove("presetId");
            if(chosenPreset[0]!=null){
                for(int i=0;i<presetIds.length;i++)if(presetIds[i].equals(chosenPreset[0])&&presets[i].equals(value)){
                    replacement.put("presetId",chosenPreset[0]);break;
                }
            }
            replacement.put("name",value).put("start",from).put("end",to);
            replacement.put("color",String.format(Locale.ROOT,"#%06X",chosenColor[0]&0xffffff));
            if(index<0)regions.put(replacement);
            else{updateRegionRules(previous,replacement);regions.put(index,replacement);}
        });
    }

    private void updateRegionRules(JSONObject old,JSONObject replacement){
        JSONObject arrangement=project.optJSONObject("arrangement");
        JSONArray rules=arrangement==null?null:arrangement.optJSONArray("rules");
        if(rules==null)return;
        for(int i=rules.length()-1;i>=0;i--){
            JSONObject rule=rules.optJSONObject(i);
            boolean from=matches(rule.optJSONObject("from"),old),to=matches(rule.optJSONObject("to"),old);
            if(!from&&!to)continue;
            if(replacement==null){rules.remove(i);continue;}
            try{
                if(from)rule.put("from",reference(replacement));
                if(to)rule.put("to",reference(replacement));
            }catch(Exception unexpected){throw new IllegalArgumentException(unexpected);}
        }
    }

    private static boolean matches(JSONObject ref,JSONObject region){
        return ref!=null&&Math.abs(ref.optDouble("start")-region.optDouble("start"))<.01
            &&Math.abs(ref.optDouble("end")-region.optDouble("end"))<.01;
    }

    private static JSONObject reference(JSONObject region) throws Exception {
        return new JSONObject().put("name",region.getString("name")).put("start",region.getDouble("start")).put("end",region.getDouble("end"));
    }

    private void editJumps(){
        try{
            JSONObject saved=project.optJSONObject("arrangement");
            JSONObject value=saved==null?new JSONObject().put("version",1).put("mode","rehearsal").put("enabled",true)
                .put("rules",new JSONArray()):new JSONObject(saved.toString());
            if(value.optJSONArray("rules")==null)value.put("rules",new JSONArray());
            showArrangement(value);
        }catch(Exception failure){invalid();}
    }

    private void showArrangement(JSONObject value){
        LinearLayout panel=column();
        LinearLayout controls=new LinearLayout(context);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        Switch enabled=new Switch(context);
        enabled.setText(R.string.project_arrangement_enabled);
        enabled.setChecked(value.optBoolean("enabled",true));
        enabled.setOnCheckedChangeListener((view,on)->{
            try{value.put("enabled",on);}catch(Exception failure){invalid();}
        });
        controls.addView(enabled,new LinearLayout.LayoutParams(0,dp(42),1));
        Spinner mode=spinner(new String[]{context.getString(R.string.automation_rehearsal),context.getString(R.string.automation_live)},
            "live".equals(value.optString("mode"))?1:0,choice->{
                try{value.put("mode",choice==0?"rehearsal":"live");}catch(Exception failure){invalid();}
            });
        controls.addView(mode,new LinearLayout.LayoutParams(dp(165),dp(42)));
        panel.addView(controls);
        TextView explanation=label(context.getString(R.string.project_arrangement_help),12,0xffa9bdd8);
        explanation.setSingleLine(false);
        panel.addView(explanation);
        ScrollView scroll=new ScrollView(context);
        LinearLayout rows=column();
        rows.setPadding(0,dp(8),0,dp(8));
        scroll.addView(rows);
        panel.addView(scroll,new LinearLayout.LayoutParams(-1,dp(190)));
        Runnable[] render={null};
        render[0]=()->{
            rows.removeAllViews();
            JSONArray rules=value.optJSONArray("rules"),regions=regions();
            String[] names=new String[regions==null?0:regions.length()];
            for(int i=0;i<names.length;i++)names[i]=regions.optJSONObject(i).optString("name")+" · "+clock(regions.optJSONObject(i).optDouble("start"));
            for(int i=0;i<rules.length();i++){
                int index=i;
                JSONObject rule=rules.optJSONObject(i);
                LinearLayout line=new LinearLayout(context);
                line.setGravity(Gravity.CENTER_VERTICAL);
                line.setPadding(dp(4),dp(4),dp(4),dp(4));
                line.setBackgroundColor(i%2==0?0xff182537:0xff132030);
                int from=findRegion(rule.optJSONObject("from")),to=findRegion(rule.optJSONObject("to"));
                Spinner source=spinner(names,from,choice->setJumpReference(rule,"from",choice));
                source.setContentDescription(context.getString(R.string.project_jump_source));
                line.addView(source,new LinearLayout.LayoutParams(0,dp(45),1));
                line.addView(label("  →  ",18,0xffb48aff));
                Spinner target=spinner(names,to,choice->setJumpReference(rule,"to",choice));
                target.setContentDescription(context.getString(R.string.project_jump_target));
                line.addView(target,new LinearLayout.LayoutParams(0,dp(45),1));
                control(line,context.getString(R.string.project_jump_up),"↑",()->{
                    if(index<1)return;
                    swapRules(rules,index,index-1);render[0].run();
                },false);
                control(line,context.getString(R.string.project_jump_down),"↓",()->{
                    if(index+1>=rules.length())return;
                    swapRules(rules,index,index+1);render[0].run();
                },false);
                control(line,context.getString(R.string.project_editor_delete),"×",()->confirm(R.string.delete_project_jump,()->{
                    rules.remove(index);render[0].run();
                }),false);
                rows.addView(line);
            }
            if(rules.length()==0)rows.addView(label(context.getString(R.string.project_no_jumps),13,0xff9fb4ce));
        };
        render[0].run();
        addButton(panel,R.string.add_project_jump,()->{
            JSONArray regions=regions(),rules=value.optJSONArray("rules");
            if(regions==null||regions.length()<2||rules.length()>=512){invalid();return;}
            try{
                rules.put(new JSONObject().put("id",java.util.UUID.randomUUID().toString())
                    .put("from",reference(regions.getJSONObject(0))).put("to",reference(regions.getJSONObject(1))));
                render[0].run();
            }catch(Exception failure){invalid();}
        });
        AlertDialog sheet=form(R.string.edit_project_jumps,panel);
        accept(sheet,()->{
            project.put("arrangement",value);
        });
    }

    private int findRegion(JSONObject ref){
        JSONArray list=regions();
        for(int i=0;list!=null&&i<list.length();i++)if(matches(ref,list.optJSONObject(i)))return i;
        return -1;
    }

    private void setJumpReference(JSONObject rule,String field,int index){
        try{
            if(index>=0)rule.put(field,reference(regions().getJSONObject(index)));
        }catch(Exception failure){invalid();}
    }

    private void swapRules(JSONArray rules,int a,int b){
        try{
            Object first=rules.get(a);
            rules.put(a,rules.get(b));
            rules.put(b,first);
        }catch(Exception failure){invalid();}
    }

    private Spinner spinner(String[] labels,int selected,java.util.function.IntConsumer change){
        Spinner spinner=new StyledSpinner(context);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(context,android.R.layout.simple_spinner_item,labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        if(selected>=0)spinner.setSelection(selected);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> parent,View view,int position,long id){change.accept(position);}
            @Override public void onNothingSelected(AdapterView<?> parent){}
        });
        return spinner;
    }

    private void chooseGuide(String asset){
        if(importGuide==null||importingGuide||clipCount()>=ProjectSpec.MAX_CLIPS)return;
        importingGuide=true;
        double at=transport.position();
        importGuide.accept(asset,files->{
            importingGuide=false;
            if(!dialog.isShowing())return;
            try{
                if(files.isEmpty())return;
                if(clipCount()+files.size()>ProjectSpec.MAX_CLIPS)throw new ProjectSpec.Invalid();
                JSONArray tracks=project.getJSONArray("tracks");
                JSONObject guide=null;
                int trackIndex=-1;
                for(int i=0;i<tracks.length();i++){
                    JSONObject track=tracks.getJSONObject(i);
                    if(ProjectSpec.isGuideName(track.optString("name"))){guide=track;trackIndex=i;break;}
                }
                if(guide==null){
                    if(tracks.length()>=32)throw new ProjectSpec.Invalid();
                    trackIndex=tracks.length();
                    guide=new JSONObject().put("name","Guia").put("group","VS").put("pitchEnabled",false).put("items",new JSONArray());
                    tracks.put(guide);
                }
                JSONArray clips=guide.getJSONArray("items");
                int firstClip=clips.length();
                double position=at;
                for(ProjectGuide.Audio file:files){
                    clips.put(new JSONObject().put("file",file.path).put("displayName",file.label)
                        .put("position",position).put("offset",0).put("length",file.duration));
                    waves.put(file.path,file.waveform);
                    position+=file.duration;
                }
                ProjectGuide.Audio first=files.get(0);
                selection=new EditorTimeline.Item(trackIndex,firstClip,0,first.label,at,at+first.duration,0,first.waveform,true,false);
                draw();
            }catch(Exception failure){invalid();}
        });
    }

    private void editClip(JSONArray clips,int index){
        JSONObject clip=clips.optJSONObject(index),effects=clip.optJSONObject("effects");
        LinearLayout fields=column();
        EditText at=number(fields,R.string.project_start,clip.optDouble("position",0));
        EditText offset=number(fields,R.string.project_clip_offset,clip.optDouble("offset",0));
        EditText length=number(fields,R.string.project_clip_length,clip.optDouble("length",0));
        EditText gain=field(fields,R.string.project_clip_gain,seconds(effects==null?0:effects.optDouble("trim",0)),true);
        CheckBox mute=new CheckBox(context);
        mute.setText(R.string.project_clip_mute);
        mute.setChecked(clip.optBoolean("muted"));
        fields.addView(mute);
        AlertDialog form=form(R.string.edit_project_clip,fields);
        addButton(fields,R.string.project_clip_eq,()->editClipEq(clip));
        addButton(fields,R.string.project_use_cursor,()->at.setText(seconds(transport.position())));
        addButton(fields,R.string.project_split_clip,()->{if(splitClip(clips,index))form.dismiss();});
        addButton(fields,R.string.project_duplicate_clip,()->{if(duplicateClip(clips,index))form.dismiss();});
        if(clipCount()>1)addButton(fields,R.string.project_delete_clip,()->confirm(R.string.project_delete_clip,()->{
            clips.remove(index);
            form.dismiss();
            draw();
        }));
        accept(form,()->{
            double position=parse(at),source=parse(offset),span=parse(length),db=parse(gain);
            SourceWaveform sourceWave=waves.get(clip.optString("file"));
            double available=sourceWave==null?clip.optDouble("offset",0)+clip.getDouble("length"):sourceWave.duration;
            if(position<0||source<0||span<=0||position+span>86400||source+span>available+.00001||db<-24||db>24)
                throw new IllegalArgumentException();
            clip.put("position",position).put("offset",source).put("length",span).put("muted",mute.isChecked());
            JSONObject savedEffects=clip.optJSONObject("effects");
            if(savedEffects!=null||db!=0){
                JSONObject settings=savedEffects==null?new JSONObject():savedEffects;
                if(db!=settings.optDouble("trim",0))settings.put("enabled",true);
                settings.put("trim",db);
                clip.put("effects",settings);
            }
        });
    }

    private void editClipEq(JSONObject clip){
        ChannelEqDialog.open(context,context.getString(R.string.project_clip_eq),clip.optJSONObject("effects"),effects->{
            try{clip.put("effects",effects);draw();}catch(Exception failure){invalid();}
        });
    }

    private int clipCount(){
        JSONArray tracks=project.optJSONArray("tracks");
        int count=0;
        for(int i=0;i<tracks.length();i++)count+=tracks.optJSONObject(i).optJSONArray("items").length();
        return count;
    }

    private LinearLayout column(){
        LinearLayout panel=new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        int padding=(int)(16*context.getResources().getDisplayMetrics().density);
        panel.setPadding(padding,0,padding,0);
        return panel;
    }

    private int dp(int size){return Math.round(context.getResources().getDisplayMetrics().density*size);}

    private LinearLayout strip(LinearLayout parent){
        LinearLayout row=new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8),0,dp(8),0);
        row.setBackgroundColor(0xff151e2d);
        parent.addView(row,new LinearLayout.LayoutParams(-1,dp(46)));
        return row;
    }

    private TextView label(String text,int size,int color){
        TextView view=new TextView(context);
        view.setTypeface(context.getResources().getFont(R.font.inter));
        view.setText(text);view.setTextSize(size);view.setTextColor(color);
        view.setSingleLine();
        view.setEllipsize(TextUtils.TruncateAt.END);
        return view;
    }

    private Button control(LinearLayout row,String text,String symbol,Runnable action,boolean primary){
        PerformanceButton button=new PerformanceButton(context);
        button.setTypeface(context.getResources().getFont(R.font.inter));
        button.setTextSize(12);
        button.setText(text);
        button.setContentDescription(text);
        button.setSingleLine();
        int width=-2;
        if(symbol!=null){
            width=dp(36);
            String glyph=symbol.equals("back")?"‹":symbol.equals("play")?"▶":symbol.equals("stop")?"■":symbol;
            button.setText(glyph);
            if(symbol.equals("magnet")){button.setText("");button.setPadding(0,0,0,0);button.leadingSymbol(symbol);button.setCompoundDrawablePadding(0);}
            button.setTextSize(20);
        }
        button.style(primary?PerformanceButton.Style.PRIMARY:PerformanceButton.Style.CONTROL);
        button.setOnClickListener(view->action.run());
        LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(width,dp(35));
        size.setMargins(0,0,dp(6),0);
        row.addView(button,size);
        return button;
    }

    private static String clock(double time){
        int seconds=(int)time;
        return String.format(Locale.ROOT,"%d:%02d.%02d",seconds/60,seconds%60,(int)((time-seconds)*100));
    }

    private EditText number(LinearLayout fields,int title,double value){return field(fields,title,seconds(value),true);}
    private EditText field(LinearLayout fields,int title,String value,boolean numeric){
        TextView label=new TextView(context);
        label.setText(title);
        fields.addView(label);
        EditText input=new EditText(context);
        input.setSingleLine(true);
        if(numeric)input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setContentDescription(context.getString(title));
        input.setText(value);
        fields.addView(input);
        return input;
    }

    private Button addButton(LinearLayout panel,int title,Runnable action){return addButton(panel,context.getString(title),action);}
    private Button addButton(LinearLayout panel,String title,Runnable action){
        Button button=new Button(context);
        button.setText(title);
        button.setOnClickListener(view->action.run());
        panel.addView(button);
        return button;
    }

    private AlertDialog form(int title,LinearLayout fields){
        ScrollView scroll=new ScrollView(context);
        scroll.addView(fields);
        return new AlertDialog.Builder(context).setTitle(title).setView(scroll)
            .setNegativeButton(android.R.string.cancel,null).setPositiveButton(android.R.string.ok,null).create();
    }
    private interface Edit {void run() throws Exception;}
    private void accept(AlertDialog form,Edit edit){
        form.setOnShowListener(view->form.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button->{
            try{edit.run();form.dismiss();draw();}catch(Exception failure){invalid();}
        }));
        form.show();
        Window window=form.getWindow();
        window.setBackgroundDrawable(new ColorDrawable(0xff151e2d));
        boolean tablet=context.getResources().getConfiguration().screenWidthDp>=600;
        window.setLayout((int)(context.getResources().getDisplayMetrics().widthPixels*(tablet?.72:.94)),
            Math.min(context.getResources().getDisplayMetrics().heightPixels-dp(30),dp(580)));
        window.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
    }
    private void confirm(int title,Runnable action){
        new AlertDialog.Builder(context).setMessage(title).setNegativeButton(android.R.string.cancel,null)
            .setPositiveButton(android.R.string.ok,(view,which)->action.run()).show();
    }
    private void invalid(){Toast.makeText(context,R.string.project_edit_invalid,Toast.LENGTH_LONG).show();}
    private static double parse(EditText input){
        double value=Double.parseDouble(input.getText().toString().trim().replace(',','.'));
        if(!Double.isFinite(value))throw new IllegalArgumentException();
        return value;
    }
    private static String seconds(double value){return String.format(Locale.ROOT,"%.6f",value).replaceFirst("0+$","").replaceFirst("\\.$","");}
}
