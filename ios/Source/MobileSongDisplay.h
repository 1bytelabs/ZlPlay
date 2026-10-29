// SPDX-License-Identifier: AGPL-3.0-only
#pragma once
#include <juce_core/juce_core.h>
#include <regex>
namespace mobile {
// Presentation only; same patterns as Android SongDisplay at 4025815.
inline juce::String cleanSongName(juce::String raw){auto value=raw.replaceCharacter('_',' ').toStdString();
    for(auto pattern:{R"(\.(rpp|zlplay)$)",R"(\s+Tom\s+[A-G](?:#|b)?m?\s+BPM\s+\d+.*$)",R"(\s*(?:-|·)\s*[A-G](?:#|b)?m?\s*(?:-|·)\s*\d+(?:[.,]\d+)?\s*bpm.*$)",R"(\s*(?:-|·)\s*Original\s*(?:\([A-G](?:#|b)?m?\))?\s*$)"})value=std::regex_replace(value,std::regex(pattern,std::regex::icase),"");
    value=std::regex_replace(value,std::regex(R"(\s+)")," ");return juce::String::fromUTF8(value.c_str()).trim();}
}
