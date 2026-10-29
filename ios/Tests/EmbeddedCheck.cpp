#include "embedded-engine.h"
#include <juce_events/juce_events.h>
#include <iostream>
#include "ProjectImportCheck.h"
#include "PerformanceStateCheck.h"
#include "SoundBankCheck.h"
#include "RemoteProtocolCheck.h"
#include "GuidePreviewCheck.h"
#include <stdexcept>
using namespace juce;
static var obj(std::initializer_list<std::pair<const char*,var>> p){auto* o=new DynamicObject();for(auto& a:p)o->setProperty(a.first,a.second);return var(o);}
int main(int argc,char** argv){
    if(argc==2&&String(argv[1])=="--state-only"){performanceStateCheck();remoteProtocolCheck();std::cout<<"Performance state: Tap, mute/solo, repertoire filtering, guide lead-in, pinch, reorder, routing and remote protocol passed.\n";return 0;}
    if(argc==3&&String(argv[1])=="--sound-check"){soundBankCheck(File(String(argv[2])));std::cout<<"Sound bank: names, twelve notes, unchanged copies, duplicate rejection and private paths passed.\n";return 0;}
    if(argc==3&&String(argv[1])=="--remote-reject"){auto invite=mobile::RemoteInvitation::parse(File(String(argv[2])).loadFileAsString());try{mobile::remoteRequest(invite.hosts[0],"/v1/pair",invite.pin,{},obj({{"key",invite.key}}));}catch(const std::exception& e){if(std::string(e.what()).find("trust=-67818")==std::string::npos){std::cerr<<e.what()<<"\nUnexpected failure instead of expiry rejection\n";return 1;}std::cout<<"Native HTTPS: matching pin with expired certificate rejected by X.509 validity.\n";return 0;}std::cerr<<"Invalid certificate accepted\n";return 1;}
    if(argc==3&&String(argv[1])=="--remote-check"){
        try{auto invite=mobile::RemoteInvitation::parse(File(String(argv[2])).loadFileAsString());bool rejected=false;try{mobile::remoteRequest(invite.hosts[0],"/v1/pair",String::repeatedString("0",64),{},obj({{"key",invite.key},{"device","Synthetic iOS transport check"}}));}catch(const std::exception&){rejected=true;}if(!rejected)throw std::runtime_error("wrong TLS pin accepted");auto pair=mobile::remoteRequest(invite.hosts[0],"/v1/pair",invite.pin,{},obj({{"key",invite.key},{"device","Synthetic iOS transport check"}}));auto token=pair["token"].toString();auto state=pair["state"];if(token.length()!=64||int(state["protocol"])!=1)throw std::runtime_error("pair failed");for(auto type:StringArray{"play","pause","stop"}){state=mobile::remoteRequest(invite.hosts[0],"/v1/command",invite.pin,token,obj({{"id",Uuid().toString()},{"projectId",state["projectId"]},{"type",type}}));if(bool(state["playing"])!=(type=="play"))throw std::runtime_error("transport response mismatch");}auto polled=mobile::remoteRequest(invite.hosts[0],"/v1/state",invite.pin,token,{});if(polled["projectId"]!=state["projectId"])throw std::runtime_error("poll mismatch");mobile::remoteRequest(invite.hosts[0],"/v1/disconnect",invite.pin,token,obj({}));std::cout<<"Native HTTPS: wrong pin rejected, pair/play/pause/stop/poll/disconnect passed. Synthetic host only.\n";return 0;}catch(const std::exception& e){std::cerr<<e.what()<<'\n';return 1;}}
    ScopedJuceInitialiser_GUI init;
    if(argc!=2)return 2;
    auto* engine=zlplay::createEngine();
    try{
        performanceStateCheck();projectImportCheck(File(String(argv[1])));
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
        guidePreviewCheck(engine);
        zlplay::destroyEngine(engine);
        std::cout<<"Project import and embedded engine: RPP, ZLPlay, setlist, paths, PCM, seek, gain, stop and guide preview/cancellation passed. No iOS runtime claimed.\n";
        return 0;
    }catch(const mobile::ImportError& e){zlplay::destroyEngine(engine);std::cerr<<"Import error kind="<<int(e.kind)<<" detail="<<e.detail<<"\n";return 1;}catch(const std::exception& e){zlplay::destroyEngine(engine);std::cerr<<e.what()<<'\n';return 1;}
}
