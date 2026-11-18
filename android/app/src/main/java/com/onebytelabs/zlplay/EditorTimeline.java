package com.onebytelabs.zlplay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;
import com.onebytelabs.zlplay.audio.SourceWaveform;
import java.util.Collections;
import java.util.List;

/** Scrolls and zooms the same time axis for the ruler, clips and playhead. */
final class EditorTimeline extends View {
    static final class Lane {
        final String name;
        final int color;
        final float height;
        final List<Item> items;

        Lane(String name, int color, float height, List<Item> items) {
            this.name = name;
            this.color = color;
            this.height = height;
            this.items = items;
        }
    }

    static final class Item {
        final int track, index, color;
        final String text;
        final double start, end, offset;
        final SourceWaveform wave;
        final boolean editable, muted, showTitle;

        Item(int track, int index, int color, String text, double start, double end, double offset,
             SourceWaveform wave, boolean editable, boolean muted) {
            this(track,index,color,text,start,end,offset,wave,editable,muted,true);
        }

        Item(int track,int index,int color,String text,double start,double end,double offset,
             SourceWaveform wave,boolean editable,boolean muted,boolean showTitle){
            this.track = track;
            this.index = index;
            this.color = color;
            this.text = text;
            this.start = start;
            this.end = end;
            this.offset = offset;
            this.wave = wave;
            this.editable = editable;
            this.muted = muted;
            this.showTitle = showTitle;
        }

        boolean matches(Item other) {
            return other != null && track == other.track && index == other.index;
        }
    }

    interface Listener {
        void select(Item item);
        void open(Item item);
        void seek(double seconds);
        void change(Item item, double start, double end, double offset);
    }

    private enum Gesture { NONE, RULER, PAN, VERTICAL, MOVE, LEFT, RIGHT }
    private static final int TEXT = 0xffedf3fd, MUTED_TEXT = 0xffa4b5cd, GRID = 0xff253247;
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint type = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path cursor = new Path();
    private final float density, labels, ruler, slop;
    private final Listener listener;
    private final ScaleGestureDetector pinch;
    private List<Lane> lanes = Collections.emptyList();
    private Item selected, touched;
    private Gesture gesture = Gesture.NONE;
    private float downX, downY, scrollAtDown, vertical;
    private double duration = 1, beat = .5, zoom = 1, left, leftAtDown, position, newStart, newEnd, newOffset;
    private int beatsPerBar = 4;
    private boolean scaled, snap;
    private long lastTap;
    private Item lastTapped;

    EditorTimeline(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        density = getResources().getDisplayMetrics().density;
        labels = dp(getResources().getConfiguration().smallestScreenWidthDp >= 600 ? 158 : 112);
        ruler = dp(34);
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
        type.setTypeface(getResources().getFont(R.font.inter));
        setFocusable(true);
        setContentDescription(context.getString(R.string.timeline));
        pinch = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScaleBegin(ScaleGestureDetector detector) {
                scaled = true;
                gesture = Gesture.NONE;
                return true;
            }
            @Override public boolean onScale(ScaleGestureDetector detector) {
                zoomBy(detector.getScaleFactor(), detector.getFocusX());
                return true;
            }
        });
        pinch.setQuickScaleEnabled(false);
    }

    void show(List<Lane> lanes, Item selected, double duration, double bpm, int numerator, int denominator) {
        this.lanes = lanes;
        this.selected = selected;
        this.duration = Math.max(.1, duration);
        beat = 60 / Math.max(20, bpm) * 4 / Math.max(1, denominator);
        beatsPerBar = Math.max(1, numerator);
        boundViewport();
        invalidate();
    }

    void setPosition(double seconds, boolean follow) {
        boolean changed=position!=seconds;
        position = seconds;
        if (follow && gesture == Gesture.NONE && !pinch.isInProgress() && (seconds < left || seconds > left + span())) {
            left = seconds - span() * .2;
            boundViewport();
            changed=true;
        }
        if(changed)invalidate();
    }

    void setSnap(boolean enabled) { snap = enabled; }
    void zoomBy(double factor) {
        float x = x(position);
        zoomBy(factor, x < labels || x > getWidth() ? (labels + getWidth()) / 2 : x);
    }
    void fit() { zoom = 1; left = 0; invalidate(); }

    private void zoomBy(double factor, float anchor) {
        double time = time(anchor);
        zoom = clamp(zoom * factor, 1, 64);
        left = time - (anchor - labels) / pixelsPerSecond();
        boundViewport();
        invalidate();
    }

    private float dp(float size) { return size * density; }
    private double span() { return duration / zoom; }
    private double pixelsPerSecond() { return Math.max(1, getWidth() - labels) / span(); }
    private float x(double seconds) { return labels + (float) ((seconds - left) * pixelsPerSecond()); }
    private double time(float x) { return left + (x - labels) / pixelsPerSecond(); }
    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
    private void boundViewport() {
        left = clamp(left, 0, duration - span());
        float height = 0;
        for (Lane lane : lanes) height += dp(lane.height);
        vertical = (float) clamp(vertical, 0, Math.max(0, height - Math.max(0, getHeight() - ruler)));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(0xff101824);
        float y = ruler - vertical;
        for (int i = 0; i < lanes.size(); i++) {
            Lane lane = lanes.get(i);
            float bottom = y + dp(lane.height);
            if (bottom > ruler && y < getHeight()) drawLane(canvas, lane, y, bottom, i);
            y = bottom;
        }
        drawGrid(canvas);
        y = ruler - vertical;
        for (Lane lane : lanes) {
            float bottom = y + dp(lane.height);
            if (bottom > ruler && y < getHeight()) {
                int saved = canvas.save();
                canvas.clipRect(labels, ruler, getWidth(), getHeight());
                for (Item item : lane.items) if (!item.matches(selected)) drawItem(canvas, item, y, bottom);
                for (Item item : lane.items) if (item.matches(selected)) drawItem(canvas, item, y, bottom);
                canvas.restoreToCount(saved);
            }
            y = bottom;
        }
        float playhead = x(position);
        if (playhead >= labels && playhead <= getWidth()) {
            ink.setColor(0xffe8f6ff);
            ink.setStrokeWidth(dp(1.25f));
            canvas.drawLine(playhead, dp(24), playhead, getHeight(), ink);
            cursor.reset();
            cursor.moveTo(playhead - dp(5), dp(22));
            cursor.lineTo(playhead + dp(5), dp(22));
            cursor.lineTo(playhead, dp(29));
            cursor.close();
            canvas.drawPath(cursor, ink);
        }
    }

    private void drawLane(Canvas canvas, Lane lane, float top, float bottom, int index) {
        int saved = canvas.save();
        canvas.clipRect(0, ruler, getWidth(), getHeight());
        ink.setColor(index % 2 == 0 ? 0xff111b29 : 0xff14202e);
        canvas.drawRect(0, top, getWidth(), bottom, ink);
        ink.setColor(0xff182435);
        canvas.drawRect(0, top, labels, bottom, ink);
        ink.setColor(lane.color);
        canvas.drawRoundRect(dp(8), top + dp(12), dp(11), bottom - dp(12), dp(2), dp(2), ink);
        text(canvas, lane.name, dp(19), (top + bottom) / 2 + dp(4), labels - dp(28), TEXT, 12);
        ink.setColor(GRID);
        ink.setStrokeWidth(dp(1));
        canvas.drawLine(0, bottom, getWidth(), bottom, ink);
        canvas.restoreToCount(saved);
    }

    private void drawGrid(Canvas canvas) {
        ink.setColor(0xff0c1320);
        canvas.drawRect(0, 0, getWidth(), ruler, ink);
        double bar = beat * beatsPerBar;
        double pps=pixelsPerSecond();
        int labelStride=Math.max(1,(int)Math.ceil(dp(67)/(bar*pps)));
        float end=getWidth();
        int saved=canvas.save();
        canvas.clipRect(labels,0,end,getHeight());
        double step=beat;
        while(step*pps<dp(10))step*=2;
        for(double at=Math.max(0,Math.floor(left/step)*step);at<=left+span();at+=step){
            long tick=Math.round(at/beat);
            boolean measure=tick%beatsPerBar==0;
            float px=x(at);
            ink.setColor(measure?0x88748da8:0x444f718c);
            ink.setStrokeWidth(dp(measure?1:.65f));
            canvas.drawLine(px,measure?dp(22):ruler,px,getHeight(),ink);
        }
        for(double at=Math.max(0,Math.floor(left/(bar*labelStride))*bar*labelStride);at<=left+span();at+=bar*labelStride){
            float px=x(at);
            text(canvas,String.valueOf(1+Math.round(at/bar)),px+dp(5),dp(15),Math.min(dp(58),end-px),MUTED_TEXT,11);
        }
        canvas.restoreToCount(saved);
        ink.setColor(GRID);
        canvas.drawLine(labels, 0, labels, getHeight(), ink);
        canvas.drawLine(0, ruler, end, ruler, ink);
        text(canvas, getContext().getString(R.string.project_editor_bars), dp(12), dp(20), labels - dp(22), MUTED_TEXT, 11);
    }

    private void drawItem(Canvas canvas, Item item, float top, float bottom) {
        boolean active = item.matches(selected);
        boolean moving = item.matches(touched) && (gesture == Gesture.MOVE || gesture == Gesture.LEFT || gesture == Gesture.RIGHT);
        double start = moving ? newStart : item.start, end = moving ? newEnd : item.end;
        double offset = moving ? newOffset : item.offset;
        float leftEdge = x(start), rightEdge = Math.max(leftEdge + dp(6), x(end));
        if (rightEdge < labels || leftEdge > getWidth()) return;
        rect.set(leftEdge + dp(1), top + dp(4), rightEdge - dp(1), bottom - dp(4));
        int shade = item.muted ? 0xff303745 : item.color;
        ink.setColor(shade);
        ink.setAlpha(item.wave==null?210:170);
        canvas.drawRoundRect(rect, dp(5), dp(5), ink);
        if (item.wave != null) {
            int saved = canvas.save();
            canvas.clipRect(rect);
            boolean label=item.showTitle&&!item.text.isEmpty();
            float mid = rect.centerY() + (label ? dp(6) : 0);
            float half = rect.height() * (label ? .32f : .44f);
            ink.setColor(item.muted ? 0xff8993a3 : 0xffc1deff);
            ink.setAlpha(item.muted ? 120 : 200);
            float tick = dp(2.4f);
            ink.setStrokeWidth(dp(1.2f));
            for (float bar = Math.max(labels, rect.left + dp(2)); bar < Math.min(getWidth(), rect.right); bar += tick) {
                double from = offset + time(bar) - start, to = offset + time(bar + tick) - start;
                float peak = Math.min(1, item.wave.peak(from, to) * 1.35f) * half;
                if (peak > 0) canvas.drawLine(bar, mid - peak, bar, mid + peak, ink);
            }
            canvas.restoreToCount(saved);
        }
        float nameX = Math.max(rect.left + dp(active ? 14 : 7), labels + dp(5));
        if(item.showTitle)text(canvas, item.text, nameX, rect.top + dp(15), rect.right - nameX - dp(active ? 14 : 5), TEXT, 11);
        if (active) {
            ink.setColor(0xfff1eaff);
            ink.setStrokeWidth(dp(1.5f));
            ink.setStyle(Paint.Style.STROKE);
            canvas.drawRoundRect(rect, dp(5), dp(5), ink);
            ink.setStyle(Paint.Style.FILL);
            if(item.track==-3)return;
            float middle = rect.centerY(), handle = dp(3.5f);
            canvas.drawRoundRect(rect.left + dp(2), middle - dp(10), rect.left + dp(2) + handle, middle + dp(10), handle, handle, ink);
            canvas.drawRoundRect(rect.right - dp(2) - handle, middle - dp(10), rect.right - dp(2), middle + dp(10), handle, handle, ink);
        }
    }

    private void text(Canvas canvas, String text, float x, float y, float width, int color, int size) {
        if (text.isEmpty() || width < dp(8)) return;
        type.setColor(color);
        type.setTextSize(dp(size));
        CharSequence clipped = TextUtils.ellipsize(text, type, width, TextUtils.TruncateAt.END);
        canvas.drawText(clipped, 0, clipped.length(), x, y, type);
    }

    private Item hit(float x, float y) {
        float top = ruler - vertical;
        for (Lane lane : lanes) {
            float bottom = top + dp(lane.height);
            if (y >= top && y < bottom) {
                if (x < labels) return null;
                for (int i = lane.items.size() - 1; i >= 0; i--) {
                    Item item = lane.items.get(i);
                    float start = x(item.start), end = Math.max(start + dp(6), x(item.end));
                    if (item.editable && x >= start && x <= end) return item;
                }
                return null;
            }
            top = bottom;
        }
        return null;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        pinch.onTouchEvent(event);
        float x = event.getX(), y = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                scaled = false;
                downX = x;
                downY = y;
                leftAtDown = left;
                scrollAtDown = vertical;
                touched = y > ruler ? hit(x, y) : null;
                boolean onCursor=Math.abs(x-x(position))<=dp(7)&&!hitsSelectedHandle(x);
                gesture = x >= labels && (y<=ruler||onCursor) ? Gesture.RULER : Gesture.NONE;
                if (gesture == Gesture.RULER) listener.seek(clamp(snapped(time(x)), 0, duration));
                return true;
            case MotionEvent.ACTION_MOVE:
                if (scaled || event.getPointerCount() > 1) return true;
                float dx = x - downX, dy = y - downY;
                if (gesture == Gesture.NONE && (Math.abs(dx) > slop || Math.abs(dy) > slop)) {
                    gesture = Math.abs(dy) > Math.abs(dx) ? Gesture.VERTICAL : Gesture.PAN;
                    if (touched != null && gesture == Gesture.PAN) beginItemGesture();
                }
                if (gesture == Gesture.RULER) listener.seek(clamp(snapped(time(x)), 0, duration));
                if (gesture == Gesture.PAN) left = leftAtDown - dx / pixelsPerSecond();
                if (gesture == Gesture.VERTICAL) vertical = scrollAtDown - dy;
                if (gesture == Gesture.MOVE || gesture == Gesture.LEFT || gesture == Gesture.RIGHT)
                    moveItem(dx / pixelsPerSecond());
                boundViewport();
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                if (!scaled && (gesture == Gesture.MOVE || gesture == Gesture.LEFT || gesture == Gesture.RIGHT))
                    listener.change(touched, newStart, newEnd, newOffset);
                else if (!scaled && gesture == Gesture.NONE) {
                    if (touched != null) {
                        long now = event.getEventTime();
                        if (touched.matches(lastTapped) && now - lastTap <= ViewConfiguration.getDoubleTapTimeout()) listener.open(touched);
                        else listener.select(touched);
                        lastTap = now;
                        lastTapped = touched;
                    } else if (x >= labels) listener.seek(clamp(snapped(time(x)), 0, duration));
                    performClick();
                }
                // fall through to clear the visual drag, including a cancelled pinch
            case MotionEvent.ACTION_CANCEL:
                gesture = Gesture.NONE;
                touched = null;
                invalidate();
                return true;
            default:
                return true;
        }
    }

    @Override public boolean performClick() { super.performClick(); return true; }

    private void beginItemGesture() {
        boolean handles = touched.track!=-3 && touched.matches(selected);
        float reach = Math.min(dp(19), (x(touched.end) - x(touched.start)) * .32f);
        gesture = handles && downX - x(touched.start) <= reach ? Gesture.LEFT :
            handles && x(touched.end) - downX <= reach ? Gesture.RIGHT : Gesture.MOVE;
        if (!handles) listener.select(touched);
        newStart = touched.start;
        newEnd = touched.end;
        newOffset = touched.offset;
    }

    private boolean hitsSelectedHandle(float x){
        if(touched==null||touched.track==-3||!touched.matches(selected))return false;
        float reach=Math.min(dp(19),(x(touched.end)-x(touched.start))*.32f);
        return x-x(touched.start)<=reach||x(touched.end)-x<=reach;
    }

    private void moveItem(double delta) {
        boolean audio = touched.track >= 0;
        double length = touched.end - touched.start;
        double minimum = Math.min(.02, length);
        double sourceEnd = touched.wave == null ? touched.offset + length : touched.wave.duration;
        if (gesture == Gesture.LEFT) {
            double lower = audio ? Math.max(0, touched.start - touched.offset) : 0;
            newStart = clamp(snapped(touched.start + delta), lower, touched.end - minimum);
            if (audio) newOffset = Math.max(0, touched.offset + newStart - touched.start);
        } else if (gesture == Gesture.RIGHT) {
            double upper = audio ? Math.min(duration, touched.start + sourceEnd - touched.offset) : duration;
            newEnd = clamp(snapped(touched.end + delta), touched.start + minimum, upper);
        } else {
            newStart = clamp(snapped(touched.start + delta), 0, duration - length);
            newEnd = newStart + length;
        }
    }

    private double snapped(double seconds) { return snap ? Math.round(seconds / beat) * beat : seconds; }
}
