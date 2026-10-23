// ZLPlay native audio worker. Disk reads and DSP run off the device callback.
#define NOMINMAX
#include <juce_audio_devices/juce_audio_devices.h>
#include <juce_audio_formats/juce_audio_formats.h>
#include "platform-support.h"
#include "signalsmith-stretch.h"
#include "vendor/libebur128/ebur128.h"
#include <iostream>
#include <thread>
#include <mutex>
#include <atomic>
#include <array>
#include <map>
#include <tuple>
#include <chrono>
#include <cmath>
#include <condition_variable>
using namespace juce;
static var object(std::initializer_list<std::pair<const char*, var>> properties) {
    auto* o = new DynamicObject(); for (auto& p:properties) o->setProperty(p.first,p.second); return var(o);
}
static double num(const var& o,const char* k,double fallback=0) { return o.hasProperty(k)?double(o[k]):fallback; }
static std::string str(const var& o,const char* k) { return o[k].toString().toStdString(); }
static std::mutex outputMutex;
static void send(const var& value) { std::lock_guard<std::mutex> l(outputMutex); std::cout << JSON::toString(value,true).toStdString() << std::endl; }
constexpr int blockSize=512, queueSize=32, maxOutputs=64, renderOutputs=maxOutputs+2;
struct Clip {
    std::unique_ptr<AudioFormatReader> reader;
    std::unique_ptr<AudioFormatReader> standby;
    double position=0,offset=0,length=0,rate=1;
    AudioBuffer<float> scratch{2,16384};
    int64 cachedStart=-1000000; int cachedCount=0;
};
#include "channel-dsp.h"
struct Track { std::vector<std::unique_ptr<Clip>> clips; double gain=1; float pan=0; double gainTarget=1,gainStep=0; int gainRemaining=0; double pitch=0; int group=0,left=maxOutputs,right=maxOutputs+1; std::string effectId,bus,special,profile; ChannelDsp fx; AudioBuffer<float> processed{2,blockSize}; bool preview=false; double previewStart=0,previewEnd=0,previewSource=0; };
struct Group {
    double pitch=0; std::string bus,profile; bool special=false; ChannelDsp fx,vsFx; int left=maxOutputs,right=maxOutputs+1; signalsmith::stretch::SignalsmithStretch<float> stretch;
    AudioBuffer<float> input{2,blockSize}, output{2,blockSize};
    std::vector<float> delayL,delayR; size_t delayIndex=0;
};
struct BusMeter { AudioBuffer<float> sum{2,blockSize};LevelMeter meter; };
struct Block { float audio[renderOutputs][blockSize]{}; double positions[blockSize]{}; int jumps[blockSize]{}; };
class Engine : public AudioIODeviceCallback {
public:
    AudioFormatManager formats;
    AudioDeviceManager devices;
    TimeSliceThread diskThread{"ZLPlay disk read ahead"};
    std::vector<std::unique_ptr<Track>> tracks;
    std::vector<std::unique_ptr<Group>> groups;
    std::map<std::string,var> effectSettings;std::map<std::string,std::unique_ptr<BusMeter>> busMeters;AudioBuffer<float> vsSum{2,blockSize};ChannelDsp masterFx,masterAuxFx,masterSoloFx,padFx,soloFx,guideFx;AudioBuffer<float> masterMeterScratch{2,65536};int launchWaitFrames=0;bool handoffArmed=false;LevelMeter vsMeter,masterMeter;
    std::map<std::string,std::array<float,2>> lastMeterLevels;
    std::mutex renderMutex, callbackMutex;
    std::vector<Block> queue=std::vector<Block>(queueSize);
    std::atomic<uint64_t> head{0},tail{0},underruns{0},readBytes{0};
    std::atomic<int> maxCallbackFrames{0}; double maxRenderMs=0;
    int callbackOffset=0,latency=0;
    std::atomic<bool> playing{false},quit{false};
    std::atomic<double> playhead{0};
    std::atomic<float> master{1};
    std::atomic<int> playedJumpSerial{0}; int renderedJumpSerial=0;
    bool jumpEnabled=false,inputJumpDone=false,audibleJumpDone=false;
    double jumpBoundary=0,jumpTarget=0;std::string jumpId;
    double sampleRate=44100,duration=0,inputPosition=0,audiblePosition=0;
    double loopStart=0,loopEnd=0;
    std::atomic<bool> loopEnabled{false}; bool configured=false,deviceOpen=false;
    bool click=false,accent=false; int meter=4,accentPosition=0;
    float clickGain=.5f,clickPan=0;
    int clickLeft=maxOutputs,clickRight=maxOutputs+1;
    std::atomic<int> masterLeft{0},masterRight{1};
    struct AuxFade {
        double level=0,step=0; int remaining=0;
        void target(bool enabled,double rate) { remaining=std::max(1,int(std::ceil(rate*1.5)));step=((enabled?1.0:0.0)-level)/remaining; }
        float next() { if(remaining>0){level=std::clamp(level+step,0.0,1.0);if(--remaining==0)level=step>0?1.0:0.0;}return float(level); }
    };
    struct PadVoice { double at=0; AuxFade fade; };
    std::array<PadVoice,12> padVoices;
    int activePad=-1;
    AuxFade soloFade;
    std::array<AudioBuffer<float>,12> padSamples;
    std::array<double,12> padRates{};
    std::array<String,12> padSources, padStamps;
    bool padEnabled=false,soloEnabled=false;
    float padGain=1,padPan=0;
    int padLeft=maxOutputs,padRight=maxOutputs+1,soloLeft=maxOutputs,soloRight=maxOutputs+1;
    double soloTime=0,soloBpm=120;
    float soloGain=1,soloPan=0;bool soloAccent=false;int soloMeter=4,soloAccentPosition=0;
    AudioBuffer<float> clickSample,accentSample;
    double accentSampleRate=44100,previewAt=-1;bool previewAccent=false,accentIsDefault=true;String normalSource,accentSource;
    double clickSampleRate=44100;
    std::vector<std::pair<double,double>> tempos{{0,120}};
    std::thread worker;
    bool timerPrecision=false;
    Engine() { timerPrecision=beginPlatformTimer();formats.registerBasicFormats(); diskThread.startThread(Thread::Priority::high); worker=std::thread([this]{run();}); }
    ~Engine() override { quit=true; if(worker.joinable())worker.join(); devices.removeAudioCallback(this); devices.closeAudioDevice(); tracks.clear();diskThread.stopThread(3000);endPlatformTimer(timerPrecision); }
    std::unique_ptr<AudioFormatReader> open(const String& filename) {
        auto r=std::unique_ptr<AudioFormatReader>(formats.createReaderFor(File(filename)));
        if(!r)throw std::runtime_error("Formato de áudio não suportado: "+filename.toStdString());
        if(r->numChannels>2)throw std::runtime_error("Use áudio mono ou estéreo.");
        return r;
    }
    var probe(const var& files) {
        Array<var> result;
        if(auto* a=files.getArray()) for(const auto& file:*a) {
            auto r=open(file["path"].toString());
            result.add(object({{"token",file["token"]},{"duration",double(r->lengthInSamples)/r->sampleRate},{"sampleRate",r->sampleRate},{"numberOfChannels",int(r->numChannels)},{"frames",int64(r->lengthInSamples)}}));
        }
        return result;
    }
    void readClick(const String& source,AudioBuffer<float>& wave,double& rate){auto reader=open(source);if(reader->lengthInSamples>reader->sampleRate*10||reader->lengthInSamples<1)throw std::runtime_error("Use sons de clique de até 10 segundos.");rate=reader->sampleRate;wave.setSize(2,int(reader->lengthInSamples)+1);wave.clear();if(!reader->read(&wave,0,int(reader->lengthInSamples),0,true,true))throw std::runtime_error("Não foi possível carregar o som do clique.");}
    void loadClickSample(){if(clickSample.getNumSamples()>0)return;readClick(File::getSpecialLocation(File::currentExecutableFile).getSiblingFile("Meuclick.wav").getFullPathName(),clickSample,clickSampleRate);}
    double clickLength(){return std::max((clickSample.getNumSamples()-1)/clickSampleRate,accentSample.getNumSamples()>0?(accentSample.getNumSamples()-1)/accentSampleRate:0.0);}
    float clickValue(double seconds,bool strong,int channel){const bool custom=strong&&accentSample.getNumSamples()>0;const auto& wave=custom?accentSample:clickSample;const double at=seconds*(custom?accentSampleRate:clickSampleRate)*(strong&&accentIsDefault?1.16:1.0);if(at<0||at>=wave.getNumSamples()-1)return 0;int j=int(at);float f=float(at-j);return (wave.getSample(channel,j)+(wave.getSample(channel,j+1)-wave.getSample(channel,j))*f)*(strong&&accentIsDefault?1.3f:1.0f);}
    void ensureDevice(double preferred) {
        if(deviceOpen)return;
        auto error=devices.initialiseWithDefaultDevices(0,2);
        if(error.isNotEmpty())throw std::runtime_error(error.toStdString());
        auto setup=devices.getAudioDeviceSetup();
        if(auto* current=devices.getCurrentAudioDevice()){
            if(current->getAvailableSampleRates().contains(preferred))setup.sampleRate=preferred;
            if(current->getAvailableBufferSizes().contains(512))setup.bufferSize=512;
            setup.useDefaultOutputChannels=false;setup.outputChannels.clear();setup.outputChannels.setRange(0,std::min(maxOutputs,current->getOutputChannelNames().size()),true);
        }
        error=devices.setAudioDeviceSetup(setup,true);if(error.isNotEmpty())throw std::runtime_error(error.toStdString());
        auto* device=devices.getCurrentAudioDevice();
        if(!device)throw std::runtime_error("Nenhuma saída de áudio disponível.");
        sampleRate=device->getCurrentSampleRate(); deviceOpen=true;
        devices.addAudioCallback(this);
    }
    var deviceInfo() {
        Array<var> types;
        for(auto* type:devices.getAvailableDeviceTypes()){
            type->scanForDevices();Array<var> names;
            for(auto& name:type->getDeviceNames(false))names.add(name);
            types.add(object({{"name",type->getTypeName()},{"devices",names}}));
        }
        Array<var> outputs,rates,buffers;
        auto* device=devices.getCurrentAudioDevice();
        if(device){for(auto& name:device->getOutputChannelNames())outputs.add(name);for(auto rate:device->getAvailableSampleRates())rates.add(rate);for(auto size:device->getAvailableBufferSizes())buffers.add(size);}
        return object({{"types",types},{"type",devices.getCurrentAudioDeviceType()},{"device",device?device->getName():String()},
            {"outputs",outputs},{"sampleRates",rates},{"bufferSizes",buffers},{"sampleRate",device?device->getCurrentSampleRate():sampleRate},
            {"bufferSize",device?device->getCurrentBufferSizeSamples():512},{"controlPanel",device&&device->hasControlPanel()}, {"maxOutputs",maxOutputs}});
    }
    var configureDevice(const var& request) {
        playing=false;
        std::lock_guard<std::mutex> lock(renderMutex);
        {std::lock_guard<std::mutex> c(callbackMutex);padEnabled=soloEnabled=false;padVoices={};activePad=-1;soloFade={};}
        const auto previousType=devices.getCurrentAudioDeviceType();const auto previousSetup=devices.getAudioDeviceSetup();
        devices.removeAudioCallback(this);devices.closeAudioDevice();deviceOpen=false;
        const auto typeName=request["type"].toString();
        bool found=false;for(auto* type:devices.getAvailableDeviceTypes())if(type->getTypeName()==typeName)found=true;
        String error;
        if(!found)error="Driver de áudio não disponível.";
        else {
            devices.setCurrentAudioDeviceType(typeName,true);
            auto setup=devices.getAudioDeviceSetup();setup.outputDeviceName=request["device"].toString();setup.inputDeviceName.clear();setup.inputChannels.clear();setup.useDefaultInputChannels=false;
            setup.sampleRate=num(request,"sampleRate",44100);setup.bufferSize=int(num(request,"bufferSize",512));setup.useDefaultOutputChannels=true;
            error=devices.setAudioDeviceSetup(setup,true);
            if(error.isEmpty()){
                if(auto* device=devices.getCurrentAudioDevice()){
                    setup=devices.getAudioDeviceSetup();setup.useDefaultOutputChannels=false;setup.outputChannels.clear();setup.outputChannels.setRange(0,std::min(maxOutputs,device->getOutputChannelNames().size()),true);
                    error=devices.setAudioDeviceSetup(setup,true);
                }else error="Dispositivo de áudio não disponível.";
            }
        }
        if(error.isNotEmpty()){
            if(previousType.isNotEmpty()){devices.setCurrentAudioDeviceType(previousType,true);devices.setAudioDeviceSetup(previousSetup,true);}
        }
        if(auto* device=devices.getCurrentAudioDevice()){sampleRate=device->getCurrentSampleRate();deviceOpen=true;devices.addAudioCallback(this);}
        if(configured&&deviceOpen){makeGroups();reset(playhead.load());}
        if(error.isNotEmpty())throw std::runtime_error(error.toStdString());
        if(!deviceOpen)throw std::runtime_error("Não foi possível abrir a saída de áudio.");
        return deviceInfo();
    }
    void makeGroups() {
        groups.clear();busMeters.clear(); std::map<std::tuple<double,int,int,std::string,std::string>,int> lookup;
        bool shifted=false; for(auto& t:tracks) if(std::abs(t->pitch)>.001)shifted=true;
        latency=0;
        for(auto& t:tracks) {
            auto key=std::make_tuple(t->pitch,t->left,t->right,t->bus,t->profile);auto found=lookup.find(key);
            if(found!=lookup.end()){t->group=found->second;continue;}
            auto g=std::make_unique<Group>();g->pitch=t->pitch;g->left=t->left;g->right=t->right;g->bus=t->bus;if(!g->bus.empty()&&!busMeters.count(g->bus))busMeters[g->bus]=std::make_unique<BusMeter>();g->profile=t->profile;g->special=!t->special.empty();g->fx.configure(effectSettings["group:"+g->bus],sampleRate);g->vsFx.configure(effectSettings["vs"],sampleRate);
            if(std::abs(g->pitch)>.001){g->stretch.configure(2,int(sampleRate*(g->profile=="voice"?.14:.12)),int(sampleRate*.02));g->stretch.setTransposeSemitones(g->pitch);if(g->profile=="voice"){g->stretch.setFormantFactor(1,true);g->stretch.setFormantBase(200/sampleRate);}latency=std::max(latency,g->stretch.inputLatency()+g->stretch.outputLatency());}
            t->group=int(groups.size());lookup[key]=t->group;groups.push_back(std::move(g));
        }
        if(lookup.find(std::make_tuple(0.0,clickLeft,clickRight,std::string(),std::string("click")))==lookup.end()){auto g=std::make_unique<Group>();g->left=clickLeft;g->right=clickRight;g->special=true;g->profile="click";g->fx.configure(effectSettings["click"],sampleRate);groups.push_back(std::move(g));}
        if(shifted) for(auto& g:groups)if(std::abs(g->pitch)<.001){g->delayL.assign(latency,0);g->delayR.assign(latency,0);}
    }
    var load(const var& request) {
        playing=false;
        std::lock_guard<std::mutex> lock(renderMutex);
        configured=false; tracks.clear();groups.clear();jumpEnabled=false;jumpId.clear();playedJumpSerial=0;renderedJumpSerial=0;
        double preferred=num(request,"sampleRate",44100);
        if(bool(request["offline"])) sampleRate=preferred; else ensureDevice(preferred);
        duration=num(request,"duration");
        loadClickSample();
        auto* array=request["tracks"].getArray();
        if(!array||array->isEmpty())throw std::runtime_error("Projeto sem tracks.");
        for(auto& value:*array) {
            auto t=std::make_unique<Track>();t->gain=num(value,"gain",1);t->gainTarget=t->gain;t->pan=num(value,"pan");t->pitch=num(value,"pitch");t->left=int(num(value,"outputLeft",maxOutputs));t->right=int(num(value,"outputRight",maxOutputs+1));
            if(auto* items=value["items"].getArray()) for(auto& it:*items) {
                auto c=std::make_unique<Clip>();auto source=open(it["path"].toString());
                auto buffered=std::make_unique<BufferingAudioReader>(source.release(),diskThread,262144);
                buffered->setReadTimeout(2000);c->reader=std::move(buffered);
                auto alternate=std::make_unique<BufferingAudioReader>(open(it["path"].toString()).release(),diskThread,262144);
                alternate->setReadTimeout(2000);c->standby=std::move(alternate);
                c->position=num(it,"position"); c->offset=num(it,"offset"); c->rate=num(it,"rate",1);
                if(c->rate<=0||c->rate>4)throw std::runtime_error("Velocidade inválida.");
                c->length=num(it,"length",std::max(0.0,double(c->reader->lengthInSamples)/c->reader->sampleRate-c->offset)/c->rate);
                if(c->reader->sampleRate/sampleRate*c->rate*blockSize+4>c->scratch.getNumSamples())throw std::runtime_error("Taxa de amostragem fora do limite.");
                t->clips.push_back(std::move(c));
            }
            t->effectId=str(value,"effectId");t->bus=str(value,"bus");t->special=str(value,"special");t->profile=str(value,"pitchProfile");tracks.push_back(std::move(t));
        }
        clickLeft=int(num(request,"clickLeft",maxOutputs));clickRight=int(num(request,"clickRight",maxOutputs+1));makeGroups(); configured=true;underruns=0;readBytes=0;
        updateMix(request); reset(0);
        return status();
    }
    void updateMix(const var& request) {
        if(auto* object=request["channelEffects"].getDynamicObject()){effectSettings.clear();for(const auto& property:object->getProperties())effectSettings[property.name.toString().toStdString()]=property.value;}
        for(auto& t:tracks)t->fx.configure(effectSettings[t->effectId],sampleRate);for(auto& g:groups){g->fx.configure(effectSettings[g->profile=="click"?"click":"group:"+g->bus],sampleRate);g->vsFx.configure(effectSettings["vs"],sampleRate);}
        masterFx.configure(effectSettings["master"],sampleRate);{std::lock_guard<std::mutex> c(callbackMutex);masterAuxFx.configure(effectSettings["master"],sampleRate);masterSoloFx.configure(effectSettings["master"],sampleRate);padFx.configure(effectSettings["pad"],sampleRate);soloFx.configure(effectSettings["click"],sampleRate);}
        master=float(num(request,"master",master.load()));
        masterLeft=int(num(request,"masterLeft",masterLeft.load()));masterRight=int(num(request,"masterRight",masterRight.load()));
        if(auto* array=request["mix"].getArray()) for(int i=0;i<array->size()&&i<int(tracks.size());++i){auto& t=*tracks[i];const double target=num((*array)[i],"gain",1),fade=std::clamp(num(request,"fadeSeconds"),0.0,10.0);
            if(std::abs(target-t.gainTarget)>1e-7){t.gainTarget=target;if(fade>0){t.gainRemaining=std::max(1,int(std::ceil(sampleRate*fade)));t.gainStep=(target-t.gain)/t.gainRemaining;}else{t.gain=target;t.gainRemaining=0;}}
            t.pan=num((*array)[i],"pan");}
        click=bool(request["click"]);clickGain=num(request,"clickGain",.5);clickPan=num(request,"clickPan");
        accent=bool(request["accent"]);meter=std::max(1,int(num(request,"meter",4)));accentPosition=int(num(request,"accentPosition"));
        if(auto* a=request["tempos"].getArray()){tempos.clear();for(auto& t:*a)tempos.emplace_back(num(t,"time"),std::max(20.0,num(t,"bpm",120)));if(tempos.empty())tempos={{0,120}};}
        if(request.hasProperty("loop")){auto loop=request["loop"];loopStart=num(loop,"start");loopEnd=num(loop,"end");loopEnabled=bool(loop["enabled"])&&loopEnd>loopStart&&loopStart>=0&&loopEnd<=duration;}
        if(request.hasProperty("jump")){
            auto j=request["jump"];auto id=str(j,"id");
            if(bool(j["enabled"])&&num(j,"boundary")>=0&&num(j,"target")>=0&&num(j,"target")<duration){
                if(id!=jumpId){jumpId=id;inputJumpDone=audibleJumpDone=false;}
                jumpEnabled=true;jumpBoundary=num(j,"boundary");jumpTarget=num(j,"target");
            }else jumpEnabled=false;
        }
        for(auto& t:tracks)t->preview=false;
        if(request["guidePreview"].isObject()&&!(jumpEnabled&&inputJumpDone)){
            auto p=request["guidePreview"];int index=int(num(p,"track",-1));
            if(index>=0&&index<int(tracks.size())){auto& t=*tracks[index];t.preview=true;t.previewStart=num(p,"start");t.previewEnd=num(p,"end");t.previewSource=num(p,"source");AudioBuffer<float> warm(2,1);for(auto& c:t.clips){auto* r=static_cast<BufferingAudioReader*>(c->standby.get());r->setReadTimeout(0);r->read(&warm,0,1,int64(std::max(0.0,(c->offset+(t.previewSource-c->position)*c->rate)*r->sampleRate)),true,true);r->setReadTimeout(2000);}}
        }
    }
    bool applyPitches(const var& request) {
        bool change=false;
        if(auto* a=request["mix"].getArray())for(int i=0;i<a->size()&&i<int(tracks.size());i++){auto& t=*tracks[i];auto value=(*a)[i];double pitch=num(value,"pitch");const auto profile=str(value,"pitchProfile"),bus=str(value,"bus");if(profile!=t.profile||bus!=t.bus){t.profile=profile;t.bus=bus;change=true;}int left=int(num(value,"outputLeft",maxOutputs)),right=int(num(value,"outputRight",maxOutputs+1));if(std::abs(pitch-t.pitch)>.001||left!=t.left||right!=t.right){t.pitch=pitch;t.left=left;t.right=right;change=true;}}
        int left=int(num(request,"clickLeft",maxOutputs)),right=int(num(request,"clickRight",maxOutputs+1));if(left!=clickLeft||right!=clickRight){clickLeft=left;clickRight=right;change=true;}
        if(change)makeGroups();return change;
    }
    void prepareDestination(double destination,bool wait,bool swap=false) {
        AudioBuffer<float> scratch(2,1);
        for(auto& t:tracks)for(auto& c:t->clips){
            if(swap){c->reader.swap(c->standby);c->cachedStart=-1000000;c->cachedCount=0;}
            auto* standby=static_cast<BufferingAudioReader*>(c->standby.get());
            double target=t->preview&&!swap?t->previewSource+std::max(0.0,inputPosition-t->previewStart):destination;
            auto start=int64(std::max(0.0,(c->offset+(target-c->position)*c->rate)*standby->sampleRate));
            standby->setReadTimeout(wait?2000:0);standby->read(&scratch,0,1,start,true,true);standby->setReadTimeout(2000);
        }
    }
    void prepareLoop(bool wait,bool swap=false) {
        if(jumpEnabled&&!inputJumpDone)prepareDestination(jumpTarget,wait,swap);
        else if(loopEnabled&&(!jumpEnabled||!inputJumpDone))prepareDestination(loopStart,wait,swap);
    }
    double advance(double p) {p+=1/sampleRate;if(jumpEnabled&&!audibleJumpDone&&p>=jumpBoundary-.5/sampleRate){p=jumpTarget+std::max(0.0,p-jumpBoundary);audibleJumpDone=true;renderedJumpSerial++;}else if(loopEnabled&&(!jumpEnabled||!audibleJumpDone)&&p>=loopEnd-.5/sampleRate)p=loopStart+std::max(0.0,p-loopEnd);return p;}
    void raw(int n,float* const* outputs) {
        ScopedNoDenormals noDenormals;vsSum.clear();for(auto& item:busMeters)item.second->sum.clear();
        for(auto& g:groups)g->input.clear();
        for(auto& t:tracks) {
            if(std::abs(t->gain)<.000001&&t->gainRemaining==0)continue;
            float levels[blockSize];for(int i=0;i<n;i++){if(t->gainRemaining>0){t->gain+=t->gainStep;if(--t->gainRemaining==0)t->gain=t->gainTarget;}levels[i]=t->gain;}
            double trackPosition=inputPosition;
            if(t->preview&&inputPosition>=t->previewStart){if(inputPosition>=t->previewEnd)continue;trackPosition+=t->previewSource-t->previewStart;}
            auto& dest=groups[t->group]->input;
            t->processed.clear();
            const float gl=(t->pan>0?std::cos(t->pan*float(MathConstants<double>::halfPi)):1);
            const float gr=(t->pan<0?std::cos(-t->pan*float(MathConstants<double>::halfPi)):1);
            for(auto& c:t->clips) {
                auto* reader=t->preview&&inputPosition>=t->previewStart?c->standby.get():c->reader.get();
                double begin=std::max(trackPosition,c->position),end=std::min(trackPosition+n/sampleRate,c->position+c->length);
                if(end<=begin)continue;
                int first=std::clamp(int(std::ceil((begin-trackPosition)*sampleRate-1e-7)),0,n);
                int last=std::clamp(int(std::ceil((end-trackPosition)*sampleRate-1e-7)),0,n);
                if(last<=first)continue;
                double source=(c->offset+(trackPosition+first/sampleRate-c->position)*c->rate)*c->reader->sampleRate;
                auto sourceStart=int64(std::floor(source));
                double ratio=c->reader->sampleRate/sampleRate*c->rate;
                int count=int(std::ceil((last-first)*ratio))+2;
                // Reuse a bounded source window instead of seeking every device block.
                // Only the worker thread touches these windows, never the device callback.
                if(sourceStart<c->cachedStart||sourceStart+count>c->cachedStart+c->cachedCount){
                    c->scratch.clear();c->cachedStart=sourceStart;
                    // Avoid waiting for a second read-ahead page when the first
                    // page already contains enough audio for this render block.
                    const int pageRemaining=32768-int((sourceStart%32768+32768)%32768);
                    c->cachedCount=std::max(count,std::min(c->scratch.getNumSamples(),pageRemaining));
                    const bool previewRead=t->preview&&inputPosition>=t->previewStart;
                    static_cast<BufferingAudioReader*>(reader)->setReadTimeout(previewRead?0:2000);
                    if(!reader->read(&c->scratch,0,c->cachedCount,sourceStart,true,true)){
                        // A late preview request must never stop the music while
                        // its guide snippet is still being fetched in the background.
                        if(previewRead){c->scratch.clear();c->cachedCount=0;}
                        else throw std::runtime_error("Falha ao ler áudio do disco.");
                    }
                    readBytes+=uint64_t(c->cachedCount*c->reader->numChannels*c->reader->bitsPerSample/8);
                }
                const float* sl=c->scratch.getReadPointer(0);const float* sr=c->scratch.getReadPointer(1);
                float* dl=t->processed.getWritePointer(0);float* dr=t->processed.getWritePointer(1);
                for(int i=first;i<last;i++) {double at=source-c->cachedStart+(i-first)*ratio;int j=int(at);float f=float(at-j);dl[i]+=(sl[j]+(sl[j+1]-sl[j])*f)*gl*levels[i];dr[i]+=(sr[j]+(sr[j+1]-sr[j])*f)*gr*levels[i];}
            }
            for(int i=0;i<n;i++){float l=t->processed.getSample(0,i),r=t->processed.getSample(1,i);t->fx.process(l,r);dest.addSample(0,i,l);dest.addSample(1,i,r);}
        }
        // The click shares the same processing delay and disk-playback clock.
        Group* dry=nullptr;for(auto& g:groups)if(g->profile=="click"&&g->left==clickLeft&&g->right==clickRight){dry=g.get();break;}
        if(click&&dry){for(int i=0;i<n;i++){
            double time=inputPosition+i/sampleRate;auto tempo=tempos.front();for(auto& p:tempos){if(p.first>time)break;tempo=p;}
            const double step=60/tempo.second,beat=(time-tempo.first)/step;
            const int latest=int(std::floor(beat+1e-8));
            const int tails=int(std::ceil(clickLength()/step));
            float left=0,right=0;
            // Use the exact same sample and accent factors as Solo Click.
            // Preserve overlapping sample tails at fast tempos.
            for(int back=0;back<=tails;back++){
                const int index=latest-back;if(index<0)continue;
                const double since=time-(tempo.first+index*step);
                const bool strong=accent&&(index%meter==accentPosition);
                left+=clickValue(since,strong,0)*clickGain;right+=clickValue(since,strong,1)*clickGain;
            }
            // Match Web Audio's stereo panner instead of attenuating one side linearly.
            const double angle=(clickPan<0?clickPan+1:clickPan)*MathConstants<double>::halfPi;
            const float outLeft=clickPan<0?left+right*float(std::cos(angle)):left*float(std::cos(angle));
            const float outRight=clickPan<0?right*float(std::sin(angle)):right+left*float(std::sin(angle));
            dry->input.addSample(0,i,outLeft);dry->input.addSample(1,i,outRight);
        }}
        for(int ch=0;ch<renderOutputs;ch++)std::fill(outputs[ch],outputs[ch]+n,0);
        for(auto& g:groups){
            if(std::abs(g->pitch)>.001)g->stretch.process(g->input.getArrayOfReadPointers(),n,g->output.getArrayOfWritePointers(),n);
            else for(int i=0;i<n;i++)for(int ch=0;ch<2;ch++){
                auto& delay=ch==0?g->delayL:g->delayR;float value=g->input.getSample(ch,i);
                if(!delay.empty()){float previous=delay[(g->delayIndex+i)%delay.size()];delay[(g->delayIndex+i)%delay.size()]=value;value=previous;}
                g->output.setSample(ch,i,value);
            }
            if(!g->delayL.empty())g->delayIndex=(g->delayIndex+n)%g->delayL.size();
            for(int i=0;i<n;i++){
                float l=g->output.getSample(0,i),r=g->output.getSample(1,i);if(g->profile=="click")g->fx.process(l,r);else if(!g->special){if(g->bus!="VS")g->fx.process(l,r);g->vsFx.process(l,r);vsSum.addSample(0,i,l);vsSum.addSample(1,i,r);if(auto it=busMeters.find(g->bus);it!=busMeters.end()){it->second->sum.addSample(0,i,l);it->second->sum.addSample(1,i,r);}}g->output.setSample(0,i,l);g->output.setSample(1,i,r);
                if(g->left>=0&&g->left<renderOutputs)outputs[g->left][i]+=g->right<0?.5f*(g->output.getSample(0,i)+g->output.getSample(1,i)):g->output.getSample(0,i);
                if(g->right>=0&&g->right<renderOutputs)outputs[g->right][i]+=g->output.getSample(1,i);
            }
        }
        for(int i=0;i<n;i++){vsMeter.add(vsSum.getSample(0,i),vsSum.getSample(1,i));for(auto& bus:busMeters)bus.second->meter.add(bus.second->sum.getSample(0,i),bus.second->sum.getSample(1,i));}
        for(int i=0;i<n;i++){float l=outputs[maxOutputs][i],r=outputs[maxOutputs+1][i];masterFx.process(l,r);outputs[maxOutputs][i]=l;outputs[maxOutputs+1][i]=r;}
        inputPosition+=n/sampleRate;
    }
    void render(Block& block,bool discard=false,int count=blockSize) {
        int done=0;
        while(done<count){
            if(jumpEnabled&&!inputJumpDone&&inputPosition>=jumpBoundary-.5/sampleRate){prepareDestination(jumpTarget,false,true);inputPosition=jumpTarget;inputJumpDone=true;for(auto& t:tracks)t->preview=false;}
            const bool looping=loopEnabled&&(!jumpEnabled||!inputJumpDone);
            if(looping&&inputPosition>=loopEnd-.5/sampleRate){inputPosition=loopStart;prepareLoop(false,true);}
            int n=count-done;
            if(jumpEnabled&&!inputJumpDone&&inputPosition<jumpBoundary)n=std::min(n,std::max(1,int(std::llround((jumpBoundary-inputPosition)*sampleRate))));
            for(auto& t:tracks)if(t->preview)for(double boundary:{t->previewStart,t->previewEnd})if(boundary>inputPosition+.5/sampleRate)n=std::min(n,std::max(1,int(std::llround((boundary-inputPosition)*sampleRate))));
            if(looping&&inputPosition<loopEnd)n=std::min(n,std::max(1,int(std::llround((loopEnd-inputPosition)*sampleRate))));
            float* outputs[renderOutputs];for(int ch=0;ch<renderOutputs;ch++)outputs[ch]=block.audio[ch]+done;
            raw(n,outputs);done+=n;
        }
        if(!discard)for(int i=0;i<count;i++){audiblePosition=advance(audiblePosition);block.positions[i]=audiblePosition;block.jumps[i]=renderedJumpSerial;}
    }
    void reset(double position) {
        std::lock_guard<std::mutex> c(callbackMutex);
        head=tail=0;callbackOffset=0;inputPosition=audiblePosition=std::clamp(position,0.0,duration);playhead=audiblePosition;
        inputJumpDone=audibleJumpDone=false;renderedJumpSerial=playedJumpSerial.load();
        prepareLoop(true);
        for(auto& g:groups){if(std::abs(g->pitch)>.001)g->stretch.reset();std::fill(g->delayL.begin(),g->delayL.end(),0.0f);std::fill(g->delayR.begin(),g->delayR.end(),0.0f);g->delayIndex=0;}
        Block scratch;
        for(int remaining=latency;remaining>0;remaining-=blockSize)render(scratch,true,std::min(remaining,blockSize));
        for(int i=0;i<16;i++){render(queue[i]);tail++;}
    }
    void run() {
        while(!quit){
            bool produced=false;
            try {std::lock_guard<std::mutex> l(renderMutex);
                if(configured&&tail.load()-head.load()<queueSize-1){auto started=std::chrono::steady_clock::now();auto t=tail.load();render(queue[t%queueSize]);tail.store(t+1);produced=true;maxRenderMs=std::max(maxRenderMs,std::chrono::duration<double,std::milli>(std::chrono::steady_clock::now()-started).count());}
            }catch(const std::exception& e){playing=false;configured=false;send(object({{"event","error"},{"message",String(e.what())}}));}
            // Windows timer granularity can turn a 1 ms sleep into ~16 ms.
            // Fill the queue without sleeping; yield only once it is full.
            if(!produced)std::this_thread::sleep_for(std::chrono::milliseconds(1));
        }
    }
    float physicalSample(const Block& b,int ch,int frame) {
        float v=b.audio[ch][frame];const int l=masterLeft,r=masterRight;const float gain=master;
        if(ch==l)v+=(r<0?.5f*(b.audio[maxOutputs][frame]+b.audio[maxOutputs+1][frame]):b.audio[maxOutputs][frame])*gain;
        if(ch==r)v+=b.audio[maxOutputs+1][frame]*gain;return v;
    }
    void audioDeviceIOCallbackWithContext(const float* const*,int,float* const* outputs,int channels,int samples,const AudioIODeviceCallbackContext&) override {
        ScopedNoDenormals noDenormals;
        maxCallbackFrames=std::max(maxCallbackFrames.load(),samples);
        for(int c=0;c<channels;c++)if(outputs[c])std::fill(outputs[c],outputs[c]+samples,0);
        std::unique_lock<std::mutex> lock(callbackMutex,std::try_to_lock);
        if(!lock.owns_lock())return;
        int done=0;float gain=master;if(samples>masterMeterScratch.getNumSamples())return;masterMeterScratch.clear(0,samples);int handoffCut=-1;if(playing&&handoffArmed){if(launchWaitFrames>0){done=std::min(samples,launchWaitFrames);launchWaitFrames-=done;}if(launchWaitFrames==0&&done<samples){handoffCut=done;handoffArmed=false;}}
        while(done<samples&&playing){
            auto h=head.load();if(h>=tail.load()){underruns++;break;}
            auto& block=queue[h%queueSize];int n=std::min(samples-done,blockSize-callbackOffset);
            for(int i=0;i<n;i++){
                for(int ch=0;ch<std::min(channels,maxOutputs);ch++)if(outputs[ch])outputs[ch][done+i]=physicalSample(block,ch,callbackOffset+i);
            }
            for(int i=0;i<n;i++){masterMeterScratch.addSample(0,done+i,block.audio[maxOutputs][callbackOffset+i]*gain);masterMeterScratch.addSample(1,done+i,block.audio[maxOutputs+1][callbackOffset+i]*gain);}
            playhead=block.positions[callbackOffset+n-1];playedJumpSerial=block.jumps[callbackOffset+n-1];callbackOffset+=n;done+=n;
            if(!loopEnabled&&playhead>=duration){playing=false;playhead=duration;}
            if(callbackOffset==blockSize){callbackOffset=0;head.store(h+1);}
        }
        auto add=[&](int frame,float left,float right,int l,int r,bool solo=false){float level=1;if(l==maxOutputs){(solo?masterSoloFx:masterAuxFx).process(left,right);masterMeterScratch.addSample(0,frame,left*gain);masterMeterScratch.addSample(1,frame,right*gain);l=masterLeft;r=masterRight;level=gain;}if(l>=0&&l<channels&&outputs[l])outputs[l][frame]+=(r<0?.5f*(left+right):left)*level;if(r>=0&&r<channels&&outputs[r])outputs[r][frame]+=right*level;};
        auto panStereo=[](float& l,float& r,float pan){double angle=(pan<0?pan+1:pan)*MathConstants<double>::halfPi;float left=pan<0?l+r*float(std::cos(angle)):l*float(std::cos(angle));r=pan<0?r*float(std::sin(angle)):r+l*float(std::sin(angle));l=left;};
        for(int i=0;i<samples;i++){
            if(i==handoffCut){soloEnabled=false;soloFade={};}
            float pl=0,pr=0;
            for(int note=0;note<12;note++){
                auto* voice=&padVoices[note];
                if(voice->fade.level<=0&&voice->fade.remaining==0)continue;
                const float level=voice->fade.next();
                const auto& wave=padSamples[note];const int frames=wave.getNumSamples();if(frames<2)continue;
                const double fade=std::min(.5*padRates[note],frames*.25),period=frames-fade;
                double at=std::fmod(voice->at,period);int j=int(at);float f=float(at-j);
                for(int ch=0;ch<2;ch++){
                    auto sample=[&](double index){int a=int(index);float fraction=float(index-a);return wave.getSample(ch,a)+(wave.getSample(ch,std::min(a+1,frames-1))-wave.getSample(ch,a))*fraction;};
                    float v=sample(at);if(voice->at>=period&&at<fade){double angle=at/fade*MathConstants<double>::halfPi;v=v*float(std::sin(angle))+sample(period+at)*float(std::cos(angle));}
                    if(ch==0)pl+=v*level*padGain;else pr+=v*level*padGain;
                }
                voice->at+=padRates[note]/sampleRate;
            }
            if(previewAt>=0){add(i,clickValue(previewAt,previewAccent,0),clickValue(previewAt,previewAccent,1),soloLeft,soloRight,true);previewAt+=1/sampleRate;if(previewAt>clickLength())previewAt=-1;}
            panStereo(pl,pr,padPan);padFx.process(pl,pr);add(i,pl,pr,padLeft,padRight);
            if(soloEnabled||soloFade.level>0||soloFade.remaining>0){
                const float level=soloFade.next();float l=0,r=0;const double step=60/soloBpm;const int latest=int(std::floor(soloTime/step+1e-8)),tails=int(std::ceil(clickLength()/step));
                for(int back=0;back<=tails;back++){int index=latest-back;if(index<0)continue;bool strong=soloAccent&&index%soloMeter==soloAccentPosition;double since=soloTime-index*step;l+=clickValue(since,strong,0)*soloGain;r+=clickValue(since,strong,1)*soloGain;}
                panStereo(l,r,soloPan);l*=level;r*=level;soloFx.process(l,r);add(i,l,r,soloLeft,soloRight,true);soloTime+=1/sampleRate;
            }
        }
        for(int i=0;i<samples;i++)masterMeter.add(masterMeterScratch.getSample(0,i),masterMeterScratch.getSample(1,i));
        for(int ch=0;ch<channels;ch++)if(outputs[ch])for(int i=0;i<samples;i++)outputs[ch][i]=std::clamp(outputs[ch][i],-1.0f,1.0f);
    }
    void audioDeviceAboutToStart(AudioIODevice*) override {}
    void audioDeviceStopped() override {playing=false;}
    var status() {const auto memory=platformMemory();return object({{"soloEnabled",soloEnabled},{"launchWaitFrames",launchWaitFrames},{"jumpSerial",playedJumpSerial.load()},{"jumpId",String(jumpId)},{"position",playhead.load()},{"playing",playing.load()&&launchWaitFrames==0},{"sampleRate",sampleRate},{"tracks",int(tracks.size())},{"underruns",int64(underruns.load())},{"readBytes",int64(readBytes.load())},{"queuedFrames",int((tail.load()-head.load())*blockSize)},{"latencyFrames",latency},{"maxCallbackFrames",maxCallbackFrames.load()},{"maxRenderMs",maxRenderMs},{"workingSetBytes",int64(memory.first)},{"privateBytes",int64(memory.second)}});}
    var command(const var& request) {
        auto op=str(request,"op");
        if(op=="clickSounds"){const String normal=request["normal"].toString(),accentPath=request["accent"].toString();if(normal==normalSource&&accentPath==accentSource&&clickSample.getNumSamples()>0)return true;AudioBuffer<float> normalWave,accentWave;double normalRate=44100,accentRate=44100;readClick(normal.isEmpty()?File::getSpecialLocation(File::currentExecutableFile).getSiblingFile("Meuclick.wav").getFullPathName():normal,normalWave,normalRate);if(accentPath.isNotEmpty())readClick(accentPath,accentWave,accentRate);else if(normal.isNotEmpty())readClick(File::getSpecialLocation(File::currentExecutableFile).getSiblingFile("Meuclick.wav").getFullPathName(),accentWave,accentRate);std::lock_guard<std::mutex> l(renderMutex);{std::lock_guard<std::mutex> c(callbackMutex);clickSample=std::move(normalWave);accentSample=std::move(accentWave);clickSampleRate=normalRate;accentSampleRate=accentRate;normalSource=normal;accentSource=accentPath;accentIsDefault=accentPath.isEmpty();}if(configured)reset(playhead.load());return true;}
        if(op=="previewClick"){ensureDevice(44100);loadClickSample();std::lock_guard<std::mutex> c(callbackMutex);previewAccent=bool(request["accent"]);previewAt=0;return true;}
        if(op=="cpuFeatures"){return object({{"avx2",SystemStats::hasAVX2()&&SystemStats::hasFMA3()&&platformHasF16C()}});}
        if(op=="spectrum"){
            std::lock_guard<std::mutex> l(renderMutex);std::lock_guard<std::mutex> c(callbackMutex);
            const auto id=str(request,"effectId");std::vector<ChannelDsp*> selected;
            auto choose=[&](ChannelDsp& fx,bool match){if(match&&!fx.capture){fx.spectrumBefore.fill(0);fx.spectrumAfter.fill(0);fx.spectrumAt=0;}fx.capture=match;if(match)selected.push_back(&fx);};
            for(auto& t:tracks)choose(t->fx,!id.empty()&&id==t->effectId);
            for(auto& g:groups){choose(g->fx,id=="click"?g->profile=="click":!g->special&&g->bus!="VS"&&id=="group:"+g->bus);choose(g->vsFx,id=="vs"&&!g->special);}
            choose(padFx,id=="pad");choose(soloFx,id=="click"&&(soloEnabled||soloFade.level>1e-6));choose(masterFx,id=="master");choose(masterAuxFx,id=="master");choose(masterSoloFx,id=="master"&&(soloEnabled||soloFade.level>1e-6));
            Array<var> before,after;for(int i=0;i<16384;i++){double a=0,b=0;for(auto* fx:selected){const int at=(fx->spectrumAt+i)%16384;a+=fx->spectrumBefore[at];b+=fx->spectrumAfter[at];}before.add(a);after.add(b);}return object({{"before",before},{"after",after},{"sampleRate",sampleRate}});
        }
        if(op=="meters"){
            std::lock_guard<std::mutex> l(renderMutex);DynamicObject* object=new DynamicObject();
            auto put=[&](const std::string& id,LevelMeter& meter){auto values=meter.read();auto& previous=lastMeterLevels[id];for(int ch=0;ch<2;ch++)previous[ch]=std::max(float(double(values[ch])),previous[ch]*.75f);object->setProperty(Identifier(String(id)),Array<var>{double(previous[0]),double(previous[1])});};
            for(auto& t:tracks)if(!t->effectId.empty())put(t->effectId,t->fx.meter);for(auto& bus:busMeters)if(bus.first!="VS")put("group:"+bus.first,bus.second->meter);put("vs",vsMeter);put("master",masterMeter);put("pad",padFx.meter);put("click",soloFx.meter);for(auto& g:groups)if(g->profile=="click"){auto values=g->fx.meter.read();auto old=object->getProperty("click");object->setProperty("click",Array<var>{std::max(double(old[0]),double(values[0])),std::max(double(old[1]),double(values[1]))});}return var(object);
        }
        if(op=="devices"){ensureDevice(44100);return deviceInfo();}
        if(op=="configureDevice")return configureDevice(request);
        if(op=="controlPanel"){
            playing=false;auto* device=devices.getCurrentAudioDevice();
            if(!device||!device->hasControlPanel())throw std::runtime_error("Este driver não oferece painel próprio.");
            device->showControlPanel();return configureDevice(deviceInfo());
        }
        if(op=="aux"){
            if(!bool(request["offline"]))ensureDevice(44100);loadClickSample();int note=int(num(request,"note",-1));
            if(note>=0&&note<12&&bool(request["padEnabled"])){
                String source=request["padPath"].toString(),stamp=request["padStamp"].toString();
                if(source.isEmpty()||!File(source).existsAsFile())throw std::runtime_error("Pad indisponível. Confira a pasta de pads em Config.");
                if(padSources[note]!=source||padStamps[note]!=stamp||padSamples[note].getNumSamples()==0){
                auto reader=open(source);
                if(reader->lengthInSamples>reader->sampleRate*60)throw std::runtime_error("Pad longo demais.");
                AudioBuffer<float> sample(2,int(reader->lengthInSamples));reader->read(&sample,0,int(reader->lengthInSamples),0,true,true);
                std::lock_guard<std::mutex> lock(callbackMutex);
                padVoices[note]={};if(activePad==note){activePad=-1;padEnabled=false;}
                padSamples[note]=std::move(sample);padRates[note]=reader->sampleRate;padSources[note]=source;padStamps[note]=stamp;
                }
            }
            std::lock_guard<std::mutex> c(callbackMutex);
            if(auto* effects=request["channelEffects"].getDynamicObject()){padFx.configure(effects->getProperty("pad"),sampleRate);soloFx.configure(effects->getProperty("click"),sampleRate);masterAuxFx.configure(effects->getProperty("master"),sampleRate);masterSoloFx.configure(effects->getProperty("master"),sampleRate);}
            masterLeft=int(num(request,"masterLeft",masterLeft.load()));masterRight=int(num(request,"masterRight",masterRight.load()));
            master=float(num(request,"master",master.load()));
            if(request.hasProperty("padEnabled")){
                const bool enabled=bool(request["padEnabled"]);const int selected=enabled&&note>=0&&note<12?note:activePad;
                if(enabled!=padEnabled||(enabled&&selected!=activePad)){
                    for(int i=0;i<12;i++){
                        const bool target=enabled&&i==selected;
                        if(target&&padVoices[i].fade.level==0&&padVoices[i].fade.remaining==0)padVoices[i].at=0;
                        padVoices[i].fade.target(target,sampleRate);
                    }
                }
                padEnabled=enabled;activePad=selected;
            }
            if(bool(request["padImmediateStop"])){padVoices={};padEnabled=false;activePad=-1;}
            padGain=num(request,"padGain",padGain);padPan=num(request,"padPan",padPan);padLeft=int(num(request,"padLeft",padLeft));padRight=int(num(request,"padRight",padRight));
            if(bool(request["soloImmediateStop"])){soloEnabled=false;soloFade={};}
            if(request.hasProperty("soloEnabled")){bool enabled=bool(request["soloEnabled"]);if(enabled!=soloEnabled){if(enabled&&soloFade.level==0)soloTime=0;soloFade.target(enabled,sampleRate);}soloEnabled=enabled;}
            soloBpm=std::clamp(num(request,"soloBpm",soloBpm),20.0,400.0);soloGain=num(request,"soloGain",soloGain);soloPan=num(request,"soloPan",soloPan);soloLeft=int(num(request,"soloLeft",soloLeft));soloRight=int(num(request,"soloRight",soloRight));
            soloMeter=std::max(1,int(num(request,"soloMeter",soloMeter)));soloAccent=bool(request["soloAccent"]);soloAccentPosition=int(num(request,"soloAccentPosition"));return true;
        }
        if(op=="probe")return probe(request["files"]);
        if(op=="load")return load(request);
        if(op=="status") {std::lock_guard<std::mutex> l(renderMutex);return status();}
        if(op=="pause"){playing=false;if(handoffArmed){std::lock_guard<std::mutex> c(callbackMutex);soloEnabled=false;soloFade={};handoffArmed=false;launchWaitFrames=0;}return status();}
        if(op=="master"){master=float(num(request,"value",1));return true;}
        if(op=="play"||op=="seek"||op=="stop"){
            bool resume=op=="play"||bool(request["playing"]);playing=false;
            std::lock_guard<std::mutex> l(renderMutex);if(!configured)throw std::runtime_error("Abra uma música primeiro.");
            updateMix(request);applyPitches(request);const double desired=op=="stop"?0:num(request,"position",playhead.load());if(op!="play"||std::abs(desired-playhead.load())>0.5/sampleRate||head.load()==tail.load())reset(desired);if(op=="play"&&bool(request["handoff"])&&soloEnabled){const double bar=60.0/soloBpm*soloMeter;launchWaitFrames=std::max(1,int(std::ceil((std::ceil((soloTime+.15)/bar)*bar-soloTime)*sampleRate)));handoffArmed=true;}else{launchWaitFrames=0;handoffArmed=false;}playing=resume;return status();
        }
        if(op=="mix"){
            std::lock_guard<std::mutex> l(renderMutex);updateMix(request);
            bool change=applyPitches(request);
            if(change){bool was=playing.exchange(false);double p=playhead;reset(p);playing=was;}
            else prepareLoop(false);
            return true;
        }
        if(op=="peaks"){
            auto r=open(request["path"].toString());int bins=std::clamp(int(num(request,"bins",1600)),64,4000);Array<var> peaks;const double start=std::clamp(num(request,"start"),0.0,double(r->lengthInSamples)/r->sampleRate),end=std::clamp(num(request,"end",double(r->lengthInSamples)/r->sampleRate),start,double(r->lengthInSamples)/r->sampleRate);const int window=std::clamp(int((end-start)*r->sampleRate/bins),1,4096);
            AudioBuffer<float> data(2,4096);
            // Sample small windows; background preview never requires a complete PCM buffer.
            for(int i=0;i<bins;i++){int64 at=int64((start+double(i)/bins*(end-start))*r->sampleRate);r->read(&data,0,window,at,true,true);float peak=0;for(int ch=0;ch<2;ch++)peak=std::max(peak,data.getMagnitude(ch,0,window));peaks.add(double(peak));}
            return peaks;
        }
        if(op=="auxRenderTest"){
            playing=false;int frames=std::clamp(int(num(request,"frames",44100)),1,441000),channels=std::clamp(int(num(request,"channels",4)),1,maxOutputs);Array<var> captured;
            AudioBuffer<float> buffer(channels,blockSize);float* pointers[maxOutputs];for(int ch=0;ch<channels;ch++)pointers[ch]=buffer.getWritePointer(ch);
            for(int done=0;done<frames;done+=blockSize){const int count=std::min(blockSize,frames-done);audioDeviceIOCallbackWithContext(nullptr,0,pointers,channels,count,{});for(int i=0;i<count;i++)for(int ch=0;ch<channels;ch++)captured.add(double(buffer.getSample(ch,i)));}
            return object({{"channels",channels},{"samples",captured}});
        }
        if(op=="clickEnvelope"){
            auto r=open(request["path"].toString());const double start=std::max(0.0,num(request,"start")),end=std::min(double(r->lengthInSamples)/r->sampleRate,num(request,"end"));const int step=std::max(1,int(r->sampleRate*.002));AudioBuffer<float> data(2,step);Array<var> peaks;for(int64 at=int64(start*r->sampleRate);at<int64(end*r->sampleRate);at+=step){r->read(&data,0,step,at,true,true);peaks.add(double(data.getMagnitude(0,0,step)));}return peaks;
        }
        if(op=="exportGuide"){
            playing=false;std::lock_guard<std::mutex> lock(renderMutex);reset(0);File file(request["path"].toString());auto stream=file.createOutputStream();if(!stream)throw std::runtime_error("Cannot write guide.");WavAudioFormat format;std::unique_ptr<AudioFormatWriter> writer(format.createWriterFor(stream.release(),sampleRate,1,16,{},0));if(!writer)throw std::runtime_error("Cannot create WAV.");const int64 frames=int64(duration*sampleRate);Block block;AudioBuffer<float> mono(1,blockSize);for(int64 done=0;done<frames;done+=blockSize){auto h=head.load();if(h<tail.load()){block=queue[h%queueSize];head=h+1;}else render(block);const int count=int(std::min<int64>(blockSize,frames-done));for(int i=0;i<count;i++)mono.setSample(0,i,(physicalSample(block,0,i)+physicalSample(block,1,i))*.5f);writer->writeFromAudioSampleBuffer(mono,0,count);}return true;
        }
        if(op=="analyzeLoudness"){

            playing=false;std::lock_guard<std::mutex> lock(renderMutex);reset(0);
            const int64 frames=int64(std::ceil(duration*sampleRate));
            if(frames<=0||duration>21600)throw std::runtime_error("Duração inválida para análise.");
            auto* meter=ebur128_init(2,(unsigned long)sampleRate,EBUR128_MODE_I|EBUR128_MODE_TRUE_PEAK);
            if(!meter)throw std::runtime_error("Não foi possível iniciar a medição.");
            Block block;float interleaved[blockSize*2];int64 done=0;
            while(done<frames){auto h=head.load();if(h<tail.load()){block=queue[h%queueSize];head=h+1;}else render(block);const int count=int(std::min<int64>(blockSize,frames-done));for(int i=0;i<count;i++){interleaved[i*2]=physicalSample(block,0,i);interleaved[i*2+1]=physicalSample(block,1,i);}ebur128_add_frames_float(meter,interleaved,count);done+=count;}
            double loudness=0,left=0,right=0;ebur128_loudness_global(meter,&loudness);ebur128_true_peak(meter,0,&left);ebur128_true_peak(meter,1,&right);ebur128_destroy(&meter);
            if(!std::isfinite(loudness))throw std::runtime_error("Sinal silencioso ou curto demais para medir loudness.");
            return object({{"lufs",loudness},{"truePeak",20*std::log10(std::max(1e-12,std::max(left,right)))},{"seconds",duration}});
        }
        if(op=="renderTest"){
            playing=false;std::lock_guard<std::mutex> l(renderMutex);reset(num(request,"position"));
            int frames=std::clamp(int(num(request,"frames",44100)),1,441000),channels=std::clamp(int(num(request,"channels",2)),1,maxOutputs);double energy=0;float peak=0;Block b; Array<var> captured;
            // Consume the primed queue before rendering subsequent blocks.
            int done=0;while(done<frames){auto h=head.load();if(h<tail.load()){b=queue[h%queueSize];head=h+1;}else render(b);int n=std::min(blockSize,frames-done);for(int i=0;i<n;i++)for(int ch=0;ch<channels;ch++){const float v=physicalSample(b,ch,i);energy+=double(v)*v;peak=std::max(peak,std::abs(v));if(bool(request["capture"]))captured.add(double(v));}done+=n;}
            return object({{"frames",frames},{"channels",channels},{"rms",std::sqrt(energy/(frames*channels))},{"peak",double(peak)},{"readBytes",int64(readBytes.load())},{"samples",captured}});
        }
        throw std::runtime_error("Comando de áudio desconhecido.");
    }
};
int main(){
    ScopedJuceInitialiser_GUI initialise;
    auto ownedEngine=std::make_unique<Engine>();auto& engine=*ownedEngine;
    std::thread input([&]{std::string line;while(std::getline(std::cin,line)){
        auto request=JSON::parse(String::fromUTF8(line.c_str()));
        if(str(request,"op")=="quit")break;
        MessageManager::callAsync([&,request]{auto id=request["id"];
            try{send(object({{"id",id},{"ok",true},{"result",engine.command(request)}}));}
            catch(const std::exception& e){send(object({{"id",id},{"ok",false},{"error",String::fromUTF8(e.what())}}));}
        });
    }MessageManager::callAsync([]{MessageManager::getInstance()->stopDispatchLoop();});});
    MessageManager::getInstance()->runDispatchLoop();input.join();
    return 0;
}


