$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$compilerRoot = (Get-ChildItem (Join-Path $projectRoot 'tools/msvc/VC/Tools/MSVC') -Directory | Sort-Object Name -Descending | Select-Object -First 1).FullName
$compilerExe = Join-Path $compilerRoot 'bin/Hostx64/x64/cl.exe'
$sdkRoot = Join-Path $projectRoot 'tools/msvc/Windows Kits/10'
$sdkVersion = (Get-ChildItem (Join-Path $sdkRoot 'Include') -Directory | Sort-Object Name -Descending | Select-Object -First 1).Name
$modules = Join-Path $projectRoot 'tools/juce-build/JUCE-9.0.4/modules'
$env:PATH = (Join-Path $compilerRoot 'bin/Hostx64/x64') + ';' + $env:PATH
$env:INCLUDE = (Join-Path $compilerRoot 'include') + ';' + (Join-Path $sdkRoot "Include/$sdkVersion/ucrt") + ';' + (Join-Path $sdkRoot "Include/$sdkVersion/shared") + ';' + (Join-Path $sdkRoot "Include/$sdkVersion/um") + ';' + (Join-Path $sdkRoot "Include/$sdkVersion/winrt")
$env:LIB = (Join-Path $compilerRoot 'lib/x64') + ';' + (Join-Path $sdkRoot "Lib/$sdkVersion/ucrt/x64") + ';' + (Join-Path $sdkRoot "Lib/$sdkVersion/um/x64")
$out = Join-Path $PSScriptRoot 'bin'
$objects = Join-Path $projectRoot 'tools/native-objects-asio'
New-Item -ItemType Directory -Force $out,$objects | Out-Null
$flags = @('/nologo','/std:c++17','/O2','/EHsc','/MT','/utf-8','/DNDEBUG','/DJUCE_GLOBAL_MODULE_SETTINGS_INCLUDED=1','/DJUCE_STANDALONE_APPLICATION=1','/DJUCE_USE_CURL=0','/DJUCE_WEB_BROWSER=0','/DJUCE_USE_WINDOWS_MEDIA_FORMAT=0','/DJUCE_USE_MP3AUDIOFORMAT=1','/DJUCE_ASIO=1',"/I$modules", "/I$PSScriptRoot/vendor/signalsmith-stretch-main", "/I$PSScriptRoot/vendor/linear-main/include")
$sources = @('juce_core','juce_events','juce_audio_basics','juce_audio_formats','juce_audio_devices')
foreach ($module in $sources) {
  $source = Join-Path $modules "$module/$module.cpp"
  $object = Join-Path $objects "$module.obj"
  if (!(Test-Path $object)) {
    Write-Output "Compilando $module"
    & $compilerExe @flags '/c' $source "/Fo$object"
    if ($LASTEXITCODE -ne 0) { throw "Falha em $module" }
  }
}
foreach ($relative in @('juce_core/juce_core_CompilationTime.cpp','juce_core/juce_core_zlib.c','juce_audio_formats/juce_audio_formats_flac_1.c','juce_audio_formats/juce_audio_formats_flac_2.c','juce_audio_formats/juce_audio_formats_ogg.c','juce_audio_formats/juce_audio_formats_opus.c','juce_audio_formats/juce_audio_formats_opusfile.c')) {
  $source = Join-Path $modules $relative
  $object = Join-Path $objects (([IO.Path]::GetFileNameWithoutExtension($relative)) + '.obj')
  if (!(Test-Path $object)) {
    & $compilerExe @flags '/c' $source "/Fo$object"
    if ($LASTEXITCODE -ne 0) { throw "Falha em $relative" }
  }
}
& $compilerExe @flags '/c' (Join-Path $PSScriptRoot 'engine.cpp') "/Fo$objects/engine.obj"
if ($LASTEXITCODE -ne 0) { throw 'Falha no motor' }
& $compilerExe '/nologo' '/O2' '/MT' '/TC' '/D_USE_MATH_DEFINES' "/I$PSScriptRoot/vendor/libebur128" '/c' (Join-Path $PSScriptRoot 'vendor/libebur128/ebur128.c') "/Fo$objects/ebur128.obj"
if ($LASTEXITCODE -ne 0) { throw 'Falha na medição de loudness' }
$objectFiles = @((Get-ChildItem $objects -Filter '*.obj').FullName)
& $compilerExe '/nologo' @objectFiles "/Fe$out/zlplay-audio.exe" '/link' '/SUBSYSTEM:CONSOLE' 'psapi.lib' 'shell32.lib' 'winmm.lib' 'ole32.lib' 'oleaut32.lib' 'uuid.lib' 'version.lib' 'shlwapi.lib' 'ws2_32.lib' 'user32.lib' 'gdi32.lib' 'comdlg32.lib' 'imm32.lib' 'dwmapi.lib' 'wininet.lib' 'crypt32.lib' 'bcrypt.lib'
if ($LASTEXITCODE -ne 0) { throw 'Falha na ligação' }
Copy-Item (Join-Path $projectRoot 'app/Meuclick.wav') (Join-Path $out 'Meuclick.wav')
Copy-Item (Join-Path (Split-Path $modules -Parent) 'LICENSE.md') (Join-Path $out 'JUCE-LICENSE.md')
Copy-Item (Join-Path $PSScriptRoot 'vendor/signalsmith-stretch-main/LICENSE.txt') (Join-Path $out 'Signalsmith-Stretch-LICENSE.txt')
Copy-Item (Join-Path $PSScriptRoot 'vendor/linear-main/LICENSE.txt') (Join-Path $out 'Signalsmith-Linear-LICENSE.txt')
Copy-Item (Join-Path $PSScriptRoot 'README.md') (Join-Path $out 'README.md')
Copy-Item (Join-Path $PSScriptRoot 'vendor/libebur128/COPYING') (Join-Path $out 'libebur128-LICENSE.txt')
Copy-Item (Join-Path $PSScriptRoot 'vendor/libebur128/sys/queue.h') (Join-Path $out 'libebur128-queue-LICENSE.h')
Write-Output "Motor gerado em $out"
