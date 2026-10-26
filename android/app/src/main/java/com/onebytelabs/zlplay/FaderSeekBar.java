package com.onebytelabs.zlplay;
import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewParent;
import android.widget.SeekBar;

/** Keep the entire fader gesture, including its first move, out of parent scroll views. */
public final class FaderSeekBar extends SeekBar {
    public FaderSeekBar(Context context){super(context);}
    @Override public boolean onTouchEvent(MotionEvent event){
        ViewParent parent=getParent();
        if(isEnabled()&&event.getActionMasked()==MotionEvent.ACTION_DOWN&&parent!=null)parent.requestDisallowInterceptTouchEvent(true);
        boolean handled=super.onTouchEvent(event);
        if((event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL)&&parent!=null)parent.requestDisallowInterceptTouchEvent(false);
        return handled;
    }
}
