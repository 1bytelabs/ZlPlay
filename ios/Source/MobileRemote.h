// SPDX-License-Identifier: AGPL-3.0-only
#pragma once
#include <juce_core/juce_core.h>
#include <deque>
#include <regex>
#include <cmath>
namespace mobile {
using namespace juce;
struct RemoteInvitation{
    String key,pin;StringArray hosts;
    static RemoteInvitation parse(String code,int64 now=Time::currentTimeMillis()){
        if(code.length()>3000||!code.startsWith("zlplay-remote:"))throw std::runtime_error("Invalid invitation");auto encoded=code.substring(14).replaceCharacter('-','+').replaceCharacter('_','/');while(encoded.length()%4)encoded+='=';MemoryOutputStream bytes;if(!Base64::convertFromBase64(bytes,encoded)||bytes.getDataSize()>2200)throw std::runtime_error("Invalid invitation");auto data=JSON::parse(bytes.toString());auto hex=[](String value){return value.length()==64&&value.containsOnly("0123456789abcdef");};int64 remaining=int64(data["expires"])-now;if(int(data["v"])!=1||remaining<=0||remaining>11*60000||!hex(data["key"].toString())||!hex(data["cert"].toString())||!data["hosts"].isArray()||data["hosts"].size()<1||data["hosts"].size()>4)throw std::runtime_error("Invalid invitation");RemoteInvitation result;result.key=data["key"].toString();result.pin=data["cert"].toString();for(auto& value:*data["hosts"].getArray()){auto host=value.toString();auto ip=host.upToFirstOccurrenceOf(":",false,false);auto port=host.fromFirstOccurrenceOf(":",false,false);auto octets=StringArray::fromTokens(ip,".","");if(octets.size()!=4||port.isEmpty()||port.length()>5||!port.containsOnly("0123456789")||port.getIntValue()<1||port.getIntValue()>65535)throw std::runtime_error("Invalid host");for(auto& octet:octets)if(octet.isEmpty()||octet.length()>3||!octet.containsOnly("0123456789")||octet.getIntValue()>255)throw std::runtime_error("Invalid host");int a=octets[0].getIntValue(),b=octets[1].getIntValue();if(!(a==10||a==127||(a==192&&b==168)||(a==169&&b==254)||(a==172&&b>=16&&b<=31)))throw std::runtime_error("Local hosts only");result.hosts.add(host);}return result;
    }
};
#if JUCE_IOS || JUCE_MAC
var remoteRequest(const String& host,const String& path,const String& pin,const String& token,const var& body);
#endif
#if JUCE_IOS
void scanRemoteQR(std::function<void(String)>);
#endif
class RemoteSession final : private Thread {
public:
    using Transport=std::function<var(const String&,const String&,const String&,const String&,const var&)>;
    RemoteSession(RemoteInvitation invitation,Transport network,std::function<void(var)> update,std::function<void()> failure):Thread("ZLPlay remote"),invite(std::move(invitation)),request(std::move(network)),onState(std::move(update)),onFailure(std::move(failure)){startThread();}
    ~RemoteSession()override{signalThreadShouldExit();notify();stopThread(15000);}
    void command(String type,var value={},int track=999){const ScopedLock lock(mutex);if(!connected)return;auto* object=new DynamicObject();object->setProperty("type",type);object->setProperty("id",Uuid().toString());object->setProperty("projectId",snapshot["projectId"]);if(!value.isVoid())object->setProperty("value",value);if(track!=999)object->setProperty("track",track);String key=type+":"+String(track);for(auto& item:queue)if(item.first==key){item.second=var(object);return;}if(queue.size()<32)queue.push_back({key,var(object)});}
    bool isConnected()const{return connected;}
private:
    RemoteInvitation invite;Transport request;std::function<void(var)> onState;std::function<void()> onFailure;CriticalSection mutex;std::deque<std::pair<String,var>> queue;var snapshot;std::atomic<bool> connected{false};String host,token;
    static void validate(const var& state){if(int(state["protocol"])!=1||state["projectId"].toString().isEmpty()||!state["tracks"].isArray()||state["tracks"].size()>256||!state["regions"].isArray()||state["regions"].size()>512||state["songs"].size()>256||state["peaks"].size()>4096||!std::isfinite(double(state["duration"]))||double(state["duration"])<0||double(state["duration"])>86400)throw std::runtime_error("Invalid state");}
    void publish(var state){validate(state);{const ScopedLock lock(mutex);snapshot=state.clone();}if(!threadShouldExit())onState(state);}
    void run()override{try{bool paired=false;for(auto& candidate:invite.hosts){if(threadShouldExit())return;try{auto* body=new DynamicObject();body->setProperty("key",invite.key);body->setProperty("device","ZLPlay iPhone/iPad");auto response=request(candidate,"/v1/pair",invite.pin,{},var(body));auto candidateToken=response["token"].toString();if(candidateToken.length()!=64||!candidateToken.containsOnly("0123456789abcdef"))throw std::runtime_error("Invalid token");validate(response["state"]);host=candidate;token=candidateToken;connected=true;publish(response["state"]);paired=true;break;}catch(const std::exception&){}}if(!paired)throw std::runtime_error("Pair failed");while(!threadShouldExit()){std::deque<std::pair<String,var>> pending;String project;{const ScopedLock lock(mutex);pending.swap(queue);project=snapshot["projectId"].toString();}for(auto& item:pending){if(threadShouldExit())break;{const ScopedLock lock(mutex);project=snapshot["projectId"].toString();}if(item.second["projectId"].toString()==project){try{publish(request(host,"/v1/command",invite.pin,token,item.second));}catch(const std::exception&){auto refreshed=request(host,"/v1/state",invite.pin,token,{});if(refreshed["projectId"].toString()==project)throw;publish(refreshed);}}}if(!threadShouldExit())publish(request(host,"/v1/state",invite.pin,token,{}));wait(200);}}catch(const std::exception&){if(!threadShouldExit())onFailure();}connected=false;if(token.isNotEmpty())try{request(host,"/v1/disconnect",invite.pin,token,var(new DynamicObject()));}catch(const std::exception&){} }
};
}
