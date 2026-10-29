// SPDX-License-Identifier: AGPL-3.0-only
#pragma once
#include <juce_gui_basics/juce_gui_basics.h>
#if JUCE_IOS
juce::BorderSize<int> mobileSafeInsets(juce::Component&);
#endif
#if JUCE_IOS
void withMobileFolderAccess(juce::URL, const std::function<void(const juce::File&)>&);
#endif
#if JUCE_IOS
void mobileKeepAwake(bool);
void mobileOpenAppSettings();
void mobileShowOutputPicker();
#endif
