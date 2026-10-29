// SPDX-License-Identifier: AGPL-3.0-only
#pragma once
#include <juce_core/juce_core.h>
namespace mobile {
// iOS may change the container UUID on install/restore. Persist only private relative paths.
inline juce::File privateFile(const juce::File& root,juce::String reference){if(reference.isEmpty())return {};if(juce::File::isAbsolutePath(reference)){juce::File existing(reference);if(existing.isAChildOf(root))return existing;const juce::String marker="/ZLPlayMobile/";if(!reference.contains(marker))return {};reference=reference.fromLastOccurrenceOf(marker,false,false);}auto parts=juce::StringArray::fromTokens(reference.replaceCharacter('\\','/'),"/","");for(auto& part:parts)if(part.isEmpty()||part=="."||part=="..")return {};auto file=root.getChildFile(parts.joinIntoString("/"));return file.isAChildOf(root)?file:juce::File();}
inline juce::String privateReference(const juce::File& root,const juce::File& file){return file.isAChildOf(root)?file.getRelativePathFrom(root):juce::String();}
}
