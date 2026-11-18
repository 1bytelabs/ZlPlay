package com.onebytelabs.zlplay.audio;

/** Bounded preview of the original source, independent of the clip's position or trim. */
public final class SourceWaveform {
    public final double duration;
    private final float[] peaks;

    public SourceWaveform(double duration, float[] peaks) {
        this.duration = duration;
        this.peaks = peaks;
    }

    public static SourceWaveform from(PcmTrack source) {
        double duration = source.duration();
        float[] peaks = new float[1024];
        for (int i = 0; i < peaks.length; i++) {
            peaks[i] = source.previewPeak(duration * i / peaks.length);
        }
        return new SourceWaveform(duration, peaks);
    }

    public float peak(double from, double to) {
        if (duration <= 0 || peaks.length == 0 || to < 0 || from >= duration) return 0;
        int first = Math.max(0, (int) (from / duration * peaks.length));
        int last = Math.min(peaks.length - 1, (int) (to / duration * peaks.length));
        float result = 0;
        for (int i = first; i <= last; i++) result = Math.max(result, peaks[i]);
        return result;
    }
}
