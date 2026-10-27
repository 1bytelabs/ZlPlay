package com.onebytelabs.zlplay;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.widget.Button;

/** Native, accessible button with the approved performance UI skin and vector symbols. */
public final class PerformanceButton extends Button {
    public enum Style { CONTROL, PRIMARY, CYAN, CARD, GHOST }
    private Style style=Style.CONTROL;
    private String symbol;
    private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
    public PerformanceButton(Context context){super(context);setAllCaps(false);setMinHeight(0);setMinimumHeight(0);setMinWidth(0);setMinimumWidth(0);setPadding(d(10),0,d(10),0);setIncludeFontPadding(false);setElevation(0);skin();}
    private int d(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    public void style(Style value){style=value;skin();}
    public void symbol(String value){symbol=value;invalidate();}
    public void leadingSymbol(String name){Drawable icon=new Drawable(){public void draw(Canvas canvas){Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(getCurrentTextColor());p.setStrokeWidth(1.8f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setStyle(Paint.Style.STROKE);int save=canvas.save();canvas.translate(getBounds().left,getBounds().top);canvas.scale(getBounds().width()/24f,getBounds().height()/24f);drawSymbol(canvas,p,name);canvas.restoreToCount(save);}public void setAlpha(int a){}public void setColorFilter(ColorFilter f){}public int getOpacity(){return PixelFormat.TRANSLUCENT;}};icon.setBounds(0,0,d(17),d(17));setCompoundDrawables(icon,null,null,null);setCompoundDrawablePadding(d(7));}
    public void cover(Bitmap image,int side){Drawable art=new Drawable(){final Paint p=new Paint(3);public void draw(Canvas c){Rect b=getBounds();int save=c.save();Path clip=new Path();clip.addRoundRect(new RectF(b),d(5),d(5),Path.Direction.CW);c.clipPath(clip);p.setColor(0xff242238);c.drawRect(b,p);if(image!=null){float ratio=Math.max(b.width()/(float)image.getWidth(),b.height()/(float)image.getHeight());float w=image.getWidth()*ratio,h=image.getHeight()*ratio;c.drawBitmap(image,null,new RectF(b.centerX()-w/2,b.centerY()-h/2,b.centerX()+w/2,b.centerY()+h/2),p);}else{p.setColor(0xffa675ff);p.setStrokeWidth(d(2));p.setStrokeCap(Paint.Cap.ROUND);for(int i=0;i<5;i++){float x=b.left+b.width()*(i+1)/6f,half=b.height()*(i==2?.30f:i==1||i==3?.21f:.12f);c.drawLine(x,b.centerY()-half,x,b.centerY()+half,p);}}c.restoreToCount(save);}public void setAlpha(int alpha){}public void setColorFilter(ColorFilter f){}public int getOpacity(){return PixelFormat.TRANSLUCENT;}};art.setBounds(0,0,d(side),d(side));setCompoundDrawables(art,null,getCompoundDrawables()[2],null);setCompoundDrawablePadding(d(9));}
    public void keyBadge(String text){if(text==null||text.isEmpty()||text.equals("—")){setCompoundDrawables(getCompoundDrawables()[0],null,null,null);return;}Drawable badge=new Drawable(){final Paint p=new Paint(3);public void draw(Canvas c){Rect r=getBounds();p.setColor(0xff29223e);c.drawRoundRect(new RectF(r.left,r.centerY()-d(12),r.right,r.centerY()+d(12)),d(5),d(5),p);p.setTypeface(getTypeface());p.setTextAlign(Paint.Align.CENTER);p.setTextSize(d(11));p.setColor(0xffd9c6ff);c.drawText(text,r.centerX(),r.centerY()-(p.ascent()+p.descent())/2,p);}public void setAlpha(int alpha){}public void setColorFilter(ColorFilter f){}public int getOpacity(){return PixelFormat.TRANSLUCENT;}};badge.setBounds(0,0,d(text.length()>2?35:29),d(26));setCompoundDrawables(getCompoundDrawables()[0],null,badge,null);setCompoundDrawablePadding(d(7));}
    @Override public void setSelected(boolean value){boolean changed=value!=isSelected();super.setSelected(value);if(changed)skin();}
    private void skin(){int top=0xff192432,bottom=0xff101824,stroke=0xff2a384c;float width=.7f;
        if(style==Style.GHOST){top=bottom=Color.TRANSPARENT;stroke=Color.TRANSPARENT;}
        if(style==Style.CARD){top=isSelected()?0xff28213e:0xff121d29;bottom=isSelected()?0xff181c31:0xff0e1722;stroke=isSelected()?0xffa675ff:0xff243144;width=isSelected()?1.3f:.7f;}
        else if(style==Style.PRIMARY||isSelected()){top=0xffa65bff;bottom=0xff7337ec;stroke=0xffaf80ff;}
        if(style==Style.CYAN){top=0xff26c9d8;bottom=0xff16b5cf;stroke=0xff45e4e9;}
        GradientDrawable face=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{top,bottom});face.setCornerRadius(d(style==Style.PRIMARY?12:9));face.setStroke(Math.max(1,d(width)),stroke);setBackground(face);setTextColor(style==Style.CYAN?0xff07151d:0xffecf1fa);
        GradientDrawable mask=new GradientDrawable();mask.setColor(Color.WHITE);mask.setCornerRadius(d(10));setForeground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x55a675ff),null,mask));invalidate();
    }
    @Override protected void onDraw(Canvas canvas){if(symbol==null){super.onDraw(canvas);return;}ink.setColor(style==Style.CYAN?0xff07151d:0xffe9eef8);ink.setStrokeWidth(1.9f);ink.setStyle(Paint.Style.STROKE);ink.setStrokeCap(Paint.Cap.ROUND);ink.setStrokeJoin(Paint.Join.ROUND);int save=canvas.save();float size=d(22);canvas.translate((getWidth()-size)/2,(getHeight()-size)/2);canvas.scale(size/24,size/24);drawSymbol(canvas,ink,symbol.equals("play")&&isSelected()?"pause":symbol);canvas.restoreToCount(save);}
    public static void drawSymbol(Canvas c,Paint p,String name){Path path=new Path();
        switch(name){
        case "play":p.setStyle(Paint.Style.FILL);path.moveTo(8,4);path.lineTo(21,12);path.lineTo(8,20);path.close();c.drawPath(path,p);break;
        case "pause":p.setStyle(Paint.Style.FILL);c.drawRoundRect(6,4,10,20,1,1,p);c.drawRoundRect(15,4,19,20,1,1,p);break;
        case "stop":p.setStyle(Paint.Style.FILL);c.drawRoundRect(5,5,20,20,1.5f,1.5f,p);break;
        case "rewind":c.drawLine(5,5,5,20,p);p.setStyle(Paint.Style.FILL);path.moveTo(19,5);path.lineTo(7,12);path.lineTo(19,20);path.close();c.drawPath(path,p);break;
        case "loop":c.drawRoundRect(3,5,21,19,5,5,p);c.drawLine(16,2,21,5,p);c.drawLine(21,5,17,9,p);c.drawLine(8,22,3,19,p);c.drawLine(3,19,7,15,p);break;
        case "back":path.moveTo(15,3);path.lineTo(6,12);path.lineTo(15,21);c.drawPath(path,p);break;
        case "settings":for(int i=0;i<8;i++){double a=i*Math.PI/4;c.drawLine(12+(float)Math.cos(a)*8,12+(float)Math.sin(a)*8,12+(float)Math.cos(a)*10.5f,12+(float)Math.sin(a)*10.5f,p);}c.drawCircle(12,12,7.5f,p);c.drawCircle(12,12,2.6f,p);break;
        case "audio":path.moveTo(3,9);path.lineTo(7,9);path.lineTo(12,4);path.lineTo(12,20);path.lineTo(7,15);path.lineTo(3,15);path.close();c.drawPath(path,p);c.drawArc(10,6,21,18,-65,130,false,p);c.drawArc(8,2,27,22,-58,116,false,p);break;
        case "more":p.setStyle(Paint.Style.FILL);for(int i=0;i<3;i++)c.drawCircle(5+i*7,12,1.7f,p);break;
        case "pad":for(int i=0;i<5;i++){float height=i==2?17:i==1||i==3?11:6;float x=3+i*4.5f;c.drawLine(x,12-height/2,x,12+height/2,p);}break;
        case "mixer":for(int i=0;i<3;i++){float x=5+i*7;c.drawLine(x,3,x,21,p);float y=i==1?8:15;c.drawRoundRect(x-2,y-2,x+2,y+2,1,1,p);}break;
        case "note":path.moveTo(10,18);path.lineTo(10,5);path.lineTo(20,3);path.lineTo(20,16);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);c.drawOval(4,16,10,21,p);c.drawOval(14,14,20,19,p);break;
        case "list":for(int i=0;i<3;i++){float y=5+i*7;c.drawLine(8,y,22,y,p);c.drawCircle(3,y,.6f,p);}break;
        default:c.drawLine(7,9,12,14,p);c.drawLine(12,14,17,9,p);
        }p.setStyle(Paint.Style.STROKE);
    }
}
