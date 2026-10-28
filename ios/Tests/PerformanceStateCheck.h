// SPDX-License-Identifier: AGPL-3.0-only
#pragma once
#include "../Source/PerformanceState.h"
#include <stdexcept>
inline void performanceStateCheck(){
    auto require=[](bool value,const char* why){if(!value)throw std::runtime_error(why);};
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
