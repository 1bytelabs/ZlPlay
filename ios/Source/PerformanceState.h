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
template<class T>int movePreservingSelection(juce::Array<T>& items,int from,int to,int selected){if(from<0||from>=items.size()||to<0||to>=items.size()||from==to)return selected;items.move(from,to);if(selected==from)return to;if(from<selected&&to>=selected)return selected-1;if(from>selected&&to<=selected)return selected+1;return selected;}
struct OutputPair{int left,right;};
inline OutputPair outputChannels(const juce::String& route,int count,bool master=false){count=juce::jlimit(1,64,count);OutputPair fallback=master?OutputPair{0,count>1?1:-1}:OutputPair{64,65};if(route=="none")return {-1,-1};bool mono=route.startsWith("mono:"),stereo=route.startsWith("stereo:");if(!mono&&!stereo)return fallback;auto number=route.fromFirstOccurrenceOf(":",false,false);if(number.isEmpty()||number.length()>2||!number.containsOnly("0123456789"))return fallback;int left=number.getIntValue();if(left>=count||(stereo&&left+1>=count))return fallback;return {left,mono?-1:left+1};}
inline double effectiveGain(double gain,bool muted,bool solo,bool anySolo){return muted||(anySolo&&!solo)?0.:gain;}
}
