#pragma once
#include <cstdint>
#include <utility>

#if defined(_WIN32)
 #ifndef NOMINMAX
  #define NOMINMAX
 #endif
 #include <windows.h>
 #include <psapi.h>
 #include <mmsystem.h>
 #include <intrin.h>
#elif defined(__APPLE__)
 #include <mach/mach.h>
 #if defined(__x86_64__)
  #include <cpuid.h>
 #endif
#endif

// A console main bypasses JUCEApplicationBase::main, which normally creates
// NSApplication on macOS. The AppKit application must exist before JUCE installs
// its delegate/message queue or runDispatchLoop() sends run to a nil NSApp.
#if defined(__APPLE__)
void initialisePlatformApplication();
#else
inline void initialisePlatformApplication() {}
#endif

inline bool beginPlatformTimer() {
 #if defined(_WIN32)
  return timeBeginPeriod(1) == TIMERR_NOERROR;
 #else
  return false;
 #endif
}
inline void endPlatformTimer(bool active) {
 #if defined(_WIN32)
  if (active) timeEndPeriod(1);
 #else
  (void) active;
 #endif
}
inline std::pair<std::uint64_t, std::uint64_t> platformMemory() {
 #if defined(_WIN32)
  PROCESS_MEMORY_COUNTERS_EX m{};m.cb=sizeof(m);
  if (GetProcessMemoryInfo(GetCurrentProcess(),reinterpret_cast<PROCESS_MEMORY_COUNTERS*>(&m),sizeof(m))) return {m.WorkingSetSize,m.PrivateUsage};
 #elif defined(__APPLE__)
  mach_task_basic_info_data_t m{};mach_msg_type_number_t size=MACH_TASK_BASIC_INFO_COUNT;
  if (task_info(mach_task_self(),MACH_TASK_BASIC_INFO,reinterpret_cast<task_info_t>(&m),&size)==KERN_SUCCESS) return {m.resident_size,0};
 #endif
 return {0,0}; // Zero means this metric is unavailable, not an estimate.
}
inline bool platformHasF16C() {
 #if defined(_MSC_VER) && (defined(_M_X64) || defined(_M_IX86))
  int flags[4]{};__cpuid(flags,1);return (flags[2] & (1<<29)) != 0;
 #elif defined(__APPLE__) && defined(__x86_64__)
  unsigned a=0,b=0,c=0,d=0;return __get_cpuid(1,&a,&b,&c,&d) && (c & (1u<<29));
 #else
  return false; // ARM Macs use the native baseline whisper build.
 #endif
}
