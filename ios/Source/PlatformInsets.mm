// SPDX-License-Identifier: AGPL-3.0-only
#include "PlatformInsets.h"
#import <UIKit/UIKit.h>
#include <stdexcept>
juce::BorderSize<int> mobileSafeInsets(juce::Component& component) {
    if(auto* peer=component.getPeer()) {
        UIView* view=(UIView*)peer->getNativeHandle();
        const auto insets=view.window.safeAreaInsets;
        return {juce::roundToInt(insets.top),juce::roundToInt(insets.left),juce::roundToInt(insets.bottom),juce::roundToInt(insets.right)};
    }
    return {};
}
void withMobileFolderAccess(juce::URL url,const std::function<void(const juce::File&)>& action) {
    @autoreleasepool {
        NSURL* location=nil;
        if(NSData* bookmark=(NSData*)getURLBookmark(url)) {
            BOOL stale=NO;NSError* error=nil;
            location=[NSURL URLByResolvingBookmarkData:bookmark options:0 relativeToURL:nil bookmarkDataIsStale:&stale error:&error];
            if(error)throw std::runtime_error("Folder permission unavailable");
        } else location=[NSURL URLWithString:[NSString stringWithUTF8String:url.toString(true).toRawUTF8()]];
        if(!location)throw std::runtime_error("Folder unavailable");
        BOOL granted=[location startAccessingSecurityScopedResource];
        try { action(juce::File(juce::String::fromUTF8(location.path.UTF8String))); }
        catch(...) {if(granted)[location stopAccessingSecurityScopedResource];throw;}
        if(granted)[location stopAccessingSecurityScopedResource];
    }
}
