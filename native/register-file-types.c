// SPDX-License-Identifier: AGPL-3.0-only
#include <CoreServices/CoreServices.h>
#include <stdio.h>
#include <string.h>
int main(int argc, char **argv) {
    if (argc != 2 || argv[1][0] != '/') return 2;
    CFURLRef url = CFURLCreateFromFileSystemRepresentation(NULL, (const UInt8 *)argv[1], strlen(argv[1]), true);
    if (!url) return 2;
    OSStatus status = LSRegisterURL(url, true);
    CFRelease(url);
    if (status != noErr) { fprintf(stderr, "ZLPlay file registration: %d\n", (int)status); return 1; }
    const CFStringRef types[] = {CFSTR("com.1bytelabs.zlplay.project"), CFSTR("com.1bytelabs.zlplay.setlist")};
    for (int i = 0; i < 2; ++i) {
        status = LSSetDefaultRoleHandlerForContentType(types[i], kLSRolesAll, CFSTR("com.1bytelabs.zlplay"));
        if (status != noErr) { fprintf(stderr, "ZLPlay file association: %d\n", (int)status); return 1; }
    }
    return 0;
}
