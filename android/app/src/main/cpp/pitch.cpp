#include <jni.h>
#include <array>
#include <vector>
#if defined(__SSE__)
#include <xmmintrin.h>
#endif
#include "signalsmith-stretch.h"
// The JNI workers are not the Java output thread. Restore their floating-point
// environment after DSP; subnormal tails must not cause CPU spikes on silence.
struct ScopedNoDenormals {
#if defined(__SSE__)
 unsigned previous=_mm_getcsr();
 ScopedNoDenormals(){_mm_setcsr(previous|0x8040);}
 ~ScopedNoDenormals(){_mm_setcsr(previous);}
#elif defined(__aarch64__)
 unsigned long previous;
 ScopedNoDenormals(){asm volatile("mrs %0, fpcr":"=r"(previous));unsigned long value=previous|(1UL<<24);asm volatile("msr fpcr, %0"::"r"(value));}
 ~ScopedNoDenormals(){asm volatile("msr fpcr, %0"::"r"(previous));}
#elif defined(__arm__) && defined(__VFP_FP__)
 unsigned previous;
 ScopedNoDenormals(){asm volatile("vmrs %0, fpscr":"=r"(previous));unsigned value=previous|(1U<<24);asm volatile("vmsr fpscr, %0"::"r"(value));}
 ~ScopedNoDenormals(){asm volatile("vmsr fpscr, %0"::"r"(previous));}
#endif
};
struct Pitch {
 signalsmith::stretch::SignalsmithStretch<float> dsp;
 std::array<std::vector<float>,2> in,out;
 std::array<float,8192*2> transfer;
 Pitch(int semitones){dsp.presetDefault(2,48000);dsp.setTransposeSemitones(semitones);for(auto &v:in)v.resize(8192);for(auto &v:out)v.resize(8192);}
};
extern "C" JNIEXPORT jlong JNICALL Java_com_onebytelabs_zlplay_audio_NativePitch_create(JNIEnv *env,jclass,jint semitones){try{return reinterpret_cast<jlong>(new Pitch(semitones));}catch(...){env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),"Pitch allocation failed");return 0;}}
extern "C" JNIEXPORT jint JNICALL Java_com_onebytelabs_zlplay_audio_NativePitch_latency(JNIEnv*,jclass,jlong handle){auto p=reinterpret_cast<Pitch*>(handle);return p->dsp.inputLatency()+p->dsp.outputLatency();}
extern "C" JNIEXPORT void JNICALL Java_com_onebytelabs_zlplay_audio_NativePitch_reset(JNIEnv*,jclass,jlong handle){reinterpret_cast<Pitch*>(handle)->dsp.reset();}
extern "C" JNIEXPORT void JNICALL Java_com_onebytelabs_zlplay_audio_NativePitch_process(JNIEnv *env,jclass,jlong handle,jfloatArray input,jfloatArray output,jint count){if(count<0||count>8192||env->GetArrayLength(input)<count*2||env->GetArrayLength(output)<count*2){env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"),"Invalid pitch block");return;}ScopedNoDenormals noDenormals;auto p=reinterpret_cast<Pitch*>(handle);env->GetFloatArrayRegion(input,0,count*2,p->transfer.data());if(env->ExceptionCheck())return;for(int i=0;i<count;i++){p->in[0][i]=p->transfer[2*i];p->in[1][i]=p->transfer[2*i+1];}float* in[]={p->in[0].data(),p->in[1].data()};float* out[]={p->out[0].data(),p->out[1].data()};p->dsp.process(in,count,out,count);for(int i=0;i<count;i++){p->transfer[2*i]=p->out[0][i];p->transfer[2*i+1]=p->out[1][i];}env->SetFloatArrayRegion(output,0,count*2,p->transfer.data());}
extern "C" JNIEXPORT void JNICALL Java_com_onebytelabs_zlplay_audio_NativePitch_destroy(JNIEnv*,jclass,jlong handle){delete reinterpret_cast<Pitch*>(handle);}
