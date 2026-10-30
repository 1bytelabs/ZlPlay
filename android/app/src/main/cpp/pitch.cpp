#include <jni.h>
#include <array>
#include <vector>
#include "signalsmith-stretch.h"
struct Pitch {
 signalsmith::stretch::SignalsmithStretch<float> dsp;
 std::array<std::vector<float>,2> in,out;
 Pitch(int semitones){dsp.presetDefault(2,48000);dsp.setTransposeSemitones(semitones);for(auto &v:in)v.resize(8192);for(auto &v:out)v.resize(8192);}
};
extern "C" JNIEXPORT jlong JNICALL Java_com_onebytelabs_zlplay_audio_NativePitch_create(JNIEnv *env,jclass,jint semitones){try{return reinterpret_cast<jlong>(new Pitch(semitones));}catch(...){env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),"Pitch allocation failed");return 0;}}
extern "C" JNIEXPORT jint JNICALL Java_com_onebytelabs_zlplay_audio_NativePitch_latency(JNIEnv*,jclass,jlong handle){auto p=reinterpret_cast<Pitch*>(handle);return p->dsp.inputLatency()+p->dsp.outputLatency();}
extern "C" JNIEXPORT void JNICALL Java_com_onebytelabs_zlplay_audio_NativePitch_reset(JNIEnv*,jclass,jlong handle){reinterpret_cast<Pitch*>(handle)->dsp.reset();}
extern "C" JNIEXPORT void JNICALL Java_com_onebytelabs_zlplay_audio_NativePitch_process(JNIEnv *env,jclass,jlong handle,jfloatArray input,jfloatArray output,jint count){if(count<0||count>8192||env->GetArrayLength(input)<count*2||env->GetArrayLength(output)<count*2){env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"),"Invalid pitch block");return;}auto p=reinterpret_cast<Pitch*>(handle);jfloat *a=env->GetFloatArrayElements(input,nullptr);for(int i=0;i<count;i++){p->in[0][i]=a[2*i];p->in[1][i]=a[2*i+1];}env->ReleaseFloatArrayElements(input,a,JNI_ABORT);float* in[]={p->in[0].data(),p->in[1].data()};float* out[]={p->out[0].data(),p->out[1].data()};p->dsp.process(in,count,out,count);jfloat *b=env->GetFloatArrayElements(output,nullptr);for(int i=0;i<count;i++){b[2*i]=p->out[0][i];b[2*i+1]=p->out[1][i];}env->ReleaseFloatArrayElements(output,b,0);}
extern "C" JNIEXPORT void JNICALL Java_com_onebytelabs_zlplay_audio_NativePitch_destroy(JNIEnv*,jclass,jlong handle){delete reinterpret_cast<Pitch*>(handle);}
