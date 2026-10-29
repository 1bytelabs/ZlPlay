// SPDX-License-Identifier: AGPL-3.0-only
#pragma once
#include "../Source/ProjectImport.h"
inline void projectImportCheck(const juce::File& wave) {
    using namespace juce;
    using namespace mobile;
    auto root=File::getSpecialLocation(File::tempDirectory).getChildFile("ZLPlay-Import-Check-"+Uuid().toString());
    struct Cleanup {File f;~Cleanup(){f.deleteRecursively();}} cleanup{root};
    auto song=root.getChildFile("Song");song.createDirectory();auto audio=song.getChildFile("Stem.wav");if(!wave.copyFileTo(audio))throw std::runtime_error("fixture copy");
    auto require=[](bool condition,const char* why){if(!condition)throw std::runtime_error(why);};
    auto rejects=[&](auto action,ImportError::Kind kind){try{action();}catch(const ImportError& e){require(e.kind==kind,"wrong validation error");return;}throw std::runtime_error("invalid input accepted");};
    auto rpp=song.getChildFile("Song - Original (Bb).rpp");rpp.replaceWithText("<REAPER_PROJECT\nTEMPO 100 4 4\nMARKER 1 0 \"Intro\" 9 0 R\nMARKER 1 2 \"\" 0\n<TRACK\nNAME \"Keys\"\nVOLPAN 0.7 -0.2\n<ITEM\nPOSITION 1\nSOFFS 0.25\nLENGTH 1\n<SOURCE WAVE\nFILE \"C:\\\\old\\\\Stem.wav\"\n>\n>\n>\n>\n");
    std::cout<<"RPP read\n";auto p=readProject(rpp,root);require(p.tracks.size()==1&&p.duration==2&&p.bpm==100,"RPP timing");require(p.originalKey=="Bb"&&p.targetKey=="Bb","RPP key");require(std::abs(double(p.tracks[0]["gain"])-.7)<1e-6,"RPP gain");require(double(p.tracks[0]["items"][0]["offset"])==.25,"RPP offset");require(p.peaks[0].size()==256,"RPP project peaks");require(p.regions.size()==1&&double(p.regions[0]["end"])==2,"RPP empty-name region closing");require(displayKey("C#m")=="Dbm"&&filenameKey("Song (Ebmin).rpp")=="Ebm","flat minor key display");
    auto* native=zlplay::createEngine();struct Destroy{zlplay::EmbeddedEngine* p;~Destroy(){zlplay::destroyEngine(p);}} destroy{native};
    auto command=[&](juce::var v){return zlplay::engineCommand(native,v);};
    auto make=[](std::initializer_list<std::pair<const char*,var>> fields){auto* o=new DynamicObject();for(auto& v:fields)o->setProperty(v.first,v.second);return var(o);};
    command(make({{"op","load"},{"offline",true},{"sampleRate",44100},{"duration",p.duration},{"tracks",p.tracks}}));
    auto gap=command(make({{"op","renderTest"},{"frames",4410},{"channels",2},{"position",0.}}));require(double(gap["rms"])<1e-8,"RPP leading gap renders silent");
    auto clip=command(make({{"op","renderTest"},{"frames",4410},{"channels",2},{"position",1.25}}));require(double(clip["rms"])>0,"RPP arranged clip renders PCM");

    auto other=song.getChildFile("Song (C).rpp");other.replaceWithText(rpp.loadFileAsString());require(projectFiles(root).size()==2,"all RPP alternatives retained");
    auto manifest=song.getChildFile("Song.zlplay");manifest.replaceWithText(R"({"format":"tom-certo-project","formatVersion":1,"song":{"title":"Test","bpm":110},"settings":{"originalKey":"Bb","lastTonalKey":"C"},"tracks":[{"name":"Keys","volume":0.5,"pan":0.2,"items":[{"file":"Stem.wav","position":0.5,"offset":0.25,"length":1}]}],"regions":[{"name":"Intro","start":0,"end":1}]})");
    std::cout<<"ZLPlay read\n";p=readProject(manifest,root);require(p.title=="Test"&&p.targetKey=="C"&&double(p.tracks[0]["pitch"])==2,"ZLPlay saved key");require(p.regions.size()==1&&p.duration==1.5,"ZLPlay regions");
    auto saved=JSON::parse(manifest.loadFileAsString());saved.getDynamicObject()->setProperty("arrangement",make({{"enabled",true},{"rules",Array<var>{}}}));saved.getDynamicObject()->setProperty("padMarkers",Array<var>{});manifest.replaceWithText(JSON::toString(saved));auto emptyDefaults=readProject(manifest,root);require(emptyDefaults.warnings.isEmpty(),"empty arrangement/pad metadata must not warn on import");saved["settings"].getDynamicObject()->setProperty("click",make({{"source","native"}}));manifest.replaceWithText(JSON::toString(saved));auto savedClick=readProject(manifest,root);require(savedClick.warnings.size()==1&&savedClick.warnings[0]=="click"&&savedClick.warningRevision.isNotEmpty(),"saved click has a specific revision-tracked notice");saved["settings"]["click"].getDynamicObject()->setProperty("accent",false);manifest.replaceWithText(JSON::toString(saved));require(readProject(manifest,root).warningRevision!=savedClick.warningRevision,"changed project metadata invalidates compatibility receipt");
    auto list=root.getChildFile("Set.zlset");list.replaceWithText(R"({"type":"zlplay-library","version":1,"songs":[{"id":"one","name":"First","folder":"Song","source":"Song.zlplay","tonalKey":"Db"},{"id":"two","name":"Missing","folder":"Absent"}],"order":["two","one"]})");std::cout<<"Setlist read\n";auto set=readSetlist(list,root);require(set.songs.size()==2&&set.songs[0].id=="two"&&set.songs[1].targetKey=="Db","setlist order and missing entry");
    std::cout<<"Path validation\n";rejects([&]{resolveFile(root,manifest,"../../outside.wav");},ImportError::invalid);
    std::cout<<"Path validation\n";rejects([&]{resolveFile(root,manifest,"Missing.wav");},ImportError::missing);
    auto duplicate=song.getChildFile("sub/Stem.wav");duplicate.getParentDirectory().createDirectory();wave.copyFileTo(duplicate);
    std::cout<<"Path validation\n";rejects([&]{resolveFile(root,manifest,"D:/old/Stem.wav");},ImportError::ambiguous);
    auto bad=song.getChildFile("Invalid.zlplay");bad.replaceWithText(R"({"format":"tom-certo-project","formatVersion":4,"song":{},"tracks":[]})");rejects([&]{readProject(bad,root);},ImportError::invalid);
    auto copy=root.getSiblingFile(root.getFileName()+"-Copy");Cleanup cleanupCopy{copy};std::cout<<"Copy folder\n";copyFolder(root,copy);MemoryBlock a,b;copy.getChildFile("Song/Stem.wav").loadFileAsData(a);audio.loadFileAsData(b);require(a==b,"copied audio unchanged");
}
