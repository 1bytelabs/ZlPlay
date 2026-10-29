// SPDX-License-Identifier: AGPL-3.0-only
#pragma once
#include "embedded-engine.h"
#include <juce_audio_formats/juce_audio_formats.h>
#include <stdexcept>
inline void guidePreviewCheck(zlplay::EmbeddedEngine* engine){using namespace juce;
    auto object=[](std::initializer_list<std::pair<const char*,var>> fields){auto* data=new DynamicObject();for(auto& field:fields)data->setProperty(field.first,field.second);return var(data);};
    auto file=File::getSpecialLocation(File::tempDirectory).getChildFile("zlplay-guide-preview-"+Uuid().toString()+".wav");struct Cleanup{File file;~Cleanup(){file.deleteFile();}}cleanup{file};
    AudioBuffer<float> pcm(2,88200);pcm.clear();for(int i=44100;i<88200;++i)for(int channel=0;channel<2;++channel)pcm.setSample(channel,i,float(.25*std::sin((i-44100)*2*MathConstants<double>::pi*440/44100.)));
    WavAudioFormat wav;std::unique_ptr<AudioFormatWriter> writer(wav.createWriterFor(new FileOutputStream(file),44100,2,16,{},0));if(!writer||!writer->writeFromAudioSampleBuffer(pcm,0,pcm.getNumSamples()))throw std::runtime_error("guide fixture write failed");writer.reset();
    auto call=[&](var request){return zlplay::engineCommand(engine,request);};auto track=object({{"items",Array<var>{object({{"path",file.getFullPathName()},{"position",0},{"offset",0},{"length",2},{"rate",1}})}},{"gain",1},{"pitch",0},{"effectId","guide-check"},{"bus","Guide"}});
    call(object({{"op","load"},{"offline",true},{"sampleRate",44100},{"duration",2},{"tracks",Array<var>{track}}}));
    auto render=[&]{return double(call(object({{"op","renderTest"},{"frames",22050},{"channels",2},{"position",0}}))["rms"]);};
    if(render()>1e-8)throw std::runtime_error("guide fixture must be silent before destination");
    call(object({{"op","mix"},{"guidePreview",object({{"track",0},{"start",0},{"end",.5},{"source",1}})}}));
    if(render()<.1)throw std::runtime_error("native destination guide preview did not render");
    call(object({{"op","mix"},{"guidePreview",var()}}));if(render()>1e-8)throw std::runtime_error("guide preview cancellation retained audio");
}
