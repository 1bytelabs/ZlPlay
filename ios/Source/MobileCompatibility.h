// SPDX-License-Identifier: AGPL-3.0-only
#pragma once
#include "ProjectImport.h"
namespace mobile {
inline juce::StringArray keyChoices(){juce::StringArray result;for(int i=0;i<12;++i)result.add(flatKey(i));return result;}
inline int keyChoiceId(const juce::String& key){int value=note(key);return value<0?0:value+1;}
inline bool nonEmptyObject(const juce::var& value){return value.isObject()&&value.getDynamicObject()->getProperties().size()>0;}
inline juce::StringArray compatibilityWarnings(const juce::var& root){juce::StringArray result;auto settings=root["settings"];if(nonEmptyObject(settings["channelEffects"]))result.add("effects");auto arrangement=root["arrangement"];if(bool(arrangement.getProperty("enabled",true))&&arrangement["rules"].isArray()&&arrangement["rules"].size()>0)result.add("arrangement");if(root["padMarkers"].isArray()&&root["padMarkers"].size()>0)result.add("pad-markers");if(nonEmptyObject(settings["click"]))result.add("click");return result;}
inline bool needsCompatibilityNotice(const juce::var& receipts,const juce::String& project,const juce::String& revision){return project.isNotEmpty()&&revision.isNotEmpty()&&receipts.getProperty(juce::Identifier(project),juce::var()).toString()!=revision;}
}
