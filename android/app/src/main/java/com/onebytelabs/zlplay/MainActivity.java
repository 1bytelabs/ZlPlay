package com.onebytelabs.zlplay;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONArray;
import android.os.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import com.onebytelabs.zlplay.audio.*;
import com.onebytelabs.zlplay.project.*;
import java.util.Locale;

public final class MainActivity extends Activity {
    private final DemoMixer mixer=new DemoMixer();
    private AudioSession audio;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextView clock,status,songTitle;
    public static final int OPEN_WAV=100,OPEN_PROJECT=101;
    private ProjectSpec activeProject;private String projectKey;private Button projectButton;private EditText bpmField;
    private final ExecutorService imports=Executors.newSingleThreadExecutor();
    private volatile boolean destroyed;
    private boolean loading;
    private int lastImportError;
    private android.app.AlertDialog importErrorDialog,projectNotice;
    private Button importButton;
    private List<Uri> currentUris=new ArrayList<>();
    private float[] sourcePeaks;
    private Button play,loop,click,pad;
    private LinearLayout mixerPanel;
    private android.app.Dialog mixerDialog;
    private Waveform waveform;
    private final int purple=Color.rgb(151,105,239),panel=Color.rgb(23,31,47),text=Color.rgb(230,235,248);
    private final Runnable refresh=new Runnable(){public void run(){
        if(clock==null)return;
        boolean activeAudio=mixer.isPlaying()||mixer.isClick()||mixer.isPad();getWindow().getDecorView().setKeepScreenOn(activeAudio);if(mixerDialog!=null&&mixerDialog.getWindow()!=null)mixerDialog.getWindow().getDecorView().setKeepScreenOn(activeAudio);
        clock.setText(getString(R.string.duration_format,time(mixer.position()),time(mixer.duration())));
        state(play,mixer.isPlaying());state(loop,mixer.isLoop());state(click,mixer.isClick());state(pad,mixer.isPad());
        if(mixer.sourceFailed())status.setText(R.string.source_unavailable);else if(audio!=null&&audio.error()!=null)status.setText(R.string.audio_error);else if(!loading&&lastImportError==0&&mixer.hasImported()&&mixer.isPlaying())status.setText(mixer.isBuffering()?R.string.buffering:R.string.import_done);
        waveform.invalidate();handler.postDelayed(this,50);
    }};
    private int dp(float x){return Math.round(x*getResources().getDisplayMetrics().density);}
    private GradientDrawable background(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(7));d.setStroke(dp(1),Color.rgb(58,67,91));return d;}
    private TextView label(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextColor(text);t.setTextSize(size);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    private LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);return r;}
    private Button button(int title,Runnable action){Button b=new Button(this);b.setText(title);b.setTextColor(text);b.setTextSize(11);b.setAllCaps(false);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(dp(9),0,dp(9),0);b.setBackground(background(panel));b.setOnClickListener(v->action.run());return b;}
    private void addButton(LinearLayout r,Button b){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(40),1);lp.setMargins(dp(2),dp(3),dp(2),dp(3));r.addView(b,lp);}
    private void state(Button b,boolean active){b.setBackground(background(active?purple:panel));b.setSelected(active);}
    private boolean ready(){if(loading)return false;if(audio==null)audio=new AudioSession(this,mixer);boolean ok=audio.start();if(!ok)status.setText(R.string.focus_denied);else if(lastImportError==0)status.setText(mixer.hasImported()?R.string.import_done:R.string.synthetic_notice);return ok;}
    @Override public void onCreate(Bundle saved){super.onCreate(saved);
        var prefs=getSharedPreferences("mix",MODE_PRIVATE);mixer.setPadLevel(prefs.getFloat("padLevel",.7f));mixer.setBpm(prefs.getInt("bpm",120));for(int i=0;i<3;i++)mixer.setGain(i,prefs.getFloat("gain"+i,i==2?.4f:.55f));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Color.rgb(10,15,25));
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(12),dp(6),dp(12),dp(8));scroll.addView(root);setContentView(scroll);
        root.setOnApplyWindowInsetsListener((v,insets)->{if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(dp(12)+bars.left,dp(6)+bars.top,dp(12)+bars.right,dp(8)+bars.bottom);}else{v.setPadding(dp(12)+insets.getSystemWindowInsetLeft(),dp(6)+insets.getSystemWindowInsetTop(),dp(12)+insets.getSystemWindowInsetRight(),dp(8)+insets.getSystemWindowInsetBottom());}return insets;});
        LinearLayout heading=row();TextView title=label("ZLPlay",23);title.setTypeface(null,Typeface.BOLD);heading.addView(title,new LinearLayout.LayoutParams(0,dp(34),1));TextView badge=label(getString(R.string.prototype),11);badge.setTextColor(0xffbd9bff);heading.addView(badge);projectButton=button(R.string.open_project,this::chooseProject);heading.addView(projectButton,new LinearLayout.LayoutParams(dp(120),dp(36)));importButton=button(R.string.import_wav,this::chooseWav);heading.addView(importButton,new LinearLayout.LayoutParams(dp(120),dp(36)));root.addView(heading);
        songTitle=label(getString(R.string.demo_project),14);songTitle.setPadding(dp(12),0,dp(12),0);songTitle.setBackground(background(panel));root.addView(songTitle,new LinearLayout.LayoutParams(-1,dp(32)));
        LinearLayout transport=row();
        play=button(R.string.play,()->{if(ready())mixer.play();});addButton(transport,play);
        addButton(transport,button(R.string.pause,mixer::pause));addButton(transport,button(R.string.stop,mixer::stop));
        loop=button(R.string.loop,()->mixer.setLoop(!mixer.isLoop()));addButton(transport,loop);
        click=button(R.string.solo_click,()->{if(mixer.isClick())mixer.setClick(false);else if(ready())mixer.setClick(true);});addButton(transport,click);
        pad=button(R.string.pad,()->{if(mixer.isPad())mixer.setPad(false);else if(ready())mixer.setPad(true);});addButton(transport,pad);
        addButton(transport,button(R.string.mixer,this::showMixer));root.addView(transport);
        LinearLayout tempo=row();tempo.addView(label(getString(R.string.bpm),12));Button minus=button(R.string.decrease,()->setBpm(mixer.getBpm()-1));Button plus=button(R.string.increase,()->setBpm(mixer.getBpm()+1));
        EditText bpm=new EditText(this);bpmField=bpm;bpm.setId(View.generateViewId());bpm.setSingleLine();bpm.setTextColor(text);bpm.setTextSize(14);bpm.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);bpm.setText(String.valueOf(mixer.getBpm()));bpm.setContentDescription(getString(R.string.bpm));bpm.setSelectAllOnFocus(true);bpm.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        bpm.setOnEditorActionListener((v,action,event)->{try{setBpm(Integer.parseInt(bpm.getText().toString()));}catch(NumberFormatException ignored){}bpm.setText(String.valueOf(mixer.getBpm()));bpm.clearFocus();((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(bpm.getWindowToken(),0);return true;});
        minus.setOnClickListener(v->{setBpm(mixer.getBpm()-1);bpm.setText(String.valueOf(mixer.getBpm()));});plus.setOnClickListener(v->{setBpm(mixer.getBpm()+1);bpm.setText(String.valueOf(mixer.getBpm()));});
        tempo.addView(minus,new LinearLayout.LayoutParams(dp(40),dp(34)));tempo.addView(bpm,new LinearLayout.LayoutParams(dp(65),dp(38)));tempo.addView(plus,new LinearLayout.LayoutParams(dp(40),dp(34)));clock=label("0:00 / 1:30",12);clock.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);tempo.addView(clock,new LinearLayout.LayoutParams(0,dp(34),1));root.addView(tempo);
        waveform=new Waveform();root.addView(waveform,new LinearLayout.LayoutParams(-1,dp(104)));waveform.setContentDescription(getString(R.string.timeline));
        status=label(getString(R.string.synthetic_notice),11);status.setMinHeight(dp(26));root.addView(status,new LinearLayout.LayoutParams(-1,-2));
        mixerPanel=new LinearLayout(this);mixerPanel.setOrientation(LinearLayout.VERTICAL);root.addView(mixerPanel);renderMixer();mixerPanel.setVisibility(getResources().getConfiguration().smallestScreenWidthDp>=600?View.VISIBLE:View.GONE);restoreImport();
    }

    private String time(double seconds){return String.format(Locale.getDefault(),"%d:%02d",(int)seconds/60,(int)seconds%60);}
    private String gainKey(int track){return activeProject!=null?"projectGain:"+projectKey+":"+track:mixer.hasImported()?"wavGain:"+currentUris.get(track):"gain"+track;}
    private void renderMixer(){
        mixerPanel.removeAllViews();if(getResources().getConfiguration().smallestScreenWidthDp>=600)mixerPanel.addView(label(getString(R.string.mixer),14));
        int stripHeight=mixerDialog==null?160:Math.max(140,Math.min(300,Math.round(getResources().getDisplayMetrics().heightPixels/getResources().getDisplayMetrics().density)-112));
        int faderHeight=stripHeight-48;
        HorizontalScrollView scroll=new HorizontalScrollView(this);LinearLayout channels=row();scroll.addView(channels);mixerPanel.addView(scroll);
        final int count=mixer.trackCount();int[] demoNames={R.string.bass,R.string.keys,R.string.drums};
        for(int i=0;i<=count;i++){
            final int track=i;final boolean isPad=i==count;
            String name=isPad?getString(R.string.pad):mixer.hasImported()?mixer.trackName(i):getString(demoNames[i]);
            LinearLayout strip=new LinearLayout(this);strip.setOrientation(LinearLayout.VERTICAL);strip.setGravity(Gravity.CENTER);strip.setBackground(background(panel));
            TextView title=label(name,11);title.setSingleLine();title.setEllipsize(android.text.TextUtils.TruncateAt.END);title.setContentDescription(name);strip.addView(title,new LinearLayout.LayoutParams(dp(62),dp(24)));
            FrameLayout container=new FrameLayout(this);SeekBar gain=new FaderSeekBar(this);gain.setMax(isPad||activeProject==null?100:Math.max(200,Math.round(mixer.getGain(i)*100)));gain.setProgress(Math.round((isPad?mixer.getPadLevel():mixer.getGain(i))*100));gain.setRotation(-90);gain.setContentDescription(getString(R.string.volume,name));
            container.addView(gain,new FrameLayout.LayoutParams(dp(faderHeight-8),dp(36),Gravity.CENTER));strip.addView(container,new LinearLayout.LayoutParams(dp(48),dp(faderHeight)));
            TextView value=label(getString(R.string.percent,gain.getProgress()),10);strip.addView(value);
            gain.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                public void onProgressChanged(SeekBar bar,int p,boolean user){if(isPad)mixer.setPadLevel(p/100f);else mixer.setGain(track,p/100f);value.setText(getString(R.string.percent,p));getSharedPreferences("mix",MODE_PRIVATE).edit().putFloat(isPad?"padLevel":gainKey(track),p/100f).apply();}
                public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}
            });
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(dp(68),dp(stripHeight));size.setMargins(0,0,dp(6),0);channels.addView(strip,size);
        }
    }
    private void showMixer(){
        if(getResources().getConfiguration().smallestScreenWidthDp>=600){mixerPanel.setVisibility(mixerPanel.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);return;}
        if(mixerDialog!=null)return;
        final ViewGroup parent=(ViewGroup)mixerPanel.getParent();final int index=parent.indexOfChild(mixerPanel);final ViewGroup.LayoutParams previous=mixerPanel.getLayoutParams();parent.removeView(mixerPanel);
        android.app.Dialog dialog=new android.app.Dialog(this,android.R.style.Theme_Material_NoActionBar);mixerDialog=dialog;
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setBackgroundColor(Color.rgb(10,15,25));content.setPadding(dp(12),dp(8),dp(12),dp(8));
        LinearLayout header=row();TextView title=label(getString(R.string.mixer),22);title.setTypeface(null,Typeface.BOLD);header.addView(title,new LinearLayout.LayoutParams(0,dp(42),1));header.addView(button(R.string.back,dialog::dismiss),new LinearLayout.LayoutParams(dp(90),dp(40)));content.addView(header);
        content.addView(mixerPanel,new LinearLayout.LayoutParams(-1,0,1));mixerPanel.setVisibility(View.VISIBLE);renderMixer();dialog.setContentView(content);
        dialog.setOnDismissListener(ignored->{((ViewGroup)mixerPanel.getParent()).removeView(mixerPanel);mixerDialog=null;if(!destroyed){parent.addView(mixerPanel,index,previous);mixerPanel.setVisibility(View.GONE);renderMixer();}});
        content.setOnApplyWindowInsetsListener((view,insets)->{if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());view.setPadding(dp(12)+bars.left,dp(8)+bars.top,dp(12)+bars.right,dp(8)+bars.bottom);}else view.setPadding(dp(12)+insets.getSystemWindowInsetLeft(),dp(8)+insets.getSystemWindowInsetTop(),dp(12)+insets.getSystemWindowInsetRight(),dp(8)+insets.getSystemWindowInsetBottom());return insets;});
        dialog.show();if(dialog.getWindow()!=null)dialog.getWindow().setLayout(-1,-1);
    }

    private void chooseProject(){
        if(loading)return;new android.app.AlertDialog.Builder(this).setTitle(R.string.open_project).setMessage(R.string.project_folder_help).setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.choose_folder,(dialog,which)->{Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);try{startActivityForResult(picker,OPEN_PROJECT);}catch(android.content.ActivityNotFoundException unavailable){status.setText(R.string.no_picker);}}).show();
    }
    private void openProjectFolder(Uri tree,String selected,boolean persistent){
        if(loading)return;loading=true;lastImportError=0;importButton.setEnabled(false);projectButton.setEnabled(false);status.setText(R.string.scanning_project);
        imports.execute(()->{try{ProjectFolder folder=ProjectFolder.scan(getApplicationContext(),tree);List<String> choices=folder.projects();runOnUiThread(()->{if(destroyed)return;if(selected!=null&&choices.contains(selected)){loadProject(folder,selected,persistent);return;}loading=false;importButton.setEnabled(true);projectButton.setEnabled(true);if(choices.isEmpty()){importFailure(R.string.no_projects);return;}new android.app.AlertDialog.Builder(this).setTitle(R.string.choose_project).setItems(choices.toArray(new String[0]),(dialog,index)->{loading=true;loadProject(folder,choices.get(index),persistent);}).setNegativeButton(android.R.string.cancel,null).show();});}catch(Exception failure){importFailure(R.string.project_failed);}});
    }
    void loadProject(ProjectFolder folder,String filename,boolean persistent){
        loading=true;lastImportError=0;if(projectNotice!=null)projectNotice.dismiss();importButton.setEnabled(false);projectButton.setEnabled(false);mixer.silence();if(audio!=null){audio.close();audio=null;}status.setText(R.string.loading_audio);
        imports.execute(()->{try{ProjectLoader.Result result=ProjectLoader.read(getApplicationContext(),folder,filename,(done,total)->runOnUiThread(()->{if(!destroyed)status.setText(getString(R.string.loading_count,done,total));}));runOnUiThread(()->{if(destroyed){result.close();return;}activeProject=result.spec;projectKey=folder.tree+":"+filename;currentUris=new ArrayList<>();sourcePeaks=result.peaks;mixer.load(result.tracks);mixer.configureProject(activeProject);var prefs=getSharedPreferences("mix",MODE_PRIVATE);for(int i=0;i<mixer.trackCount();i++){mixer.setGain(i,prefs.getFloat(gainKey(i),(float)activeProject.tracks.get(i).volume));mixer.setPan(i,(float)activeProject.tracks.get(i).pan);}mixer.setBpm(prefs.getInt("projectBpm:"+projectKey,mixer.getBpm()));bpmField.setText(String.valueOf(mixer.getBpm()));songTitle.setText(activeProject.title);if(persistent)prefs.edit().putString("sourceMode","project").putString("projectTree",folder.tree.toString()).putString("projectFile",filename).apply();renderMixer();loading=false;importButton.setEnabled(true);projectButton.setEnabled(true);status.setText(R.string.project_ready);waveform.invalidate();if(!activeProject.warnings.isEmpty())projectNotice=new android.app.AlertDialog.Builder(this).setTitle(R.string.project_limits_title).setMessage(getString(R.string.project_limits)+(activeProject.warnings.contains("mp3-cache")?"\n\n"+getString(R.string.mp3_cache_notice):"")).setPositiveButton(android.R.string.ok,null).show();});}catch(ProjectSpec.Processing unsupported){importFailure(R.string.project_processing);}catch(ProjectFolder.Missing missing){importFailure(R.string.project_missing);}catch(WavReader.Unsupported unsupported){importFailure(R.string.unsupported_wav);}catch(Mp3Decode.NoSpace storage){importFailure(R.string.audio_cache_full);}catch(OutOfMemoryError memory){importFailure(R.string.import_memory);}catch(Exception failure){importFailure(R.string.project_failed);}});
    }

    private void chooseWav(){
        Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("audio/*");
        picker.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);picker.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"audio/wav","audio/x-wav","audio/wave","audio/vnd.wave","audio/mpeg"});
        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try{startActivityForResult(picker,OPEN_WAV);}catch(android.content.ActivityNotFoundException unavailable){status.setText(R.string.no_picker);}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null)return;
        if(request==OPEN_PROJECT&&data.getData()!=null){Uri tree=data.getData();boolean durable=true;try{getContentResolver().takePersistableUriPermission(tree,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException unavailable){durable=false;}openProjectFolder(tree,null,durable);return;}
        if(request!=OPEN_WAV)return;
        LinkedHashSet<Uri> selected=new LinkedHashSet<>();if(data.getClipData()!=null)for(int i=0;i<data.getClipData().getItemCount();i++)selected.add(data.getClipData().getItemAt(i).getUri());else if(data.getData()!=null)selected.add(data.getData());
        importAudioUris(new ArrayList<>(selected));
    }
    void importAudioUris(List<Uri> selected){
        if(loading||selected.isEmpty())return;if(selected.size()>32){status.setText(R.string.too_many_files);return;}
        lastImportError=0;loading=true;if(projectNotice!=null)projectNotice.dismiss();importButton.setEnabled(false);projectButton.setEnabled(false);mixer.silence();if(audio!=null){audio.close();audio=null;}status.setText(R.string.loading_audio);
        final List<Uri> files=new ArrayList<>(selected);
        imports.execute(()->{
            try{
                boolean durable=true;for(Uri uri:files)try{if(!"content".equals(uri.getScheme())){durable=false;continue;}getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException unavailable){durable=false;}
                final boolean persistent=durable;
                WavImport.Result decoded=WavImport.read(getApplicationContext(),files,(done,total)->runOnUiThread(()->{if(!destroyed)status.setText(getString(R.string.loading_count,done,total));}));
                runOnUiThread(()->{if(destroyed){decoded.close();return;}activeProject=null;projectKey=null;currentUris=files;sourcePeaks=decoded.peaks;mixer.load(decoded.tracks);
                    var prefs=getSharedPreferences("mix",MODE_PRIVATE);for(int i=0;i<mixer.trackCount();i++)mixer.setGain(i,prefs.getFloat(gainKey(i),.7f));
                    if(persistent){JSONArray list=new JSONArray();for(Uri uri:files)list.put(uri.toString());prefs.edit().putString("wavSources",list.toString()).putString("sourceMode","wav").apply();}
                    songTitle.setText(getString(R.string.imported_project,mixer.trackCount()));renderMixer();loading=false;importButton.setEnabled(true);projectButton.setEnabled(true);status.setText(persistent?R.string.import_done:R.string.import_session_only);waveform.invalidate();if(decoded.temporary)projectNotice=new android.app.AlertDialog.Builder(this).setMessage(R.string.mp3_cache_notice).setPositiveButton(android.R.string.ok,null).show();
                });
            }catch(OutOfMemoryError memory){importFailure(R.string.import_memory);}catch(WavImport.NotSeekable unavailable){importFailure(R.string.not_seekable);}catch(WavReader.TooLarge large){importFailure(R.string.import_memory);}catch(WavReader.Unsupported unsupported){importFailure(R.string.unsupported_wav);}catch(Exception failure){importFailure(R.string.import_failed);}
        });
    }
    private void importFailure(int text){runOnUiThread(()->{if(!destroyed){loading=false;lastImportError=text;importButton.setEnabled(true);projectButton.setEnabled(true);status.setText(text);if(!isFinishing()){if(importErrorDialog!=null)importErrorDialog.dismiss();importErrorDialog=new android.app.AlertDialog.Builder(this).setTitle(R.string.import_error_title).setMessage(text).setPositiveButton(android.R.string.ok,null).show();}}});}
    private void restoreImport(){var prefs=getSharedPreferences("mix",MODE_PRIVATE);if(prefs.getString("sourceMode","").equals("project")){openProjectFolder(Uri.parse(prefs.getString("projectTree","")),prefs.getString("projectFile",""),true);return;}try{JSONArray saved=new JSONArray(getSharedPreferences("mix",MODE_PRIVATE).getString("wavSources","[]"));List<Uri> files=new ArrayList<>();for(int i=0;i<saved.length();i++)files.add(Uri.parse(saved.getString(i)));if(!files.isEmpty())importAudioUris(files);}catch(Exception ignored){status.setText(R.string.import_failed);}}
    @Override protected void onDestroy(){destroyed=true;imports.shutdownNow();if(importErrorDialog!=null)importErrorDialog.dismiss();if(projectNotice!=null)projectNotice.dismiss();if(mixerDialog!=null)mixerDialog.dismiss();mixer.closeImported();super.onDestroy();}

    private void setBpm(int value){mixer.setBpm(value);getSharedPreferences("mix",MODE_PRIVATE).edit().putInt(activeProject==null?"bpm":"projectBpm:"+projectKey,mixer.getBpm()).apply();}
    @Override protected void onResume(){super.onResume();handler.post(refresh);}
    @Override protected void onPause(){handler.removeCallbacks(refresh);if(audio!=null){audio.close();audio=null;}super.onPause();}
    private final class Waveform extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Waveform(){super(MainActivity.this);setBackground(background(panel));}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float w=getWidth(),h=getHeight();int[] colors={0xff345c7b,0xff65428e,0xff2c694a};int[] labels={R.string.intro,R.string.verse,R.string.chorus};if(activeProject!=null)drawRegions(canvas,w,h);if(!mixer.hasImported())for(int r=0;r<3;r++){p.setColor(colors[r]);canvas.drawRect(r*w/3,0,(r+1)*w/3,h,p);p.setColor(text);p.setTextSize(dp(11));canvas.drawText(getString(labels[r]),r*w/3+dp(8),dp(18),p);}p.setStrokeWidth(dp(1));p.setColor(0xff7caec7);for(int x=0;x<w;x+=dp(3)){float amp=sourcePeaks==null?(float)(.12+.55*Math.abs(Math.sin(x*.079)*Math.sin(x*.013)))*h*.3f:sourcePeaks[Math.min(sourcePeaks.length-1,(int)(x/w*sourcePeaks.length))]*h*.38f;canvas.drawLine(x,h*.62f-amp,x,h*.62f+amp,p);}p.setColor(0xffd9d6f7);if(!mixer.hasImported())for(int r=1;r<3;r++)canvas.drawLine(r*w/3,0,r*w/3,h,p);p.setColor(Color.WHITE);p.setStrokeWidth(dp(2));float x=(float)(mixer.position()/mixer.duration()*w);canvas.drawLine(x,0,x,h,p);}
        private boolean regionGesture;
        private void drawRegions(Canvas canvas,float w,float h){int index=0;for(ProjectSpec.Region r:activeProject.regions){float left=(float)(r.start/mixer.duration()*w),right=(float)(r.end/mixer.duration()*w);int color=0xff345c7b;try{if(!r.color.isEmpty())color=Color.parseColor(r.color);}catch(IllegalArgumentException ignored){}p.setColor(color);p.setAlpha(65);canvas.drawRect(left,0,right,h,p);p.setAlpha(255);p.setColor(index==mixer.pendingRegion()?0xffddbcff:color);canvas.drawRect(left,0,right,dp(26),p);p.setColor(0xffe4dff4);p.setStrokeWidth(dp(1));canvas.drawLine(left,0,left,h,p);canvas.drawLine(right,0,right,h,p);int save=canvas.save();canvas.clipRect(left+dp(3),0,right-dp(2),dp(26));p.setTextSize(dp(10));canvas.drawText(r.name,left+dp(4),dp(17),p);canvas.restoreToCount(save);index++;}}
        @Override public boolean onTouchEvent(android.view.MotionEvent e){
            if(e.getAction()==MotionEvent.ACTION_DOWN){regionGesture=activeProject!=null&&e.getY()<dp(26);if(regionGesture){double second=e.getX()/getWidth()*mixer.duration();for(int i=0;i<activeProject.regions.size();i++){ProjectSpec.Region r=activeProject.regions.get(i);if(second>=r.start&&second<r.end){mixer.selectRegion(i);break;}}getParent().requestDisallowInterceptTouchEvent(true);invalidate();return true;}}
            if(regionGesture){if(e.getAction()==MotionEvent.ACTION_UP){regionGesture=false;performClick();}else if(e.getAction()==MotionEvent.ACTION_CANCEL)regionGesture=false;return true;}
if(e.getAction()==android.view.MotionEvent.ACTION_DOWN||e.getAction()==android.view.MotionEvent.ACTION_MOVE){getParent().requestDisallowInterceptTouchEvent(true);mixer.seek(Math.max(0,Math.min(1,e.getX()/getWidth()))*mixer.duration());invalidate();return true;}if(e.getAction()==android.view.MotionEvent.ACTION_UP){performClick();return true;}return true;}
        @Override public boolean performClick(){super.performClick();return true;}
    }
}
