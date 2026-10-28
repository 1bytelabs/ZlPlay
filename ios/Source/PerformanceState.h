// SPDX-License-Identifier: AGPL-3.0-only
#pragma once
#include <juce_core/juce_core.h>
#include <array>
#include <cmath>
namespace performance {
class TapTempo {
public:
    // Matches the Mobile six-tap average and two-second restart window.
    int press(double milliseconds) {
        if(!std::isfinite(milliseconds))return 0;
        if(count>0&&milliseconds<=times[count-1])return 0;
        if(count>0&&milliseconds-times[count-1]>2000)count=0;
        if(count==int(times.size())){for(int i=1;i<count;++i)times[i-1]=times[i];--count;}
        times[count++]=milliseconds;
        if(count<2)return 0;
        return juce::jlimit(20,400,int(std::round(60000.*(count-1)/(milliseconds-times[0]))));
    }
private:
    std::array<double,6> times{};int count=0;
};
inline double effectiveGain(double gain,bool muted,bool solo,bool anySolo){return muted||(anySolo&&!solo)?0.:gain;}
}
