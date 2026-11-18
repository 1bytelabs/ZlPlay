package com.onebytelabs.zlplay;

import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;

/** Consistent touch select and dropdown rows for all native editing screens. */
final class StyledSpinner extends Spinner {
    private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
    StyledSpinner(Context context){
        super(context,Spinner.MODE_DROPDOWN);
        setMinimumHeight(dp(44));setPadding(dp(12),0,dp(30),0);
        setBackground(surface(0xff172333,0xff35465e,8));
        setPopupBackgroundDrawable(surface(0xff142030,0xff3a4960,10));
        setDropDownVerticalOffset(dp(4));setDropDownWidth(ViewGroup.LayoutParams.MATCH_PARENT);
    }
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private GradientDrawable surface(int fill,int border,int radius){GradientDrawable drawable=new GradientDrawable();drawable.setColor(fill);drawable.setCornerRadius(dp(radius));drawable.setStroke(dp(1),border);return drawable;}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(dp(1.5f));ink.setStrokeCap(Paint.Cap.ROUND);ink.setStrokeJoin(Paint.Join.ROUND);ink.setColor(isEnabled()?0xffbdcbe0:0xff687b96);
        float x=getWidth()-dp(18),y=getHeight()/2f;Path arrow=new Path();arrow.moveTo(x-dp(3),y-dp(1.5f));arrow.lineTo(x,y+dp(1.5f));arrow.lineTo(x+dp(3),y-dp(1.5f));canvas.drawPath(arrow,ink);
    }
    @Override public void setAdapter(SpinnerAdapter delegate){
        if(delegate==null){super.setAdapter(null);return;}
        super.setAdapter(new Rows(delegate));
    }
    private final class Rows extends BaseAdapter implements SpinnerAdapter {
        private final SpinnerAdapter delegate;
        Rows(SpinnerAdapter delegate){this.delegate=delegate;delegate.registerDataSetObserver(new DataSetObserver(){@Override public void onChanged(){notifyDataSetChanged();}@Override public void onInvalidated(){notifyDataSetInvalidated();}});}
        @Override public int getCount(){return delegate.getCount();}
        @Override public Object getItem(int position){return delegate.getItem(position);}
        @Override public long getItemId(int position){return delegate.getItemId(position);}
        @Override public boolean hasStableIds(){return delegate.hasStableIds();}
        @Override public View getView(int position,View recycled,ViewGroup parent){
            TextView text=recycled instanceof TextView?(TextView)recycled:new TextView(getContext());
            text.setText(String.valueOf(getItem(position)));text.setTypeface(getResources().getFont(R.font.inter));text.setTextSize(13);text.setTextColor(0xffe7eef9);text.setGravity(Gravity.CENTER_VERTICAL);text.setSingleLine(true);text.setEllipsize(TextUtils.TruncateAt.END);text.setPadding(0,0,0,0);text.setMinimumHeight(dp(40));return text;
        }
        @Override public View getDropDownView(int position,View recycled,ViewGroup parent){
            Option text=recycled instanceof Option?(Option)recycled:new Option(getContext());
            text.chosen=position==getSelectedItemPosition();text.setText(String.valueOf(getItem(position)));text.setTypeface(getResources().getFont(R.font.inter));text.setTextSize(14);text.setTextColor(text.chosen?0xffe4d3ff:0xffd4dfef);text.setGravity(Gravity.CENTER_VERTICAL);text.setSingleLine(true);text.setEllipsize(TextUtils.TruncateAt.END);text.setPadding(dp(14),0,dp(36),0);text.setBackgroundColor(text.chosen?0xff302343:0xff142030);text.setLayoutParams(new AbsListView.LayoutParams(-1,dp(48)));return text;
        }
    }
    private final class Option extends TextView {
        boolean chosen;final Paint mark=new Paint(Paint.ANTI_ALIAS_FLAG);
        Option(Context context){super(context);}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);if(!chosen)return;mark.setColor(0xffba91ff);mark.setStyle(Paint.Style.STROKE);mark.setStrokeWidth(dp(2));mark.setStrokeCap(Paint.Cap.ROUND);mark.setStrokeJoin(Paint.Join.ROUND);float x=getWidth()-dp(22),y=getHeight()/2f;Path check=new Path();check.moveTo(x-dp(4),y);check.lineTo(x-dp(1),y+dp(3));check.lineTo(x+dp(5),y-dp(4));canvas.drawPath(check,mark);}
    }
}
