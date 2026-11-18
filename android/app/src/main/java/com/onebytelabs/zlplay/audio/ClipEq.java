package com.onebytelabs.zlplay.audio;

import java.util.Arrays;

/** Stereo biquad cascade with the same band types and parameter bounds as desktop. */
public final class ClipEq {
    private final double[][] coefficients;
    private final double[] state;
    private long next = -1;

    ClipEq(double[][] coefficients) {
        this.coefficients = coefficients;
        state = new double[coefficients.length * 4];
    }

    public static double[] coefficients(String type, double frequency, double gain, double quality) {
        double rate = DemoMixer.RATE;
        double angle = 2 * Math.PI * Math.max(20, Math.min(rate * .45, frequency)) / rate;
        double cosine = Math.cos(angle), alpha = Math.sin(angle) / (2 * Math.max(.1, Math.min(10, quality)));
        double a = Math.pow(10, Math.max(-18, Math.min(18, gain)) / 40);
        double root = 2 * Math.sqrt(a) * alpha, plus = a + 1, minus = a - 1;
        double b0, b1, b2, a0, a1, a2;
        switch (type) {
            case "highpass":
            case "lowpass":
                boolean high = type.equals("highpass");
                b0 = b2 = (1 + (high ? cosine : -cosine)) / 2;
                b1 = high ? -2 * b0 : 2 * b0;
                a0 = 1 + alpha; a1 = -2 * cosine; a2 = 1 - alpha;
                break;
            case "low":
                b0 = a * (plus - minus * cosine + root); b1 = 2 * a * (minus - plus * cosine); b2 = a * (plus - minus * cosine - root);
                a0 = plus + minus * cosine + root; a1 = -2 * (minus + plus * cosine); a2 = plus + minus * cosine - root;
                break;
            case "high":
                b0 = a * (plus + minus * cosine + root); b1 = -2 * a * (minus + plus * cosine); b2 = a * (plus + minus * cosine - root);
                a0 = plus - minus * cosine + root; a1 = 2 * (minus - plus * cosine); a2 = plus - minus * cosine - root;
                break;
            default:
                b0 = 1 + alpha * a; b1 = -2 * cosine; b2 = 1 - alpha * a;
                a0 = 1 + alpha / a; a1 = -2 * cosine; a2 = 1 - alpha / a;
        }
        return new double[]{b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0};
    }

    void reset(){Arrays.fill(state,0);next=-1;}
    double sample(double input,int channel){
        for(int i=0;i<coefficients.length;i++){
            double[] c=coefficients[i];int index=i*4+channel*2;
            double output=c[0]*input+state[index];
            state[index]=c[1]*input-c[3]*output+state[index+1];
            state[index+1]=c[2]*input-c[4]*output;input=output;
        }
        return input;
    }
    void process(float[] stereo, long frame, int count) {
        if (frame != next) Arrays.fill(state, 0);
        next = frame + count;
        for (int i = 0; i < coefficients.length; i++) {
            double[] c = coefficients[i];
            int base = i * 4;
            for (int frameIndex = 0; frameIndex < count; frameIndex++) for (int channel = 0; channel < 2; channel++) {
                int sample = frameIndex * 2 + channel;
                int index = base + channel * 2;
                double input = stereo[sample], output = c[0] * input + state[index];
                state[index] = c[1] * input - c[3] * output + state[index + 1];
                state[index + 1] = c[2] * input - c[4] * output;
                stereo[sample] = (float) output;
            }
        }
    }
}
