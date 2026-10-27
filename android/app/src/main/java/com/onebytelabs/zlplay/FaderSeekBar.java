package com.onebytelabs.zlplay;
import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.ViewParent;
import android.widget.SeekBar;

/** Keep the entire fader gesture, including its first move, out of parent scroll views. */
public final class FaderSeekBar extends SeekBar {
    public FaderSeekBar(Context context){super(context);}
    private long lastUp;private float downX,downY;private boolean dragged,ownsGesture;
    private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
    private float dp(float x){return x*getResources().getDisplayMetrics().density;}
    @Override protected synchronized void onDraw(Canvas canvas){float left=getPaddingLeft(),right=getWidth()-getPaddingRight(),y=getHeight()/2f;ink.setShader(null);ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(dp(.5f));ink.setColor(0xff344258);for(int i=0;i<=10;i++){float x=left+(right-left)*i/10;canvas.drawLine(x,y-dp(8),x,y-dp(12),ink);canvas.drawLine(x,y+dp(8),x,y+dp(12),ink);}ink.setStrokeWidth(dp(4));ink.setStrokeCap(Paint.Cap.ROUND);ink.setColor(0xff344258);canvas.drawLine(left,y,right,y,ink);float x=left+(right-left)*getProgress()/Math.max(1f,getMax());ink.setShader(new LinearGradient(left,y,Math.max(left+1,x),y,0xff7637ed,0xffb18aff,Shader.TileMode.CLAMP));canvas.drawLine(left,y,x,y,ink);ink.setShader(new LinearGradient(x-dp(6),y,x+dp(6),y,0xffc6cbe0,0xfffafcff,Shader.TileMode.CLAMP));ink.setStyle(Paint.Style.FILL);canvas.drawRoundRect(x-dp(6),y-dp(7),x+dp(6),y+dp(7),dp(3),dp(3),ink);ink.setShader(null);}
    @Override public boolean onTouchEvent(MotionEvent event){ViewParent parent=getParent();int action=event.getActionMasked();if(action==MotionEvent.ACTION_DOWN){float left=getPaddingLeft(),right=getWidth()-getPaddingRight(),thumb=left+(right-left)*getProgress()/Math.max(1f,getMax());ownsGesture=isEnabled()&&Math.abs(event.getX()-thumb)<=dp(13)&&Math.abs(event.getY()-getHeight()/2f)<=dp(18);if(!ownsGesture){lastUp=0;return false;}downX=event.getX();downY=event.getY();dragged=false;if(parent!=null)parent.requestDisallowInterceptTouchEvent(true);}if(!ownsGesture)return false;if(action==MotionEvent.ACTION_MOVE&&Math.hypot(event.getX()-downX,event.getY()-downY)>dp(8))dragged=true;if(action==MotionEvent.ACTION_UP&&!dragged){long now=android.os.SystemClock.uptimeMillis();if(lastUp>0&&now-lastUp<300){setProgress(Math.min(getMax(),100));lastUp=0;ownsGesture=false;if(parent!=null)parent.requestDisallowInterceptTouchEvent(false);performClick();return true;}lastUp=now;}boolean handled=super.onTouchEvent(event);if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){ownsGesture=false;if(parent!=null)parent.requestDisallowInterceptTouchEvent(false);if(dragged||action==MotionEvent.ACTION_CANCEL)lastUp=0;}return handled;}
}
