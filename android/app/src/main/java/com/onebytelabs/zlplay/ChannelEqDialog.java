package com.onebytelabs.zlplay;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.*;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleToIntFunction;
import java.util.function.IntToDoubleFunction;
import org.json.*;

/** Shared channel/clip EQ with desktop-compatible band shapes and touch controls. */
final class ChannelEqDialog {
    private final Activity context;private final Consumer<JSONObject> saved;private final String title;
    private JSONObject settings;private JSONArray bands;private EditorEqGraph graph;
    private final Slider[] frequencies=new Slider[4],gains=new Slider[4],qualities=new Slider[4];
    private final Spinner[] shapes=new Spinner[4];
    private Slider trim,high,low;
    private static final String[] TYPES={"peak","low","high"};
    private static final int[] COLORS={0xff24d6d9,0xffffbd50,0xffd782ff,0xff78e79e};
    private ChannelEqDialog(Activity context,String title,Consumer<JSONObject> saved){this.context=context;this.title=title;this.saved=saved;}
    static void open(Activity context,String title,JSONObject effects,Consumer<JSONObject> saved){
        try{new ChannelEqDialog(context,title,saved).show(effects);}
        catch(Exception failure){Toast.makeText(context,R.string.project_edit_invalid,Toast.LENGTH_LONG).show();}
    }
    private void show(JSONObject existing)throws Exception{
        settings=existing==null?new JSONObject():new JSONObject(existing.toString());
        JSONArray previous=settings.optJSONArray("bands");bands=new JSONArray();
        int[] defaults={120,500,2500,8000};
        for(int i=0;i<4;i++){
            JSONObject band=previous==null?null:previous.optJSONObject(i);
            if(band==null)band=new JSONObject();
            band.put("type",band.optString("type",i==0?"low":i==3?"high":"peak"))
                .put("freq",band.optDouble("freq",defaults[i])).put("gain",band.optDouble("gain",0)).put("q",band.optDouble("q",.707));
            bands.put(band);
        }
        LinearLayout page=column();page.setPadding(dp(12),dp(4),dp(12),dp(12));
        CheckBox enabled=new CheckBox(context);enabled.setTypeface(font());enabled.setText(R.string.project_eq_enabled);enabled.setChecked(settings.optBoolean("enabled",true));page.addView(enabled);
        graph=new EditorEqGraph(context,bands,index->{JSONObject band=bands.optJSONObject(index);frequencies[index].refresh(band.optDouble("freq"));gains[index].refresh(band.optDouble("gain"));});
        page.addView(graph,new LinearLayout.LayoutParams(-1,dp(170)));
        TextView hint=label(context.getString(R.string.eq_drag_hint),11,0xff93aac6);hint.setPadding(0,dp(4),0,dp(8));page.addView(hint);
        enabled.setOnCheckedChangeListener((button,on)->graph.setAlpha(on?1:.4f));graph.setAlpha(enabled.isChecked()?1:.4f);
        boolean wide=context.getResources().getConfiguration().screenWidthDp>=700;
        LinearLayout global=new LinearLayout(context);global.setOrientation(wide?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);page.addView(global);
        LinearLayout trimCard=card(global,wide),highCard=card(global,wide),lowCard=card(global,wide);
        trim=new Slider(trimCard,R.string.eq_trim,96,settings.optDouble("trim",0),n->(n-48)*.5,v->(int)Math.round(v*2+48),this::db,v->set("trim",v));
        high=new Slider(highCard,R.string.eq_highpass,80,settings.optDouble("highpass",0),n->n*5,v->(int)Math.round(v/5),this::cut,v->set("highpass",v));
        low=new Slider(lowCard,R.string.eq_lowpass,1001,settings.optDouble("lowpass",0),n->n==0?0:fromLog(1001-n),v->v<=0?0:1001-toLog(v),this::cut,v->set("lowpass",v));
        int columns=context.getResources().getConfiguration().screenWidthDp>=1000?4:context.getResources().getConfiguration().screenWidthDp>=460?2:1;
        for(int start=0;start<4;start+=columns){
            LinearLayout row=new LinearLayout(context);page.addView(row);
            for(int i=start;i<Math.min(4,start+columns);i++){
                final int index=i;JSONObject band=bands.getJSONObject(i);LinearLayout box=card(row,true);
                TextView heading=label(context.getString(R.string.project_eq_band,i+1),14,COLORS[i]);heading.setTypeface(Typeface.create(font(),Typeface.BOLD));box.addView(heading);
                box.addView(label(context.getString(R.string.eq_shape),11,0xffa1b4cf));
                Spinner shape=new StyledSpinner(context);shapes[i]=shape;
                String[] labels={context.getString(R.string.eq_peak),context.getString(R.string.eq_low_shelf),context.getString(R.string.eq_high_shelf)};
                ArrayAdapter<String> adapter=new ArrayAdapter<>(context,android.R.layout.simple_spinner_item,labels);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);shape.setAdapter(adapter);
                int selected=0;for(int t=0;t<TYPES.length;t++)if(TYPES[t].equals(band.optString("type")))selected=t;
                shape.setSelection(selected);shape.setContentDescription(context.getString(R.string.project_eq_band,i+1)+" · "+context.getString(R.string.eq_shape));box.addView(shape,new LinearLayout.LayoutParams(-1,dp(40)));
                shape.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> parent){}public void onItemSelected(AdapterView<?> parent,android.view.View view,int position,long id){setBand(index,"type",TYPES[position]);}});
                frequencies[i]=new Slider(box,R.string.project_eq_frequency,1000,band.optDouble("freq"),ChannelEqDialog::fromLog,ChannelEqDialog::toLog,this::hz,v->setBand(index,"freq",v));
                gains[i]=new Slider(box,R.string.eq_gain,72,band.optDouble("gain"),n->(n-36)*.5,v->(int)Math.round(v*2+36),this::db,v->setBand(index,"gain",v));
                qualities[i]=new Slider(box,R.string.project_eq_q,990,band.optDouble("q"),n->.1+n*.01,v->(int)Math.round((v-.1)*100),v->String.format(Locale.getDefault(),"%.2f",v),v->setBand(index,"q",v));
            }
        }
        PerformanceButton reset=new PerformanceButton(context);reset.setText(R.string.eq_reset);reset.setOnClickListener(v->{
            set("trim",0);set("highpass",0);set("lowpass",0);trim.refresh(0);high.refresh(0);low.refresh(0);
            for(int i=0;i<4;i++){setBand(i,"freq",defaults[i]);setBand(i,"gain",0);setBand(i,"q",.707);shapes[i].setSelection(i==0?1:i==3?2:0);frequencies[i].refresh(defaults[i]);gains[i].refresh(0);qualities[i].refresh(.707);}
        });page.addView(reset,new LinearLayout.LayoutParams(-1,dp(38)));
        redraw();ScrollView scroll=new ScrollView(context);scroll.addView(page);
        AlertDialog dialog=new AlertDialog.Builder(context).setTitle(title).setView(scroll).setNegativeButton(android.R.string.cancel,null)
            .setPositiveButton(R.string.apply_project_edits,null).create();dialog.show();
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(0xff101824));
        dialog.getWindow().setLayout((int)(context.getResources().getDisplayMetrics().widthPixels*(wide?.94:.98)),(int)(context.getResources().getDisplayMetrics().heightPixels*.94));
        dialog.getWindow().setGravity(Gravity.CENTER);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{settings.put("enabled",enabled.isChecked()).put("bands",bands);saved.accept(settings);dialog.dismiss();}catch(Exception failure){Toast.makeText(context,R.string.project_edit_invalid,Toast.LENGTH_LONG).show();}});
    }
    private void set(String key,double value){try{settings.put(key,value);redraw();}catch(Exception ignored){}}
    private void setBand(int index,String key,Object value){try{bands.getJSONObject(index).put(key,value);redraw();}catch(Exception ignored){}}
    private void redraw(){if(graph!=null){graph.cuts(settings.optDouble("highpass",0),settings.optDouble("lowpass",0));graph.trim(settings.optDouble("trim",0));}}
    private static double fromLog(int progress){return Math.round(20*Math.pow(1000,progress/1000.0));}
    private static int toLog(double frequency){return (int)Math.round(Math.log(Math.max(20,frequency)/20)/Math.log(1000)*1000);}
    private String db(double value){return String.format(Locale.getDefault(),"%+.1f dB",value);}
    private String hz(double value){return value>=1000?String.format(Locale.getDefault(),"%.2f kHz",value/1000):String.format(Locale.getDefault(),"%.0f Hz",value);}
    private String cut(double value){return value<=0?context.getString(R.string.eq_off):hz(value);}
    private int dp(int value){return Math.round(value*context.getResources().getDisplayMetrics().density);}
    private Typeface font(){return context.getResources().getFont(R.font.inter);}
    private LinearLayout column(){LinearLayout view=new LinearLayout(context);view.setOrientation(LinearLayout.VERTICAL);return view;}
    private LinearLayout card(LinearLayout parent,boolean weight){LinearLayout box=column();box.setPadding(dp(10),dp(10),dp(10),dp(6));GradientDrawable background=new GradientDrawable();background.setColor(0xff172333);background.setCornerRadius(dp(10));background.setStroke(dp(1),0xff2a3a50);box.setBackground(background);LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(weight?0:-1,-2,weight?1:0);size.setMargins(dp(3),dp(5),dp(3),dp(5));parent.addView(box,size);return box;}
    private TextView label(String text,int size,int color){TextView view=new TextView(context);view.setTypeface(font());view.setText(text);view.setTextSize(size);view.setTextColor(color);return view;}
    private final class Slider {
        private final SeekBar input;private final TextView output;private final DoubleFunction<String> format;private final DoubleToIntFunction progress;
        Slider(LinearLayout parent,int title,int maximum,double initial,IntToDoubleFunction value,DoubleToIntFunction progress,DoubleFunction<String> format,DoubleConsumer changed){
            this.format=format;this.progress=progress;
            LinearLayout heading=new LinearLayout(context);heading.setGravity(Gravity.CENTER_VERTICAL);TextView name=label(context.getString(title),11,0xffadbed4);heading.addView(name,new LinearLayout.LayoutParams(0,-2,1));output=label("",12,0xffcbb0ff);output.setGravity(Gravity.END);heading.addView(output);parent.addView(heading);
            input=new SeekBar(context);input.setMax(maximum);input.setProgressTintList(ColorStateList.valueOf(0xffa779f0));input.setThumbTintList(ColorStateList.valueOf(0xffd1b2ff));input.setProgressBackgroundTintList(ColorStateList.valueOf(0xff35465e));input.setContentDescription(context.getString(title));parent.addView(input,new LinearLayout.LayoutParams(-1,dp(36)));refresh(initial);
            input.setOnTouchListener((view,event)->{int action=event.getActionMasked();view.getParent().requestDisallowInterceptTouchEvent(action!=MotionEvent.ACTION_UP&&action!=MotionEvent.ACTION_CANCEL);return false;});
            input.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar bar){}public void onStopTrackingTouch(SeekBar bar){}public void onProgressChanged(SeekBar bar,int n,boolean fromUser){if(fromUser){double result=value.applyAsDouble(n);output.setText(format.apply(result));changed.accept(result);}}});
        }
        void refresh(double value){input.setProgress(Math.max(0,Math.min(input.getMax(),progress.applyAsInt(value))));output.setText(format.apply(value));}
    }
}
