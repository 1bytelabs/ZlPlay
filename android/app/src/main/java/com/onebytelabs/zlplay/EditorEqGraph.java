package com.onebytelabs.zlplay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;
import com.onebytelabs.zlplay.audio.ClipEq;
import java.util.function.IntConsumer;
import org.json.JSONArray;
import org.json.JSONObject;

/** Frequency response and draggable controls for the four saved EQ bands. */
final class EditorEqGraph extends View {
    private final JSONArray bands;
    private final IntConsumer changed;
    private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path curve=new Path();
    private final int[] colors={0xff24d6d9,0xffffbd50,0xffd782ff,0xff78e79e};
    private final float scale;
    private int selected=-1;
    private double highpass,lowpass,trim;

    EditorEqGraph(Context context,JSONArray bands,IntConsumer changed){
        super(context);
        this.bands=bands;
        this.changed=changed;
        scale=getResources().getDisplayMetrics().density;
        setContentDescription(context.getString(R.string.project_clip_eq));
        setFocusable(true);
    }
    void trim(double value){trim=value;invalidate();}
    void cuts(double hp,double lp){highpass=hp;lowpass=lp;invalidate();}
    private float left(){return 30*scale;}
    private float right(){return getWidth()-12*scale;}
    private float top(){return 12*scale;}
    private float bottom(){return getHeight()-26*scale;}
    private float x(double hz){return left()+(float)(Math.log(hz/20)/Math.log(1000))*(right()-left());}
    private float y(double db){return (float)(top()+(18-db)/36*(bottom()-top()));}

    @Override protected void onDraw(Canvas canvas){
        canvas.drawColor(0xff0d1724);
        ink.setTextSize(10*scale);
        for(int db=-12;db<=12;db+=6){
            ink.setColor(db==0?0xff64748b:0xff26374a);
            ink.setStrokeWidth(scale);
            canvas.drawLine(left(),y(db),right(),y(db),ink);
            ink.setColor(0xffa8bad2);
            canvas.drawText(String.valueOf(db),0,y(db)+3*scale,ink);
        }
        for(int hz:new int[]{20,100,1000,10000,20000}){
            ink.setColor(0xff26374a);
            canvas.drawLine(x(hz),top(),x(hz),bottom(),ink);
            ink.setColor(0xffa8bad2);
            String label=hz>=1000?hz/1000+"k":String.valueOf(hz);
            canvas.drawText(label,Math.min(x(hz),getWidth()-28*scale),getHeight()-5*scale,ink);
        }
        double[][] filters=new double[6][];
        for(int i=0;i<4;i++){
            JSONObject band=bands.optJSONObject(i);
            filters[i]=ClipEq.coefficients(band.optString("type"),band.optDouble("freq"),
                band.optDouble("gain"),band.optDouble("q",.707));
        }
        if(highpass>0)filters[4]=ClipEq.coefficients("highpass",highpass,0,.707);
        if(lowpass>0)filters[5]=ClipEq.coefficients("lowpass",lowpass,0,.707);
        curve.reset();
        for(int i=0;i<=220;i++){
            double hz=20*Math.pow(1000,i/220.0),w=2*Math.PI*hz/48000;
            double db=trim;
            for(double[] c:filters)if(c!=null){
                double nr=c[0]+c[1]*Math.cos(w)+c[2]*Math.cos(2*w);
                double ni=-c[1]*Math.sin(w)-c[2]*Math.sin(2*w);
                double dr=1+c[3]*Math.cos(w)+c[4]*Math.cos(2*w);
                double di=-c[3]*Math.sin(w)-c[4]*Math.sin(2*w);
                db+=10*Math.log10(Math.max(1e-12,(nr*nr+ni*ni)/(dr*dr+di*di)));
            }
            float px=x(hz),py=y(Math.max(-18,Math.min(18,db)));
            if(i==0)curve.moveTo(px,py);else curve.lineTo(px,py);
        }
        ink.setColor(0xffb386ff);ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(2*scale);
        canvas.drawPath(curve,ink);ink.setStyle(Paint.Style.FILL);
        for(int i=0;i<4;i++){
            JSONObject band=bands.optJSONObject(i);
            ink.setColor(colors[i]);
            canvas.drawCircle(x(band.optDouble("freq")),y(band.optDouble("gain")),6*scale,ink);
        }
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){
            double distance=26*scale;
            for(int i=0;i<4;i++){
                JSONObject band=bands.optJSONObject(i);
                double current=Math.hypot(event.getX()-x(band.optDouble("freq")),event.getY()-y(band.optDouble("gain")));
                if(current<distance){selected=i;distance=current;}
            }
            if(selected<0)return false;
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        if(selected>=0&&(event.getActionMasked()==MotionEvent.ACTION_MOVE||event.getActionMasked()==MotionEvent.ACTION_UP)){
            double ratio=Math.max(0,Math.min(1,(event.getX()-left())/(right()-left())));
            double db=Math.max(-18,Math.min(18,18-36*(event.getY()-top())/(bottom()-top())));
            try{
                bands.getJSONObject(selected).put("freq",Math.round(20*Math.pow(1000,ratio))).put("gain",Math.round(db*10)/10.0);
                changed.accept(selected);invalidate();
            }catch(Exception ignored){}
        }
        if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL){
            selected=-1;getParent().requestDisallowInterceptTouchEvent(false);performClick();
        }
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
