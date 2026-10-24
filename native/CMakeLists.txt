cmake_minimum_required(VERSION 3.24)
project(ZlPlayAudio VERSION 2.8.0 LANGUAGES C CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
include(FetchContent)
set(JUCE_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(JUCE_BUILD_EXTRAS OFF CACHE BOOL "" FORCE)
FetchContent_Declare(JUCE
  URL https://codeload.github.com/juce-framework/JUCE/tar.gz/3fb5cbc0340758596dac39f7c8de47d562b4896e
  URL_HASH SHA256=2bb7095497a7bb7269dbdf841a7c6aa0939eaeb2a2bc9724ac4758d3ae3565a7
  DOWNLOAD_EXTRACT_TIMESTAMP FALSE) # JUCE 9.0.4, pinned revision and archive hash
FetchContent_MakeAvailable(JUCE)
juce_add_console_app(zlplay-audio PRODUCT_NAME "zlplay-audio" COMPANY_NAME "1Byte Labs" BUNDLE_ID "com.1bytelabs.zlplay.audio")
target_sources(zlplay-audio PRIVATE engine.cpp vendor/libebur128/ebur128.c)
if(APPLE)
  enable_language(OBJCXX)
  target_sources(zlplay-audio PRIVATE platform-macos.mm)
  find_library(APPKIT_FRAMEWORK AppKit REQUIRED)
  target_link_libraries(zlplay-audio PRIVATE ${APPKIT_FRAMEWORK})
endif()
target_include_directories(zlplay-audio PRIVATE vendor/libebur128 vendor/signalsmith-stretch-main vendor/linear-main/include)
target_compile_definitions(zlplay-audio PRIVATE
  JUCE_GLOBAL_MODULE_SETTINGS_INCLUDED=1 JUCE_STANDALONE_APPLICATION=1
  JUCE_WEB_BROWSER=0 JUCE_USE_CURL=0 JUCE_USE_WINDOWS_MEDIA_FORMAT=0
  JUCE_USE_MP3AUDIOFORMAT=1)
target_link_libraries(zlplay-audio PRIVATE juce::juce_audio_devices juce::juce_audio_formats juce::juce_recommended_config_flags)
if(MSVC)
  target_compile_options(zlplay-audio PRIVATE /utf-8)
endif()
if(WIN32)
  target_compile_definitions(zlplay-audio PRIVATE JUCE_ASIO=1 _USE_MATH_DEFINES)
  target_link_libraries(zlplay-audio PRIVATE psapi winmm)
endif()
set_target_properties(zlplay-audio PROPERTIES RUNTIME_OUTPUT_DIRECTORY "${CMAKE_CURRENT_SOURCE_DIR}/bin")
foreach(config DEBUG RELEASE RELWITHDEBINFO MINSIZEREL)
  set_target_properties(zlplay-audio PROPERTIES RUNTIME_OUTPUT_DIRECTORY_${config} "${CMAKE_CURRENT_SOURCE_DIR}/bin")
endforeach()
add_custom_command(TARGET zlplay-audio POST_BUILD
  COMMAND ${CMAKE_COMMAND} -E copy_if_different "${CMAKE_CURRENT_SOURCE_DIR}/../app/Meuclick.wav" "$<TARGET_FILE_DIR:zlplay-audio>/Meuclick.wav"
  COMMAND ${CMAKE_COMMAND} -E copy_if_different "${juce_SOURCE_DIR}/LICENSE.md" "$<TARGET_FILE_DIR:zlplay-audio>/JUCE-LICENSE.md"
  COMMAND ${CMAKE_COMMAND} -E copy_if_different "${CMAKE_CURRENT_SOURCE_DIR}/vendor/signalsmith-stretch-main/LICENSE.txt" "$<TARGET_FILE_DIR:zlplay-audio>/Signalsmith-Stretch-LICENSE.txt"
  COMMAND ${CMAKE_COMMAND} -E copy_if_different "${CMAKE_CURRENT_SOURCE_DIR}/vendor/linear-main/LICENSE.txt" "$<TARGET_FILE_DIR:zlplay-audio>/Signalsmith-Linear-LICENSE.txt"
  COMMAND ${CMAKE_COMMAND} -E copy_if_different "${CMAKE_CURRENT_SOURCE_DIR}/vendor/libebur128/COPYING" "$<TARGET_FILE_DIR:zlplay-audio>/libebur128-LICENSE.txt")
