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
struct GuidePreview{bool enabled=false;double start=0,end=0,source=0;};
inline GuidePreview guidePreview(const juce::Array<juce::var>& regions,double boundary,double target){if(target<=0||boundary<=0)return {};double previous=0;for(auto& region:regions){double from=double(region["start"]);if(from<target&&from>previous)previous=from;}double source=juce::jmax(0.,target-8.,previous),lead=target-source;if(lead<=.03)return {};return {true,boundary-lead,boundary,source};}
struct ViewportState{double zoom,start;};
inline ViewportState pinchViewport(double duration,double zoom,double start,double factor,double oldFocus,double newFocus,double width){width=juce::jmax(1.,width);double anchor=start+oldFocus/width*duration/zoom;double next=juce::jlimit(1.,24.,zoom*factor);return {next,juce::jlimit(0.,juce::jmax(0.,duration-duration/next),anchor-newFocus/width*duration/next)};}

inline float meterPeak(const juce::var& values){float peak=0;if(values.isArray())for(int channel=0;channel<juce::jmin(2,values.size());++channel){double value=double(values[channel]);if(std::isfinite(value)&&value>peak)peak=float(value);}return peak;}
struct SpecialMeterLevels{float click=0,guide=0;};
inline SpecialMeterLevels specialMeterLevels(const juce::var& meters,const juce::Array<int>& roles){SpecialMeterLevels result{meterPeak(meters["click"]),0};for(int i=0;i<roles.size();++i){float value=meterPeak(meters[juce::Identifier("track:"+juce::String(i))]);if(roles[i]==2)result.click=juce::jmax(result.click,value);else if(roles[i]==1)result.guide=juce::jmax(result.guide,value);}return result;}

}
