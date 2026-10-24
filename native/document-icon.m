// SPDX-License-Identifier: AGPL-3.0-only
#import <AppKit/AppKit.h>
int main(int argc,const char **argv){
  @autoreleasepool{
    if(argc!=3)return 2;
    NSImage *icon=[[NSImage alloc] initWithContentsOfFile:[NSString stringWithUTF8String:argv[1]]];
    if(!icon)return 3;
    return [[NSWorkspace sharedWorkspace] setIcon:icon forFile:[NSString stringWithUTF8String:argv[2]] options:0]?0:1;
  }
}
