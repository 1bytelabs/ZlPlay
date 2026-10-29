// SPDX-License-Identifier: AGPL-3.0-only
#pragma once
#include <juce_audio_formats/juce_audio_formats.h>
#include <stdexcept>
namespace mobile {
using namespace juce;
struct ImportError : std::runtime_error {
    enum Kind { invalid, missing, unsupported, limit, access, ambiguous } kind;
    String detail;
    ImportError(Kind k, String d={}) : std::runtime_error("Mobile import failed"),kind(k),detail(d) {}
};
struct Project {
    String title,originalKey,targetKey,warningRevision; double bpm=120,duration=0; int meter=4;
    Array<var> tracks,regions; StringArray names,warnings; Array<var> peaks;
};
struct Song { String id,name,folder,source,originalKey,targetKey; };
struct Setlist {String title; Array<Song> songs;};
File copyFolder(const File& source,const File& destination);
Array<File> projectFiles(const File& root);
inline Array<File> importCandidates(const Array<File>& files,bool repertoireOnly){Array<File> result;for(auto& file:files)if(file.hasFileExtension(repertoireOnly?"zlset;zlplaylibrary":"rpp;zlplay"))result.add(file);return result;}
File resolveFile(const File& root,const File& manifest,const String& reference);
Project readProject(const File& manifest,const File& root);
Setlist readSetlist(const File& manifest,const File& root);
int note(const String& key);
String flatKey(int note);
String displayKey(const String& key);
String filenameKey(const String& filename);
}
