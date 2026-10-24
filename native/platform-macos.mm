// SPDX-License-Identifier: AGPL-3.0-only
#import <AppKit/AppKit.h>
#include "platform-support.h"

void initialisePlatformApplication() {
    @autoreleasepool {
        [NSApplication sharedApplication];
        // This is the Electron app's background audio worker, not a second GUI.
        [NSApp setActivationPolicy:NSApplicationActivationPolicyProhibited];
    }
}
