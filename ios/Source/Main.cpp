// SPDX-License-Identifier: AGPL-3.0-only
#include <juce_gui_extra/juce_gui_extra.h>
#include <juce_audio_formats/juce_audio_formats.h>
#include "embedded-engine.h"
#include <atomic>
#include "BinaryData.h"
#include "PlatformInsets.h"
#include "ProjectImport.h"
using namespace juce;
static var json(std::initializer_list<std::pair<const char*,var>> properties){auto* o=new DynamicObject();for(auto& p:properties)o->setProperty(p.first,p.second);return var(o);}
static String text(const char* pt,const char* en,const char* es){const auto l=SystemStats::getUserLanguage();return String::fromUTF8(l.startsWithIgnoreCase("pt")?pt:l.startsWithIgnoreCase("es")?es:en);}
class Timeline final : public Component {
public:
    Array<var> peaks,regions; StringArray names; double duration=0,position=0;
    std::function<void(double)> onSeek;
    void paint(Graphics& g) override {
        g.fillAll(Colour(0xff0a111a));g.setColour(Colour(0xff314054));g.drawRoundedRectangle(getLocalBounds().toFloat().reduced(.5f),8,1);
        auto r=getLocalBounds().reduced(10);const int left= jmin(100,r.getWidth()/4);const int top=r.getY()+28;
        if(names.isEmpty()){g.setColour(Colour(0xffa9b6cd));g.setFont(16);g.drawFittedText(text("Importe os áudios de uma música", "Import a song's audio files", "Importa los audios de una canción"),r,Justification::centred,2);return;}
        const int row=jlimit(28,58,(r.getHeight()-32)/jmax(1,names.size()));
        for(int i=0;i<names.size()&&top+i*row<r.getBottom();++i){const int y=top+i*row;g.setColour(Colour(0xffa9b6cd));g.setFont(12);g.drawFittedText(names[i],r.getX()+3,y,left-6,row,Justification::centredLeft,2);g.setColour(Colour(0xff263244));g.drawHorizontalLine(y+row,r.getX(),r.getRight());
            if(i<peaks.size()&&peaks[i].isArray()){auto* a=peaks[i].getArray();g.setColour(Colour(0xffa855f7));const float width=float(r.getWidth()-left);for(int b=0;b<a->size();++b){float x=r.getX()+left+width*b/a->size();float h=jmin(float(row-8),jmax(1.f,float(double((*a)[b]))*(row-8)));g.drawVerticalLine(roundToInt(x),y+row*.5f-h*.5f,y+row*.5f+h*.5f);}}
        }
        for(const auto& region:regions){if(duration<=0)break;float x=r.getX()+left+float(r.getWidth()-left)*float(double(region["start"])/duration);g.setColour(Colour(0xff64748b));g.drawVerticalLine(roundToInt(x),float(top),float(r.getBottom()));g.setFont(10);g.setColour(Colour(0xffc4b5fd));g.drawText(region["name"].toString(),int(x)+3,top,70,14,Justification::centredLeft);}
        g.setColour(Colour(0xffa9b6cd));g.setFont(11);for(int t=0;t<=4;++t){float x=r.getX()+left+float(r.getWidth()-left)*t/4;g.drawText(String(duration*t/4,1)+"s",int(x)-18,r.getY(),42,20,Justification::centred);}
        if(duration>0){float x=r.getX()+left+float(r.getWidth()-left)*float(position/duration);g.setColour(Colours::white);g.drawVerticalLine(roundToInt(x),float(top),float(r.getBottom()));}
    }
    void mouseDown(const MouseEvent& e)override{seek(e);}void mouseDrag(const MouseEvent& e)override{seek(e);}
private:
    void seek(const MouseEvent& e){auto r=getLocalBounds().reduced(10);int left=jmin(100,r.getWidth()/4);if(duration>0&&onSeek)onSeek(jlimit(0.,duration,(e.position.x-r.getX()-left)/jmax(1.,double(r.getWidth()-left))*duration));}
};
class Player final : public Component,private Timer {
public:
    Player():engine(zlplay::createEngine()){
        setOpaque(true);
        brand=ImageCache::getFromMemory(BinaryData::icon_png,BinaryData::icon_pngSize);
        File::getSpecialLocation(File::userDocumentsDirectory).createDirectory();
        title.setText("ZLPlay",dontSendNotification);title.setFont(FontOptions(25).withStyle("Bold"));addAndMakeVisible(title);
        status.setText(text("Abra arquivos de áudio para começar.","Open audio files to begin.","Abre archivos de audio para empezar."),dontSendNotification);addAndMakeVisible(status);
        open.setButtonText(text("Abrir","Open","Abrir"));play.setButtonText(text("Tocar","Play","Reproducir"));pause.setButtonText(text("Pausar","Pause","Pausar"));stop.setButtonText(text("Parar","Stop","Detener"));click.setButtonText("Click");
        for(auto* b:{&open,&play,&pause,&stop,&click}){addAndMakeVisible(*b);b->setColour(TextButton::buttonColourId,Colour(0xff272039));b->setColour(TextButton::buttonOnColourId,Colour(0xff9418dd));}
        open.onClick=[this]{PopupMenu menu;menu.addItem(1,text("Pasta de projeto / repertório","Project / setlist folder","Carpeta de proyecto / repertorio"));menu.addItem(2,text("Áudios avulsos","Audio files","Audios sueltos"));
#if JUCE_DEBUG
        menu.addItem(3,text("Teste sintético","Synthetic test","Prueba sintética"));
#endif
        Component::SafePointer<Player> safe(this);menu.showMenuAsync(PopupMenu::Options().withTargetComponent(&open),[safe](int choice){if(!safe)return;if(choice==1)safe->chooseFolder();if(choice==2)safe->chooseAudio();
#if JUCE_DEBUG
        if(choice==3)safe->fixture.triggerClick();
#endif
        });};play.onClick=[this]{if(playing)send(json({{"op","pause"}}));else startPlayback();};pause.onClick=[this]{send(json({{"op","pause"}}));};stop.onClick=[this]{send(json({{"op","stop"}}));};
        mixer.setButtonText("Mixer");addAndMakeVisible(mixer);mixer.onClick=[this]{showMixer=!showMixer;resized();};
        help.setButtonText("?");addAndMakeVisible(help);help.onClick=[this]{AlertWindow::showMessageBoxAsync(MessageBoxIconType::InfoIcon,text("Importar no iPhone/iPad","Import on iPhone/iPad","Importar en iPhone/iPad"),text("Selecione WAV, MP3, AIFF ou FLAC no app Arquivos. No simulador, as pastas do Mac não aparecem automaticamente: copie os áudios para a pasta Documents do app simulado. Para projetos RPP/ZLPlay ou repertórios ZLSET, escolha Abrir > Pasta e selecione a pasta que contém os projetos e seus áudios. Os originais serão preservados.","Select WAV, MP3, AIFF or FLAC in Files. Simulator does not automatically show Mac folders: copy audio into the simulated app Documents folder. For RPP/ZLPlay projects or ZLSET setlists, choose Open > Folder and select the folder containing the projects and their audio. Originals are preserved.","Selecciona WAV, MP3, AIFF o FLAC en Archivos. El simulador no muestra automáticamente las carpetas del Mac: copia los audios a Documents de la app simulada. Para proyectos RPP/ZLPlay o repertorios ZLSET, elige Abrir > Carpeta y selecciona la carpeta con los proyectos y sus audios. Se conservan los originales."));};
        timeline.onSeek=[this](double p){send(json({{"op","seek"},{"position",p},{"playing",playing}}));};addAndMakeVisible(timeline);
        song.setText(text("Nenhuma música aberta","No song open","Ninguna canción abierta"),dontSendNotification);song.setFont(FontOptions(17).withStyle("Bold"));addAndMakeVisible(song);
        addAndMakeVisible(library);library.setTextWhenNothingSelected(text("Repertório","Setlist","Repertorio"));library.onChange=[this]{if(library.getSelectedId()>0)requestSong(library.getSelectedId()-1);};addAndMakeVisible(nextSong);
        click.setClickingTogglesState(true);click.onClick=[this]{updateMix();};
        master.setRange(0,2,.01);master.setValue(1);master.setTextBoxStyle(Slider::TextBoxRight,false,65,30);master.setName("Master");master.onValueChange=[this]{send(json({{"op","master"},{"value",master.getValue()}}));};addAndMakeVisible(master);masterLabel.setText("Master",dontSendNotification);addAndMakeVisible(masterLabel);
        seek.setRange(0,1,.01);seek.setTextBoxStyle(Slider::NoTextBox,false,0,0);seek.setName(text("Posição","Position","Posición"));seek.onDragEnd=[this]{send(json({{"op","seek"},{"position",seek.getValue()},{"playing",playing}}));};addAndMakeVisible(seek);
        bpm.setRange(20,400,1);bpm.setValue(120);bpm.setTextBoxStyle(Slider::TextBoxRight,false,65,30);bpm.setName("BPM");bpm.onValueChange=[this]{updateMix();};addAndMakeVisible(bpm);bpmLabel.setText(String::fromUTF8("BPM · Click"),dontSendNotification);addAndMakeVisible(bpmLabel);
        addAndMakeVisible(clock);view.setViewedComponent(&strips,false);view.setScrollBarsShown(true,false);addAndMakeVisible(view);
#if JUCE_DEBUG
        fixture.setButtonText(text("Teste sintético", "Synthetic test", "Prueba sintética"));
        fixture.onClick=[this]{
            fixture.setEnabled(false);Component::SafePointer<Player> safe(this);
            Thread::launch([safe]{
                auto folder=File::getSpecialLocation(File::userDocumentsDirectory).getChildFile("ZLPlay Examples");folder.createDirectory();auto file=folder.getChildFile("Synthetic-"+Uuid().toString()+".wav");
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
    void paint(Graphics& g)override{
        g.fillAll(Colour(0xff090f17));auto r=safeBounds();if(brand.isValid())g.drawImageWithin(brand,r.getX(),r.getY()+3,32,32,RectanglePlacement::centred);
        g.setColour(Colour(0xff16212e));g.fillRoundedRectangle(song.getBounds().toFloat().expanded(4,4),6);
    }
    Rectangle<int> safeBounds(){
        auto r=getLocalBounds();
#if JUCE_IOS
        r=mobileSafeInsets(*this).subtractedFrom(r);
#endif
        return r.reduced(12);
    }
    void resized()override{
        auto r=safeBounds();auto header=r.removeFromTop(40);header.removeFromLeft(38);title.setBounds(header.removeFromLeft(100));help.setBounds(header.removeFromRight(34).reduced(2));mixer.setBounds(header.removeFromRight(68).reduced(2));open.setBounds(header.reduced(2));
        library.setVisible(!setlist.songs.isEmpty());nextSong.setVisible(!setlist.songs.isEmpty());if(!setlist.songs.isEmpty()){auto queueRow=r.removeFromTop(30);library.setBounds(queueRow.removeFromLeft(queueRow.getWidth()/2).reduced(2));nextSong.setBounds(queueRow.reduced(3));}
        r.removeFromTop(4);song.setBounds(r.removeFromTop(getHeight()<500?28:42).reduced(4));r.removeFromTop(4);
        auto footer=r.removeFromBottom(20);status.setBounds(footer);
        auto transport=r.removeFromBottom(44);auto w=jmin(70,transport.getWidth()/8);play.setBounds(transport.removeFromLeft(w).reduced(2));pause.setVisible(false);stop.setBounds(transport.removeFromLeft(w).reduced(2));click.setBounds(transport.removeFromLeft(w).reduced(2));clock.setBounds(transport.removeFromLeft(100).reduced(4));bpmLabel.setBounds(transport.removeFromLeft(85));bpm.setBounds(transport);
        seek.setBounds(r.removeFromBottom(28));r.removeFromBottom(5);
        bool wide=getWidth()>=700;bool mixerVisible=wide?!showMixer:showMixer;mixer.setToggleState(mixerVisible,dontSendNotification);view.setVisible(mixerVisible);master.setVisible(mixerVisible);masterLabel.setVisible(mixerVisible);if(mixerVisible){auto mixArea=wide?r.removeFromRight(jmin(300,r.getWidth()/3)):r.removeFromBottom(jmin(210,jmax(70,r.getHeight()/2)));auto masterRow=mixArea.removeFromBottom(38);masterLabel.setBounds(masterRow.removeFromLeft(60));master.setBounds(masterRow);view.setBounds(mixArea);if(wide)r.removeFromRight(8);else r.removeFromBottom(8);}timeline.setBounds(r);
        strips.setSize(jmax(1,view.getWidth()-16),jmax(1,int(faders.size())*62));
        for(int i=0;i<int(faders.size());++i){names[i]->setBounds(6,i*62,jmax(1,strips.getWidth()-12),23);faders[i]->setBounds(6,i*62+24,jmax(1,strips.getWidth()-12),36);}
        repaint();
    }
    void suspend(){if(engine){send(json({{"op","pause"}}));send(json({{"op","aux"},{"padEnabled",false},{"padImmediateStop",true},{"soloEnabled",false},{"soloImmediateStop",true},{"offline",true}}));}}
private:
    zlplay::EmbeddedEngine* engine;Image brand;Timeline timeline;Label title,status,clock,masterLabel,bpmLabel,song,nextSong;ComboBox library;mobile::Setlist setlist;File importRoot;int projectMeter=4;bool importing=false;TextButton open,play,pause,stop,click,mixer,help;bool showMixer=false,deviceReady=false;Rectangle<int> lastSafeArea;
#if JUCE_DEBUG
    TextButton fixture;
#endif
Slider master,seek,bpm;Viewport view;Component strips;OwnedArray<Label> names;OwnedArray<Slider> faders;std::unique_ptr<FileChooser> picker;Array<var> tracks;bool loaded=false,playing=false;double duration=0;
    var send(var request){try{return zlplay::engineCommand(engine,request);}catch(const std::exception& error){Logger::writeToLog(String::fromUTF8(error.what()));status.setText(text("Não foi possível concluir. Confira os arquivos e a saída de áudio.","Could not complete. Check files and audio output.","No se pudo completar. Revisa archivos y salida de audio."),dontSendNotification);return {};}}
    void enableTransport(bool value){for(auto* b:{&play,&pause,&stop,&click})b->setEnabled(value);seek.setEnabled(value);bpm.setEnabled(value);}
    void updateMix(){if(!loaded)return;Array<var> mix;for(int i=0;i<tracks.size();++i)mix.add(json({{"gain",faders[i]->getValue()},{"pan",double(tracks[i]["pan"])},{"pitch",double(tracks[i]["pitch"])},{"bus",tracks[i]["bus"]}}));send(json({{"op","mix"},{"mix",mix},{"master",master.getValue()},{"click",click.getToggleState()},{"meter",projectMeter},{"tempos",Array<var>{json({{"time",0},{"bpm",bpm.getValue()}})}}}));}
    void startPlayback(){
        const double at=seek.getValue();if(!deviceReady){auto state=send(json({{"op","load"},{"tracks",tracks},{"duration",duration},{"sampleRate",44100},{"master",master.getValue()}}));if(!state.isObject())return;deviceReady=true;updateMix();}send(json({{"op","play"},{"position",at}}));
    }
    void setImporting(bool value){
#if JUCE_DEBUG
        fixture.setEnabled(!value);
#endif
        importing=value;open.setEnabled(!value);library.setEnabled(!value);enableTransport(!value&&loaded);if(value)status.setText(text("Preparando projeto…","Preparing project…","Preparando proyecto…"),dontSendNotification);}
    void importError(const mobile::ImportError& error){
        String message;
        switch(error.kind){
            case mobile::ImportError::missing:message=text("Arquivo ou áudio não encontrado: ","File or audio not found: ","Archivo o audio no encontrado: ");break;
            case mobile::ImportError::ambiguous:message=text("Há mais de um arquivo correspondente. Escolha uma pasta sem referências ambíguas: ","Multiple files match. Choose a folder without ambiguous references: ","Hay varios archivos coincidentes. Elige una carpeta sin referencias ambiguas: ");break;
            case mobile::ImportError::unsupported:message=text("Este projeto usa processamento ainda não compatível. Renderize esses trechos em áudio antes de importar. ","This project uses unsupported processing. Render those parts to audio before importing. ","Este proyecto usa procesamiento aún no compatible. Renderiza esas partes a audio antes de importar. ");break;
            case mobile::ImportError::limit:message=text("A pasta ou projeto excede os limites desta versão.","The folder or project exceeds this version's limits.","La carpeta o proyecto supera los límites de esta versión.");break;
            case mobile::ImportError::access:message=text("Não foi possível ler a pasta selecionada.","Could not read the selected folder.","No se pudo leer la carpeta seleccionada.");break;
            default:message=text("Projeto inválido ou incompleto. Selecione a pasta com os áudios originais.","Invalid or incomplete project. Select the folder with the original audio.","Proyecto inválido o incompleto. Selecciona la carpeta con los audios originales.");
        }
        status.setText(message+error.detail,dontSendNotification);AlertWindow::showMessageBoxAsync(MessageBoxIconType::WarningIcon,text("Importação","Import","Importación"),message+error.detail);
    }
    void chooseFolder(){
        if(importing)return;picker=std::make_unique<FileChooser>(text("Pasta com os projetos e áudios","Folder containing projects and audio","Carpeta con proyectos y audios"),File::getSpecialLocation(File::userDocumentsDirectory),"*",true);Component::SafePointer<Player> safe(this);
        picker->launchAsync(FileBrowserComponent::openMode|FileBrowserComponent::canSelectDirectories,[safe](const FileChooser& chooser){if(!safe)return;auto urls=chooser.getURLResults();if(urls.isEmpty())return;safe->suspend();safe->setImporting(true);auto url=urls[0];
            Thread::launch([safe,url]{auto destination=File::getSpecialLocation(File::userApplicationDataDirectory).getChildFile("ZLPlayMobile/Projects").getChildFile(Uuid().toString());try{
#if JUCE_IOS
                withMobileFolderAccess(url,[&](const File& source){mobile::copyFolder(source,destination);});
#else
                mobile::copyFolder(url.getLocalFile(),destination);
#endif
                auto files=mobile::projectFiles(destination);if(files.isEmpty())throw mobile::ImportError(mobile::ImportError::missing);MessageManager::callAsync([safe,destination,files]{if(!safe)return;safe->setImporting(false);safe->importRoot=destination;safe->setlist={};safe->library.clear(dontSendNotification);safe->resized();safe->status.setText(text("Escolha o projeto ou repertório.","Choose a project or setlist.","Elige un proyecto o repertorio."),dontSendNotification);safe->chooseProjectFile(files,{});});
            }catch(const mobile::ImportError& e){destination.deleteRecursively();MessageManager::callAsync([safe,e]{if(safe){safe->setImporting(false);safe->importError(e);}});}catch(...){destination.deleteRecursively();MessageManager::callAsync([safe]{if(safe){safe->setImporting(false);safe->importError(mobile::ImportError(mobile::ImportError::access));}});}});
        });
    }
    void chooseProjectFile(const Array<File>& files,const mobile::Song& entry){
        if(files.size()==1){openProjectFile(files[0],entry);return;}if(files.isEmpty()){importError(mobile::ImportError(mobile::ImportError::missing,entry.name));return;}PopupMenu menu;for(int i=0;i<files.size();++i){auto label=files[i].getRelativePathFrom(importRoot);auto key=mobile::filenameKey(files[i].getFileName());if(key.isNotEmpty())label+=String::fromUTF8(" · ")+key;menu.addItem(i+1,label);}Component::SafePointer<Player> safe(this);menu.showMenuAsync(PopupMenu::Options().withTargetComponent(&open),[safe,files,entry](int choice){if(safe&&choice>0)safe->openProjectFile(files[choice-1],entry);});
    }
    void openProjectFile(const File& file,const mobile::Song& entry){
        if(file.hasFileExtension("zlset;zlplaylibrary")){try{setlist=mobile::readSetlist(file,importRoot);library.clear(dontSendNotification);for(int i=0;i<setlist.songs.size();++i)library.addItem(setlist.songs[i].name,i+1);library.setTextWhenNothingSelected(setlist.title);status.setText(text("Escolha uma música do repertório.","Choose a song from the setlist.","Elige una canción del repertorio."),dontSendNotification);resized();}catch(const mobile::ImportError& e){importError(e);}return;}
        suspend();setImporting(true);Component::SafePointer<Player> safe(this);auto root=importRoot;
        Thread::launch([safe,file,root,entry]{try{auto project=mobile::readProject(file,root);if(mobile::note(entry.originalKey)>=0)project.originalKey=entry.originalKey;if(mobile::note(entry.targetKey)>=0){project.targetKey=entry.targetKey;int shift=(mobile::note(project.targetKey)-mobile::note(project.originalKey)+18)%12-6;if(mobile::note(project.originalKey)>=0)for(auto& t:project.tracks)if(bool(t["pitchEnabled"]))t.getDynamicObject()->setProperty("pitch",shift);}
            MessageManager::callAsync([safe,project]{if(!safe)return;safe->setImporting(false);if(project.warnings.isEmpty()){safe->applyProject(project);return;}AlertWindow::showOkCancelBox(MessageBoxIconType::WarningIcon,text("Compatibilidade do projeto","Project compatibility","Compatibilidad del proyecto"),text("O projeto contém efeitos, automações ou ajustes que esta versão ainda não reproduz integralmente. Continuar com os áudios e posições importados? Os arquivos originais não serão alterados.","The project contains effects, automation or settings this version does not fully reproduce yet. Continue with the imported audio and positions? Original files will not be changed.","El proyecto contiene efectos, automatizaciones o ajustes que esta versión aún no reproduce por completo. ¿Continuar con los audios y posiciones importados? No se modificarán los originales."),text("Continuar","Continue","Continuar"),text("Cancelar","Cancel","Cancelar"),nullptr,ModalCallbackFunction::create([safe,project](int result){if(safe&&result)safe->applyProject(project);}));});
        }catch(const mobile::ImportError& e){MessageManager::callAsync([safe,e]{if(safe){safe->setImporting(false);safe->importError(e);}});}catch(...){MessageManager::callAsync([safe]{if(safe){safe->setImporting(false);safe->importError(mobile::ImportError(mobile::ImportError::invalid));}});}});
    }
    void requestSong(int index){if(index<0||index>=setlist.songs.size()||importing)return;suspend();auto entry=setlist.songs[index];auto dir=File(entry.folder);try{Array<File> files;if(entry.source.isNotEmpty()){File dummy=dir.getChildFile("setlist-reference.zlplay");files.add(mobile::resolveFile(importRoot,dummy,entry.source));}else for(auto& f:mobile::projectFiles(dir))if(f.hasFileExtension("rpp;zlplay"))files.add(f);if(index+1<setlist.songs.size())nextSong.setText(text("Próxima: ","Next: ","Próxima: ")+setlist.songs[index+1].name,dontSendNotification);else nextSong.setText(text("Fim do repertório","End of setlist","Fin del repertorio"),dontSendNotification);chooseProjectFile(files,entry);}catch(const mobile::ImportError& e){importError(e);}}
    void applyProject(const mobile::Project& project){
        loaded=false;deviceReady=false;auto state=send(json({{"op","load"},{"tracks",project.tracks},{"duration",project.duration},{"sampleRate",44100},{"offline",true},{"master",master.getValue()}}));if(!state.isObject())return;tracks=project.tracks;duration=project.duration;loaded=true;playing=false;projectMeter=project.meter;bpm.setValue(project.bpm,dontSendNotification);seek.setRange(0,jmax(.01,duration),.01);seek.setValue(0,dontSendNotification);names.clear();faders.clear();
        for(int i=0;i<project.names.size();++i){auto* name=names.add(new Label());name->setText(project.names[i],dontSendNotification);strips.addAndMakeVisible(name);auto* fader=faders.add(new Slider());fader->setRange(0,8,.01);fader->setValue(double(tracks[i]["gain"]));fader->setName(project.names[i]);fader->setTextBoxStyle(Slider::TextBoxRight,false,65,30);fader->onValueChange=[this]{updateMix();};strips.addAndMakeVisible(fader);}
        timeline.names=project.names;timeline.peaks=project.peaks;timeline.regions=project.regions;timeline.duration=duration;timeline.position=0;auto heading=project.title;if(mobile::note(project.originalKey)>=0)heading+=String::fromUTF8(" · ")+text("Original ","Original ","Original ")+mobile::displayKey(project.originalKey);if(mobile::note(project.targetKey)>=0)heading+=String::fromUTF8(" · ")+text("Tocar em ","Play in ","Tocar en ")+mobile::displayKey(project.targetKey);song.setText(heading,dontSendNotification);enableTransport(true);status.setText(text("Projeto importado. Toque para iniciar.","Project imported. Press Play to start.","Proyecto importado. Pulsa Reproducir."),dontSendNotification);resized();updateMix();
    }
    void chooseAudio(){
        picker=std::make_unique<FileChooser>(text("Escolha os stems de uma música","Choose a song's stems","Elige los stems de una canción"),File::getSpecialLocation(File::userDocumentsDirectory),"*.wav;*.mp3;*.aif;*.aiff;*.flac",true);
        Component::SafePointer<Player> safe(this);
        picker->launchAsync(FileBrowserComponent::openMode|FileBrowserComponent::canSelectFiles|FileBrowserComponent::canSelectMultipleItems,[safe](const FileChooser& chooser){
            if(!safe)return;auto urls=chooser.getURLResults();if(urls.isEmpty())return;
            if(urls.size()>32){safe->status.setText(text("Selecione até 32 arquivos.","Select up to 32 files.","Selecciona hasta 32 archivos."),dontSendNotification);return;}
            safe->suspend();safe->open.setEnabled(false);safe->enableTransport(false);safe->status.setText(text("Preparando áudio…","Preparing audio…","Preparando audio…"),dontSendNotification);
            // iOS picker grants are consumed while copying on a worker, never by the audio callback.
            Thread::launch([safe,urls]{
                Array<File> files;auto folder=File::getSpecialLocation(File::userApplicationDataDirectory).getChildFile("ZLPlayMobile/Imports").getChildFile(Uuid().toString());auto ok=folder.createDirectory().wasOk();
                for(const auto& url:urls){
                    if(!ok)break;auto input=url.createInputStream(URL::InputStreamOptions(URL::ParameterHandling::inAddress));auto file=folder.getChildFile(String(files.size())).getChildFile(File::createLegalFileName(URL::removeEscapeChars(url.getFileName())));if(file.getParentDirectory().createDirectory().failed()){ok=false;break;}FileOutputStream output(file);
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
        loaded=false;deviceReady=false;auto state=send(json({{"op","load"},{"tracks",next},{"duration",length},{"sampleRate",44100},{"offline",true},{"master",master.getValue()}}));if(!state.isObject())return;
        setlist={};library.clear(dontSendNotification);projectMeter=4;timeline.regions.clear();tracks=next;duration=length;loaded=true;playing=false;seek.setRange(0,jmax(.01,duration),.01);names.clear();faders.clear();
        for(const auto& file:files){auto* label=names.add(new Label());label->setText(file.getFileNameWithoutExtension(),dontSendNotification);strips.addAndMakeVisible(label);auto* slider=faders.add(new Slider());slider->setRange(0,2,.01);slider->setValue(1);slider->setName(label->getText());slider->setTextBoxStyle(Slider::TextBoxRight,false,65,30);slider->onValueChange=[this]{updateMix();};strips.addAndMakeVisible(slider);}
        timeline.names.clear();timeline.peaks.clear();for(const auto& file:files){timeline.names.add(file.getFileNameWithoutExtension());timeline.peaks.add(send(json({{"op","peaks"},{"path",file.getFullPathName()},{"bins",256}})));}timeline.duration=duration;timeline.position=0;timeline.repaint();song.setText(files.size()==1?files[0].getFileNameWithoutExtension():text("Áudios importados","Imported audio","Audios importados")+" ("+String(files.size())+")",dontSendNotification);
        enableTransport(true);status.setText(text("Áudio importado. Toque para iniciar.","Audio imported. Press Play to start.","Audio importado. Pulsa Reproducir."),dontSendNotification);resized();updateMix();
    }
    void timerCallback()override{
        auto area=safeBounds();if(area!=lastSafeArea){lastSafeArea=area;resized();}
        if(!loaded||importing)return;auto state=send(json({{"op","status"}}));if(!state.isObject())return;playing=bool(state["playing"]);play.setButtonText(playing?text("Pausar","Pause","Pausar"):text("Tocar","Play","Reproducir"));play.setToggleState(playing,dontSendNotification);timeline.position=double(state["position"]);timeline.repaint();if(!seek.isMouseButtonDown())seek.setValue(double(state["position"]),dontSendNotification);clock.setText(String(double(state["position"]),1)+" / "+String(duration,1)+" s",dontSendNotification);
        auto events=send(json({{"op","events"}}));if(events.isArray()&&!events.getArray()->isEmpty())status.setText(text("A reprodução encontrou um erro de áudio.","Playback encountered an audio error.","La reproducción encontró un error de audio."),dontSendNotification);
    }
};
class MobileApp final : public JUCEApplication {
public:
    const String getApplicationName()override{return "ZLPlay Mobile";}const String getApplicationVersion()override{return "0.1.0-dev";}
    void initialise(const String&)override{
#if JUCE_IOS
        Desktop::getInstance().setOrientationsEnabled(Desktop::rotatedClockwise|Desktop::rotatedAntiClockwise);
#endif
        window=std::make_unique<Window>();
    }
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
