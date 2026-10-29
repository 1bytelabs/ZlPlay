// SPDX-License-Identifier: AGPL-3.0-only
#pragma once
#include "../Source/PerformanceState.h"
#include <stdexcept>
inline void performanceStateCheck(){
    auto require=[](bool value,const char* why){if(!value)throw std::runtime_error(why);};
    juce::Array<juce::String> songs{"a","b","c","d"};int selected=performance::movePreservingSelection(songs,0,3,1);require(selected==0&&songs[selected]=="b"&&songs[3]=="a","reorder across selected song retains playing identity");selected=performance::movePreservingSelection(songs,0,2,selected);require(selected==2&&songs[selected]=="b","moving selected song retains identity");selected=performance::movePreservingSelection(songs,3,0,selected);require(selected==3&&songs[selected]=="b","moving preceding song updates selection");auto previous=songs;require(performance::movePreservingSelection(songs,-1,0,selected)==selected&&songs==previous,"invalid reorder preserves all songs");
    auto stereo=performance::outputChannels("stereo:2",8);require(stereo.left==2&&stereo.right==3,"stereo routing agrees with desktop audio-routing");auto mono=performance::outputChannels("mono:1",2);require(mono.left==1&&mono.right==-1,"mono routing agrees with desktop");auto unavailable=performance::outputChannels("stereo:6",2);require(unavailable.left==64&&unavailable.right==65,"unavailable route follows master after device change");auto master=performance::outputChannels("master",1,true);require(master.left==0&&master.right==-1,"mono hardware default");require(performance::outputChannels("none",2).left==-1&&performance::outputChannels("stereo:63",64).left==64,"disabled and out-of-bounds routes");
    performance::TapTempo taps;
    require(taps.press(1000)==0,"first tap must not replace BPM");
    require(taps.press(1500)==120&&taps.press(2000)==120,"steady tap BPM");
    require(taps.press(2410)==128,"average jitter across taps");
    require(taps.press(6000)==0,"long pause resets tap window");
    require(taps.press(6500)==120,"restart tap BPM");
    require(taps.press(6500)==0,"duplicate timestamp must not divide by zero");
    performance::TapTempo rolling;
    for(int i=0;i<6;++i)rolling.press(1000+i*500);
    require(rolling.press(3750)==133,"six-tap rolling window discards oldest timestamp");
    require(performance::effectiveGain(.6,true,true,true)==0,"mute wins over solo");
    require(performance::effectiveGain(.6,false,false,true)==0,"another solo silences track");
    require(performance::effectiveGain(.6,false,true,true)==.6,"solo preserves slider gain");
    require(performance::effectiveGain(.6,false,false,false)==.6,"clearing solo restores slider gain");
}
