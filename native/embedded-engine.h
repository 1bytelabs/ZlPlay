// SPDX-License-Identifier: AGPL-3.0-only
#pragma once
#include <juce_core/juce_core.h>
namespace zlplay {
// One engine per app. Commands are serialized by the application control thread;
// rendering/streaming retain the native workers and callback from the desktop.
struct EmbeddedEngine;
EmbeddedEngine* createEngine();
void destroyEngine(EmbeddedEngine*);
juce::var engineCommand(EmbeddedEngine*, const juce::var&);
}
