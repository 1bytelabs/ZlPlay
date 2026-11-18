package com.onebytelabs.zlplay;

import android.app.Activity;
import android.app.Dialog;
import android.app.AlertDialog;
import android.content.ClipData;
import android.graphics.drawable.ColorDrawable;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.function.Consumer;
import org.json.*;

/** Mixer organization uses the same named groups and mixerOrder as desktop. */
final class TrackOrganizerDialog {
    private final Activity context;private final JSONObject project;private final Consumer<String> saved;
    private final Dialog dialog;private final LinearLayout body;private final List<String> order=new ArrayList<>(),groups=new ArrayList<>();
    private TrackOrganizerDialog(Activity context,String manifest,Consumer<String> saved)throws Exception{
        this.context=context;this.project=new JSONObject(manifest);this.saved=saved;dialog=new Dialog(context);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        JSONObject settings=project.optJSONObject("settings"),mix=settings==null?null:settings.optJSONObject("mixerOrder");
        collect(order,mix==null?null:mix.optJSONArray("tracks"));collect(groups,mix==null?null:mix.optJSONArray("groups"));
        JSONArray states=project.optJSONObject("mixer")==null?null:project.optJSONObject("mixer").optJSONArray("groupStates");
        if(states!=null)for(int i=0;i<states.length();i++)add(groups,states.getJSONArray(i).getString(0));
        for(int i=0;i<tracks().length();i++){JSONObject track=tracks().getJSONObject(i);add(order,track.optString("name"));if(!special(track))add(groups,track.optString("group","VS"));}
        groups.remove("VS");groups.remove("CLICK / GUIA / PAD");groups.remove(context.getString(R.string.special_channels));
        order.removeIf(name->{for(int i=0;i<tracks().length();i++){JSONObject track=tracks().optJSONObject(i);if(name.equals(track.optString("name"))&&special(track))return true;}return false;});
        LinearLayout page=column();page.setPadding(dp(16),dp(12),dp(16),dp(12));page.setBackgroundColor(0xff101824);
        TextView heading=new TextView(context);heading.setText(R.string.project_organize_tracks);heading.setTextSize(19);page.addView(heading);
        TextView hint=new TextView(context);hint.setText(R.string.project_organize_help);page.addView(hint);
        LinearLayout fixed=column();fixed.setPadding(dp(8),dp(10),dp(8),dp(10));fixed.setBackgroundColor(0xff182438);
        TextView specialTitle=new TextView(context);specialTitle.setText(R.string.special_channels);specialTitle.setTextSize(12);specialTitle.setTextColor(0xffb2c5df);fixed.addView(specialTitle);
        LinearLayout channels=new LinearLayout(context);
        int[] labels={R.string.click_track,R.string.guide_track,R.string.pad};
        for(int label:labels){TextView channel=new TextView(context);channel.setText(label);channel.setTextSize(14);channel.setGravity(Gravity.CENTER_VERTICAL);channel.setPadding(dp(8),0,dp(8),0);channels.addView(channel,new LinearLayout.LayoutParams(0,dp(38),1));}
        fixed.addView(channels);page.addView(fixed);
        ScrollView scroll=new ScrollView(context);body=column();scroll.addView(body);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout actions=new LinearLayout(context);button(actions,context.getString(R.string.project_create_group),this::createGroup);
        button(actions,context.getString(android.R.string.cancel),dialog::dismiss);
        button(actions,context.getString(R.string.apply_project_edits),()->{
            try{JSONObject prefs=project.optJSONObject("settings");if(prefs==null){prefs=new JSONObject();project.put("settings",prefs);}prefs.put("mixerOrder",new JSONObject().put("tracks",new JSONArray(order)).put("groups",new JSONArray(groups)));
                JSONObject mixer=project.optJSONObject("mixer");if(mixer==null){mixer=new JSONObject();project.put("mixer",mixer);}JSONArray old=mixer.optJSONArray("groupStates"),statesOut=new JSONArray();
                for(String group:groups){JSONObject state=null;if(old!=null)for(int i=0;i<old.length();i++){JSONArray entry=old.getJSONArray(i);if(entry.optString(0).equals(group))state=entry.getJSONObject(1);}
                    if(state==null)state=new JSONObject().put("volume",1).put("pan",0).put("mute",false).put("solo",false);
                    statesOut.put(new JSONArray().put(group).put(state));}
                mixer.put("groupStates",statesOut);saved.accept(project.toString());dialog.dismiss();
            }catch(Exception failure){invalid();}
        });page.addView(actions);dialog.setContentView(page);render();
    }
    static void open(Activity context,String manifest,Consumer<String> saved){try{TrackOrganizerDialog editor=new TrackOrganizerDialog(context,manifest,saved);editor.dialog.show();editor.dialog.getWindow().setBackgroundDrawable(new ColorDrawable(0xff101824));editor.dialog.getWindow().setLayout((int)(context.getResources().getDisplayMetrics().widthPixels*.94),(int)(context.getResources().getDisplayMetrics().heightPixels*.92));}catch(Exception failure){Toast.makeText(context,R.string.project_edit_invalid,Toast.LENGTH_LONG).show();}}
    private static void add(List<String> list,String value){if(!list.contains(value)&&!value.isEmpty())list.add(value);}
    private static void collect(List<String> list,JSONArray array){if(array!=null)for(int i=0;i<array.length();i++)add(list,array.optString(i));}
    private JSONArray tracks(){return project.optJSONArray("tracks");}
    private boolean special(JSONObject track){return com.onebytelabs.zlplay.project.ProjectSpec.isClickName(track.optString("name"))||com.onebytelabs.zlplay.project.ProjectSpec.isGuideName(track.optString("name"));}
    private LinearLayout column(){LinearLayout layout=new LinearLayout(context);layout.setOrientation(LinearLayout.VERTICAL);return layout;}
    private int dp(int n){return Math.round(n*context.getResources().getDisplayMetrics().density);}
    private void button(LinearLayout row,String text,Runnable action){PerformanceButton b=new PerformanceButton(context);b.setText(text);b.setTextSize(12);b.setOnClickListener(v->action.run());LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-2,dp(38));size.setMargins(dp(3),dp(3),dp(3),dp(3));row.addView(b,size);}
    private void createGroup(){EditText name=new EditText(context);name.setSingleLine(true);name.setHint(R.string.project_group_name);new AlertDialog.Builder(context).setTitle(R.string.project_create_group).setView(name).setNegativeButton(android.R.string.cancel,null).setPositiveButton(android.R.string.ok,(d,w)->{String value=name.getText().toString().trim();if(value.isEmpty()||value.equalsIgnoreCase("VS")||value.equals("CLICK / GUIA / PAD")||groups.contains(value)){invalid();return;}groups.add(value);render();}).show();}
    private void render(){
        body.removeAllViews();List<String> sections=new ArrayList<>();sections.add("VS");sections.addAll(groups);
        for(String group:sections){
            LinearLayout header=new LinearLayout(context);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(8),dp(7),dp(8),dp(3));header.setBackgroundColor(0xff243047);
            if(!group.equals("VS"))header.setPadding(dp(24),dp(7),dp(8),dp(3));
            TextView title=new TextView(context);title.setText(group.equals("VS")?context.getString(R.string.vs_bus):group);title.setTextSize(14);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
            int groupIndex=groups.indexOf(group);if(groupIndex>=0){button(header,"↑",()->move(groups,groupIndex,-1));button(header,"↓",()->move(groups,groupIndex,1));}
            body.addView(header);
            for(String name:order)for(int i=0;i<tracks().length();i++){
                JSONObject track=tracks().optJSONObject(i);if(special(track)||!track.optString("name").equals(name))continue;
                String section=special(track)?context.getString(R.string.special_channels):track.optString("group","VS");if(!section.equals(group))continue;
                LinearLayout row=new LinearLayout(context);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(8),dp(4),dp(8),dp(4));
                TextView label=new TextView(context);label.setText("≡  "+name);label.setTextSize(13);row.addView(label,new LinearLayout.LayoutParams(0,dp(42),1));
                label.setOnLongClickListener(v->{v.startDragAndDrop(ClipData.newPlainText("track",name),new View.DragShadowBuilder(v),name,0);return true;});
                row.setOnDragListener((v,event)->{if(event.getAction()==DragEvent.ACTION_DRAG_STARTED)return event.getLocalState() instanceof String;if(event.getAction()==DragEvent.ACTION_DROP){String source=(String)event.getLocalState();if(source.equals(name))return true;order.remove(source);order.add(order.indexOf(name),source);try{if(!special(track))for(int t=0;t<tracks().length();t++){JSONObject dragged=tracks().getJSONObject(t);if(dragged.optString("name").equals(source)&&!special(dragged))dragged.put("group",track.optString("group","VS"));}}catch(Exception failure){invalid();}render();return true;}return true;});
                button(row,"↑",()->moveTrack(name,-1));button(row,"↓",()->moveTrack(name,1));
                if(!special(track)){
                    List<String> choices=new ArrayList<>();choices.add("VS");choices.addAll(groups);Spinner target=new StyledSpinner(context);ArrayAdapter<String> adapter=new ArrayAdapter<>(context,android.R.layout.simple_spinner_item,choices);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);target.setAdapter(adapter);target.setSelection(Math.max(0,choices.indexOf(track.optString("group","VS"))));target.setContentDescription(context.getString(R.string.project_group_for,name));
                    target.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> parent){}public void onItemSelected(AdapterView<?> parent,View view,int position,long id){String selected=choices.get(position);if(!selected.equals(track.optString("group","VS")))try{track.put("group",selected);render();}catch(Exception failure){invalid();}}});row.addView(target,new LinearLayout.LayoutParams(dp(155),dp(42)));
                }
                body.addView(row);
            }
        }
    }
    private void moveTrack(String name,int direction){
        String group=null;for(int i=0;i<tracks().length();i++){JSONObject track=tracks().optJSONObject(i);if(name.equals(track.optString("name")))group=track.optString("group","VS");}
        int index=order.indexOf(name);
        for(int next=index+direction;next>=0&&next<order.size();next+=direction)for(int i=0;i<tracks().length();i++){
            JSONObject track=tracks().optJSONObject(i);
            if(order.get(next).equals(track.optString("name"))&&Objects.equals(group,track.optString("group","VS"))){Collections.swap(order,index,next);render();return;}
        }
    }
    private void move(List<String> list,int index,int direction){int next=index+direction;if(next<0||next>=list.size())return;Collections.swap(list,index,next);render();}
    private void invalid(){Toast.makeText(context,R.string.project_edit_invalid,Toast.LENGTH_LONG).show();}
}
