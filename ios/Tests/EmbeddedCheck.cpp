#include "embedded-engine.h"
#include <juce_events/juce_events.h>
#include <iostream>
#include "ProjectImportCheck.h"
#include <stdexcept>
using namespace juce;
static var obj(std::initializer_list<std::pair<const char*,var>> p){auto* o=new DynamicObject();for(auto& a:p)o->setProperty(a.first,a.second);return var(o);}
int main(int argc,char** argv){
    ScopedJuceInitialiser_GUI init;
    if(argc!=2)return 2;
    auto* engine=zlplay::createEngine();
    try{
        projectImportCheck(File(String(argv[1])));
        auto call=[&](var v){return zlplay::engineCommand(engine,v);};
        auto file = obj({{"token","fixture"},{"path",String(argv[1])}});
        auto probe=call(obj({{"op","probe"},{"files",Array<var>{file}}}));
        if(!probe.isArray()||probe.size()!=1)throw std::runtime_error("probe failed");
        auto item=obj({{"path",String(argv[1])},{"position",0},{"offset",0},{"length",2},{"rate",1}});
        auto track=obj({{"items",Array<var>{item}},{"gain",.25},{"pitch",0},{"effectId","track:0"},{"bus","VS"}});
        call(obj({{"op","load"},{"offline",true},{"sampleRate",44100},{"duration",2},{"tracks",Array<var>{track}}}));
        auto rendered=call(obj({{"op","renderTest"},{"frames",44100},{"channels",2}}));
        if(double(rendered["rms"])<=0||int(rendered["frames"])!=44100)throw std::runtime_error("no PCM");
        call(obj({{"op","seek"},{"position",.5}}));
        if(std::abs(double(call(obj({{"op","status"}}))["position"])-.5)>.001)throw std::runtime_error("seek failed");
        call(obj({{"op","mix"},{"mix",Array<var>{obj({{"gain",0},{"pitch",0}})}}}));
        rendered=call(obj({{"op","renderTest"},{"frames",44100},{"position",0},{"channels",2}}));
        if(double(rendered["rms"])>1e-8)throw std::runtime_error("mute failed");
        call(obj({{"op","stop"}}));
        if(double(call(obj({{"op","status"}}))["position"])!=0)throw std::runtime_error("stop failed");
        call(obj({{"op","events"}}));
        zlplay::destroyEngine(engine);
        std::cout<<"Project import and embedded engine: RPP, ZLPlay, setlist, paths, PCM, seek, gain and stop passed. No iOS runtime claimed.\n";
        return 0;
    }catch(const mobile::ImportError& e){zlplay::destroyEngine(engine);std::cerr<<"Import error kind="<<int(e.kind)<<" detail="<<e.detail<<"\n";return 1;}catch(const std::exception& e){zlplay::destroyEngine(engine);std::cerr<<e.what()<<'\n';return 1;}
}
