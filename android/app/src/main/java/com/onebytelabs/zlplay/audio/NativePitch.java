package com.onebytelabs.zlplay.audio;
/** Signalsmith runs on the pitch-prefetch worker, never on the AudioTrack render callback. */
public final class NativePitch {
 static {System.loadLibrary("zlplay_pitch");}
 private NativePitch(){}
 public static native long create(int semitones);
 public static native int latency(long handle);
 public static native void reset(long handle);
 public static native void process(long handle,float[] input,float[] output,int frames);
 public static native void destroy(long handle);
}
