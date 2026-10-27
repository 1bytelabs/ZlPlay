// SPDX-License-Identifier: AGPL-3.0-only
#include <juce_gui_extra/juce_gui_extra.h>
#include <juce_audio_formats/juce_audio_formats.h>
#include "embedded-engine.h"
#include <atomic>
using namespace juce;
static var json(std::initializer_list<std::pair<const char*,var>> properties){auto* o=new DynamicObject();for(auto& p:properties)o->setProperty(p.first,p.second);return var(o);}
static String text(const char* pt,const char* en,const char* es){const auto l=SystemStats::getUserLanguage();return String::fromUTF8(l.startsWithIgnoreCase("pt")?pt:l.startsWithIgnoreCase("es")?es:en);}
class Player final : public Component,private Timer {
public:
    Player():engine(zlplay::createEngine()){
        setOpaque(true);
        title.setText("ZLPlay Mobile",dontSendNotification);title.setFont(FontOptions(25).withStyle("Bold"));addAndMakeVisible(title);
        status.setText(text("Abra arquivos de áudio para começar.","Open audio files to begin.","Abre archivos de audio para empezar."),dontSendNotification);addAndMakeVisible(status);
        open.setButtonText(text("Importar áudios","Import audio","Importar audios"));play.setButtonText(text("Tocar","Play","Reproducir"));pause.setButtonText(text("Pausar","Pause","Pausar"));stop.setButtonText(text("Parar","Stop","Detener"));click.setButtonText("Click");
        for(auto* b:{&open,&play,&pause,&stop,&click}){addAndMakeVisible(*b);b->setColour(TextButton::buttonColourId,Colour(0xff272039));b->setColour(TextButton::buttonOnColourId,Colour(0xff9418dd));}
        open.onClick=[this]{chooseAudio();};play.onClick=[this]{send(json({{"op","play"}}));};pause.onClick=[this]{send(json({{"op","pause"}}));};stop.onClick=[this]{send(json({{"op","stop"}}));};
        click.setClickingTogglesState(true);click.onClick=[this]{updateMix();};
        master.setRange(0,2,.01);master.setValue(1);master.setTextBoxStyle(Slider::TextBoxRight,false,65,30);master.setName("Master");master.onValueChange=[this]{send(json({{"op","master"},{"value",master.getValue()}}));};addAndMakeVisible(master);masterLabel.setText("Master",dontSendNotification);addAndMakeVisible(masterLabel);
        seek.setRange(0,1,.01);seek.setTextBoxStyle(Slider::NoTextBox,false,0,0);seek.setName(text("Posição","Position","Posición"));seek.onDragEnd=[this]{send(json({{"op","seek"},{"position",seek.getValue()},{"playing",playing}}));};addAndMakeVisible(seek);
        bpm.setRange(20,400,1);bpm.setValue(120);bpm.setTextBoxStyle(Slider::TextBoxRight,false,65,30);bpm.setName("BPM");bpm.onValueChange=[this]{updateMix();};addAndMakeVisible(bpm);bpmLabel.setText(String::fromUTF8("BPM · Click"),dontSendNotification);addAndMakeVisible(bpmLabel);
        addAndMakeVisible(clock);view.setViewedComponent(&strips,false);view.setScrollBarsShown(true,false);addAndMakeVisible(view);
#if JUCE_DEBUG
        fixture.setButtonText(text("Teste sintético", "Synthetic test", "Prueba sintética"));addAndMakeVisible(fixture);
        fixture.onClick=[this]{
            fixture.setEnabled(false);Component::SafePointer<Player> safe(this);
            Thread::launch([safe]{
                auto file=File::getSpecialLocation(File::tempDirectory).getChildFile("ZLPlay-Synthetic-"+Uuid().toString()+".wav");
                bool ok=false;
                if(auto stream=file.createOutputStream()){
                    WavAudioFormat format;std::unique_ptr<AudioFormatWriter> writer(format.createWriterFor(stream.release(),44100,2,16,{},0));
                    if(writer){AudioBuffer<float> wave(2,44100*8);for(int i=0;i<wave.getNumSamples();++i){wave.setSample(0,i,.05f*std::sin(float(MathConstants<double>::twoPi*440*i/44100)));wave.setSample(1,i,.05f*std::sin(float(MathConstants<double>::twoPi*660*i/44100)));}ok=writer->writeFromAudioSampleBuffer(wave,0,wave.getNumSamples());}
                }
                MessageManager::callAsync([safe,file,ok]{if(!safe){file.deleteFile();return;}safe->fixture.setEnabled(true);if(ok)safe->load(Array<File>{file});});
            });
        };
#endif
        setSize(900,650);enableTransport(false);startTimerHz(10);
    }
    ~Player()override{stopTimer();zlplay::destroyEngine(engine);}
    void paint(Graphics& g)override{g.fillAll(Colour(0xff0d101c));g.setColour(Colour(0xffbd68fa));g.fillRoundedRectangle(18,26,5,32,2);}
    void resized()override{
        auto r=getLocalBounds().reduced(18);r.removeFromTop(24);title.setBounds(r.removeFromTop(42).withTrimmedLeft(16));status.setBounds(r.removeFromTop(34));
        open.setBounds(r.removeFromTop(46));
#if JUCE_DEBUG
        fixture.setBounds(r.removeFromTop(36).reduced(2));
#endif
        r.removeFromTop(12);auto row=r.removeFromTop(48);const auto w=row.getWidth()/4;for(auto* b:{&play,&pause,&stop,&click})b->setBounds(row.removeFromLeft(w).reduced(2));
        clock.setBounds(r.removeFromTop(28));seek.setBounds(r.removeFromTop(40));auto bpmRow=r.removeFromTop(42);bpmLabel.setBounds(bpmRow.removeFromLeft(95));bpm.setBounds(bpmRow);auto masterRow=r.removeFromTop(42);masterLabel.setBounds(masterRow.removeFromLeft(95));master.setBounds(masterRow);r.removeFromTop(8);view.setBounds(r);strips.setSize(jmax(1,r.getWidth()-16),jmax(1,int(faders.size())*62));
        for(int i=0;i<int(faders.size());++i){names[i]->setBounds(6,i*62,jmax(1,strips.getWidth()-12),23);faders[i]->setBounds(6,i*62+24,jmax(1,strips.getWidth()-12),36);}
    }
    void suspend(){if(engine){send(json({{"op","pause"}}));send(json({{"op","aux"},{"padEnabled",false},{"padImmediateStop",true},{"soloEnabled",false},{"soloImmediateStop",true},{"offline",true}}));}}
private:
    zlplay::EmbeddedEngine* engine;Label title,status,clock,masterLabel,bpmLabel;TextButton open,play,pause,stop,click;
#if JUCE_DEBUG
    TextButton fixture;
#endif
Slider master,seek,bpm;Viewport view;Component strips;OwnedArray<Label> names;OwnedArray<Slider> faders;std::unique_ptr<FileChooser> picker;Array<var> tracks;bool loaded=false,playing=false;double duration=0;
    var send(var request){try{return zlplay::engineCommand(engine,request);}catch(const std::exception& error){Logger::writeToLog(String::fromUTF8(error.what()));status.setText(text("Não foi possível concluir. Confira os arquivos e a saída de áudio.","Could not complete. Check files and audio output.","No se pudo completar. Revisa archivos y salida de audio."),dontSendNotification);return {};}}
    void enableTransport(bool value){for(auto* b:{&play,&pause,&stop,&click})b->setEnabled(value);seek.setEnabled(value);bpm.setEnabled(value);}
    void updateMix(){if(!loaded)return;Array<var> mix;for(int i=0;i<tracks.size();++i)mix.add(json({{"gain",faders[i]->getValue()},{"pitch",0},{"bus","VS"}}));send(json({{"op","mix"},{"mix",mix},{"master",master.getValue()},{"click",click.getToggleState()},{"meter",4},{"tempos",Array<var>{json({{"time",0},{"bpm",bpm.getValue()}})}}}));}
    void chooseAudio(){
        picker=std::make_unique<FileChooser>(text("Escolha os stems de uma música","Choose a song's stems","Elige los stems de una canción"),File(),"*.wav;*.mp3;*.aif;*.aiff;*.flac",true);
        Component::SafePointer<Player> safe(this);
        picker->launchAsync(FileBrowserComponent::openMode|FileBrowserComponent::canSelectFiles|FileBrowserComponent::canSelectMultipleItems,[safe](const FileChooser& chooser){
            if(!safe)return;auto urls=chooser.getURLResults();if(urls.isEmpty())return;
            if(urls.size()>32){safe->status.setText(text("Selecione até 32 arquivos.","Select up to 32 files.","Selecciona hasta 32 archivos."),dontSendNotification);return;}
            safe->suspend();safe->open.setEnabled(false);safe->enableTransport(false);safe->status.setText(text("Preparando áudio…","Preparing audio…","Preparando audio…"),dontSendNotification);
            // iOS picker grants are consumed while copying on a worker, never by the audio callback.
            Thread::launch([safe,urls]{
                Array<File> files;auto folder=File::getSpecialLocation(File::userApplicationDataDirectory).getChildFile("ZLPlayMobile/Imports").getChildFile(Uuid().toString());auto ok=folder.createDirectory().wasOk();
                for(const auto& url:urls){
                    if(!ok)break;auto input=url.createInputStream(URL::InputStreamOptions(URL::ParameterHandling::inAddress));auto file=folder.getChildFile(String(files.size())).getChildFile(url.getFileName());if(file.getParentDirectory().createDirectory().failed()){ok=false;break;}FileOutputStream output(file);
                    if(!input||output.failedToOpen()){ok=false;break;}std::array<char,65536> block{};int64 total=0;
                    while(!input->isExhausted()){auto n=input->read(block.data(),int(block.size()));if(n<=0){ok=false;break;}total+=n;if(total>int64(1024)*1024*1024||!output.write(block.data(),size_t(n))){ok=false;break;}}output.flush();if(output.getStatus().failed()||total==0)ok=false;if(ok)files.add(file);
                }
                MessageManager::callAsync([safe,files,folder,ok]{if(!safe){folder.deleteRecursively();return;}safe->open.setEnabled(true);if(!ok){folder.deleteRecursively();safe->enableTransport(safe->loaded);safe->status.setText(text("Não foi possível importar os arquivos.","Could not import the files.","No se pudieron importar los archivos."),dontSendNotification);return;}safe->load(files);});
            });
        });
    }
    void load(const Array<File>& files){
        Array<var> requests;for(const auto& f:files)requests.add(json({{"token",f.getFileName()},{"path",f.getFullPathName()}}));auto metadata=send(json({{"op","probe"},{"files",requests}}));if(!metadata.isArray()||metadata.size()!=files.size())return;
        Array<var> next;double length=0;for(int i=0;i<files.size();++i){const double seconds=double(metadata[i]["duration"]);length=jmax(length,seconds);next.add(json({{"gain",1},{"pitch",0},{"bus","VS"},{"effectId","track:"+String(i)},{"items",Array<var>{json({{"path",files[i].getFullPathName()},{"position",0},{"offset",0},{"length",seconds},{"rate",1}})}}}));}
        loaded=false;auto state=send(json({{"op","load"},{"tracks",next},{"duration",length},{"sampleRate",44100},{"master",master.getValue()}}));if(!state.isObject())return;
        tracks=next;duration=length;loaded=true;playing=false;seek.setRange(0,jmax(.01,duration),.01);names.clear();faders.clear();
        for(const auto& file:files){auto* label=names.add(new Label());label->setText(file.getFileNameWithoutExtension(),dontSendNotification);strips.addAndMakeVisible(label);auto* slider=faders.add(new Slider());slider->setRange(0,2,.01);slider->setValue(1);slider->setName(label->getText());slider->setTextBoxStyle(Slider::TextBoxRight,false,65,30);slider->onValueChange=[this]{updateMix();};strips.addAndMakeVisible(slider);}
        enableTransport(true);status.setText(text("Áudio importado. Toque para iniciar.","Audio imported. Press Play to start.","Audio importado. Pulsa Reproducir."),dontSendNotification);resized();updateMix();
    }
    void timerCallback()override{
        if(!loaded)return;auto state=send(json({{"op","status"}}));if(!state.isObject())return;playing=bool(state["playing"]);if(!seek.isMouseButtonDown())seek.setValue(double(state["position"]),dontSendNotification);clock.setText(String(double(state["position"]),1)+" / "+String(duration,1)+" s",dontSendNotification);
        auto events=send(json({{"op","events"}}));if(events.isArray()&&!events.getArray()->isEmpty())status.setText(text("A reprodução encontrou um erro de áudio.","Playback encountered an audio error.","La reproducción encontró un error de audio."),dontSendNotification);
    }
};
class MobileApp final : public JUCEApplication {
public:
    const String getApplicationName()override{return "ZLPlay Mobile";}const String getApplicationVersion()override{return "0.1.0-dev";}
    void initialise(const String&)override{window=std::make_unique<Window>();}
    void shutdown()override{window.reset();}
    void suspended()override{if(window)window->player->suspend();}
    void systemRequestedQuit()override{quit();}
private:
    struct Window final : DocumentWindow {Player* player;
        Window():DocumentWindow("ZLPlay Mobile",Colour(0xff0d101c),DocumentWindow::allButtons){setUsingNativeTitleBar(true);player=new Player();setContentOwned(player,true);
#if JUCE_IOS
            setFullScreen(true);
#else
            setResizable(true,true);centreWithSize(900,650);
#endif
            setVisible(true);}
        void closeButtonPressed()override{JUCEApplication::getInstance()->systemRequestedQuit();}
    };std::unique_ptr<Window> window;
};
START_JUCE_APPLICATION(MobileApp)
