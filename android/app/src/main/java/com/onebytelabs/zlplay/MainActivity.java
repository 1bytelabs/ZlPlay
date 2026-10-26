package com.onebytelabs.zlplay;

import android.app.Activity;
import android.os.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import com.onebytelabs.zlplay.audio.*;
import java.util.Locale;

public final class MainActivity extends Activity {
    private final DemoMixer mixer=new DemoMixer();
    private AudioSession audio;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextView clock,status;
    private Button play,loop,click,pad;
    private LinearLayout mixerPanel;
    private Waveform waveform;
    private final int purple=Color.rgb(151,105,239),panel=Color.rgb(23,31,47),text=Color.rgb(230,235,248);
    private final Runnable refresh=new Runnable(){public void run(){
        if(clock==null)return;
        clock.setText(String.format(Locale.getDefault(),"%d:%02d / 1:30",(int)mixer.position()/60,(int)mixer.position()%60));
        state(play,mixer.isPlaying());state(loop,mixer.isLoop());state(click,mixer.isClick());state(pad,mixer.isPad());
        if(audio!=null&&audio.error()!=null)status.setText(R.string.audio_error);
        waveform.invalidate();handler.postDelayed(this,50);
    }};
    private int dp(float x){return Math.round(x*getResources().getDisplayMetrics().density);}
    private GradientDrawable background(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(7));d.setStroke(dp(1),Color.rgb(58,67,91));return d;}
    private TextView label(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextColor(text);t.setTextSize(size);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    private LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);return r;}
    private Button button(int title,Runnable action){Button b=new Button(this);b.setText(title);b.setTextColor(text);b.setTextSize(11);b.setAllCaps(false);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(dp(9),0,dp(9),0);b.setBackground(background(panel));b.setOnClickListener(v->action.run());return b;}
    private void addButton(LinearLayout r,Button b){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(40),1);lp.setMargins(dp(2),dp(3),dp(2),dp(3));r.addView(b,lp);}
    private void state(Button b,boolean active){b.setBackground(background(active?purple:panel));b.setSelected(active);}
    private boolean ready(){if(audio==null)audio=new AudioSession(this,mixer);boolean ok=audio.start();if(!ok)status.setText(R.string.focus_denied);else status.setText(R.string.synthetic_notice);return ok;}
    @Override public void onCreate(Bundle saved){super.onCreate(saved);
        var prefs=getSharedPreferences("mix",MODE_PRIVATE);mixer.setBpm(prefs.getInt("bpm",120));for(int i=0;i<3;i++)mixer.setGain(i,prefs.getFloat("gain"+i,i==2?.4f:.55f));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Color.rgb(10,15,25));
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(12),dp(6),dp(12),dp(8));scroll.addView(root);setContentView(scroll);
        root.setOnApplyWindowInsetsListener((v,insets)->{if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(dp(12)+bars.left,dp(6)+bars.top,dp(12)+bars.right,dp(8)+bars.bottom);}else{v.setPadding(dp(12)+insets.getSystemWindowInsetLeft(),dp(6)+insets.getSystemWindowInsetTop(),dp(12)+insets.getSystemWindowInsetRight(),dp(8)+insets.getSystemWindowInsetBottom());}return insets;});
        LinearLayout heading=row();TextView title=label("ZLPlay",23);title.setTypeface(null,Typeface.BOLD);heading.addView(title,new LinearLayout.LayoutParams(0,dp(34),1));TextView badge=label(getString(R.string.prototype),11);badge.setTextColor(0xffbd9bff);heading.addView(badge);root.addView(heading);
        TextView song=label(getString(R.string.demo_project),14);song.setPadding(dp(12),0,dp(12),0);song.setBackground(background(panel));root.addView(song,new LinearLayout.LayoutParams(-1,dp(32)));
        LinearLayout transport=row();
        play=button(R.string.play,()->{if(ready())mixer.play();});addButton(transport,play);
        addButton(transport,button(R.string.pause,mixer::pause));addButton(transport,button(R.string.stop,mixer::stop));
        loop=button(R.string.loop,()->mixer.setLoop(!mixer.isLoop()));addButton(transport,loop);
        click=button(R.string.solo_click,()->{if(mixer.isClick())mixer.setClick(false);else if(ready())mixer.setClick(true);});addButton(transport,click);
        pad=button(R.string.pad,()->{if(mixer.isPad())mixer.setPad(false);else if(ready())mixer.setPad(true);});addButton(transport,pad);
        addButton(transport,button(R.string.mixer,()->mixerPanel.setVisibility(mixerPanel.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE)));root.addView(transport);
        LinearLayout tempo=row();tempo.addView(label(getString(R.string.bpm),12));Button minus=button(R.string.decrease,()->setBpm(mixer.getBpm()-1));Button plus=button(R.string.increase,()->setBpm(mixer.getBpm()+1));
        EditText bpm=new EditText(this);bpm.setId(View.generateViewId());bpm.setSingleLine();bpm.setTextColor(text);bpm.setTextSize(14);bpm.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);bpm.setText(String.valueOf(mixer.getBpm()));bpm.setContentDescription(getString(R.string.bpm));bpm.setSelectAllOnFocus(true);bpm.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        bpm.setOnEditorActionListener((v,action,event)->{try{setBpm(Integer.parseInt(bpm.getText().toString()));}catch(NumberFormatException ignored){}bpm.setText(String.valueOf(mixer.getBpm()));bpm.clearFocus();((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(bpm.getWindowToken(),0);return true;});
        minus.setOnClickListener(v->{setBpm(mixer.getBpm()-1);bpm.setText(String.valueOf(mixer.getBpm()));});plus.setOnClickListener(v->{setBpm(mixer.getBpm()+1);bpm.setText(String.valueOf(mixer.getBpm()));});
        tempo.addView(minus,new LinearLayout.LayoutParams(dp(40),dp(34)));tempo.addView(bpm,new LinearLayout.LayoutParams(dp(65),dp(38)));tempo.addView(plus,new LinearLayout.LayoutParams(dp(40),dp(34)));clock=label("0:00 / 1:30",12);clock.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);tempo.addView(clock,new LinearLayout.LayoutParams(0,dp(34),1));root.addView(tempo);
        waveform=new Waveform();root.addView(waveform,new LinearLayout.LayoutParams(-1,dp(104)));waveform.setContentDescription(getString(R.string.timeline));
        status=label(getString(R.string.synthetic_notice),11);root.addView(status,new LinearLayout.LayoutParams(-1,dp(26)));
        mixerPanel=new LinearLayout(this);mixerPanel.setOrientation(LinearLayout.VERTICAL);mixerPanel.addView(label(getString(R.string.mixer),14));LinearLayout channels=row();
        int[] names={R.string.bass,R.string.keys,R.string.drums};
        for(int i=0;i<3;i++){final int track=i;LinearLayout strip=new LinearLayout(this);strip.setOrientation(LinearLayout.VERTICAL);strip.setGravity(Gravity.CENTER);strip.setBackground(background(panel));TextView name=label(getString(names[i]),11);strip.addView(name,new LinearLayout.LayoutParams(-2,dp(24)));FrameLayout container=new FrameLayout(this);SeekBar gain=new SeekBar(this);gain.setMax(100);gain.setProgress(Math.round(mixer.getGain(i)*100));gain.setRotation(-90);gain.setContentDescription(getString(R.string.volume,getString(names[i])));FrameLayout.LayoutParams slider=new FrameLayout.LayoutParams(dp(110),dp(36),Gravity.CENTER);container.addView(gain,slider);strip.addView(container,new LinearLayout.LayoutParams(dp(40),dp(112)));TextView value=label(getString(R.string.percent,gain.getProgress()),10);strip.addView(value);gain.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar bar,int p,boolean user){mixer.setGain(track,p/100f);value.setText(getString(R.string.percent,p));getSharedPreferences("mix",MODE_PRIVATE).edit().putFloat("gain"+track,p/100f).apply();}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(dp(68),dp(160));size.setMargins(0,0,dp(6),0);channels.addView(strip,size);}
        mixerPanel.addView(channels);root.addView(mixerPanel);mixerPanel.setVisibility(getResources().getConfiguration().smallestScreenWidthDp>=600?View.VISIBLE:View.GONE);
    }
    private void setBpm(int value){mixer.setBpm(value);getSharedPreferences("mix",MODE_PRIVATE).edit().putInt("bpm",mixer.getBpm()).apply();}
    @Override protected void onResume(){super.onResume();handler.post(refresh);}
    @Override protected void onPause(){handler.removeCallbacks(refresh);if(audio!=null){audio.close();audio=null;}super.onPause();}
    private final class Waveform extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Waveform(){super(MainActivity.this);setBackground(background(panel));}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float w=getWidth(),h=getHeight();int[] colors={0xff345c7b,0xff65428e,0xff2c694a};int[] labels={R.string.intro,R.string.verse,R.string.chorus};for(int r=0;r<3;r++){p.setColor(colors[r]);canvas.drawRect(r*w/3,0,(r+1)*w/3,h,p);p.setColor(text);p.setTextSize(dp(11));canvas.drawText(getString(labels[r]),r*w/3+dp(8),dp(18),p);}p.setStrokeWidth(dp(1));p.setColor(0xff7caec7);for(int x=0;x<w;x+=dp(3)){float amp=(float)(.12+.55*Math.abs(Math.sin(x*.079)*Math.sin(x*.013)))*h*.3f;canvas.drawLine(x,h*.62f-amp,x,h*.62f+amp,p);}p.setColor(0xffd9d6f7);for(int r=1;r<3;r++)canvas.drawLine(r*w/3,0,r*w/3,h,p);p.setColor(Color.WHITE);p.setStrokeWidth(dp(2));float x=(float)(mixer.position()/90*w);canvas.drawLine(x,0,x,h,p);}
        @Override public boolean onTouchEvent(android.view.MotionEvent e){if(e.getAction()==android.view.MotionEvent.ACTION_DOWN||e.getAction()==android.view.MotionEvent.ACTION_MOVE){getParent().requestDisallowInterceptTouchEvent(true);mixer.seek(Math.max(0,Math.min(1,e.getX()/getWidth()))*90);invalidate();return true;}if(e.getAction()==android.view.MotionEvent.ACTION_UP){performClick();return true;}return true;}
        @Override public boolean performClick(){super.performClick();return true;}
    }
}
