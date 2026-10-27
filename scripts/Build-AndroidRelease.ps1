[CmdletBinding()]
param([switch]$Offline)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$repoRoot=Split-Path -Parent $PSScriptRoot
$privateRoot=Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) '1ByteLabs/ZLPlayMobile/signing'
$metadata=Get-Content -LiteralPath (Join-Path $privateRoot 'metadata.json') -Raw | ConvertFrom-Json
$encrypted=(Get-Content -LiteralPath (Join-Path $privateRoot 'password.dpapi') -Raw).Trim()
$secure=ConvertTo-SecureString -String $encrypted
$pointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try {
    $env:ZLPLAY_ANDROID_STORE_PASSWORD=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    $env:ZLPLAY_ANDROID_KEY_PASSWORD=$env:ZLPLAY_ANDROID_STORE_PASSWORD
    $env:ZLPLAY_ANDROID_KEYSTORE=Join-Path $privateRoot $metadata.keystore
    $env:ZLPLAY_ANDROID_ALIAS=$metadata.alias
    if(!$env:JAVA_HOME){$env:JAVA_HOME=Join-Path $env:ProgramFiles 'Android/Android Studio/jbr'}
    if(!$env:ANDROID_HOME){$env:ANDROID_HOME=Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'Android/Sdk'}
    $arguments=@('-p',(Join-Path $repoRoot 'android'),'assembleRelease','lintRelease','--no-daemon','--no-configuration-cache','--console=plain')
    if($Offline){$arguments+='--offline'}
    & (Join-Path $repoRoot 'android/gradlew.bat') @arguments
    if($LASTEXITCODE -ne 0){throw 'Falha ao compilar a distribuicao Android.'}
    $apk=Join-Path $repoRoot 'android/app/build/outputs/apk/release/app-release.apk'
    $buildTools=Get-ChildItem -LiteralPath (Join-Path $env:ANDROID_HOME 'build-tools') -Directory | Sort-Object Name -Descending | Select-Object -First 1
    $report=& (Join-Path $buildTools.FullName 'apksigner.bat') verify --verbose --print-certs $apk
    if($LASTEXITCODE -ne 0){throw 'A verificacao da assinatura falhou.'}
    $match=[regex]::Match(($report -join "`n"),'Signer #1 certificate SHA-256 digest: ([a-fA-F0-9]+)')
    if(!$match.Success -or $match.Groups[1].Value.ToLowerInvariant() -ne $metadata.certificateSha256){throw 'O certificado nao corresponde a chave permanente.'}
    & (Join-Path $buildTools.FullName 'zipalign.exe') -c -P 16 4 $apk
    if($LASTEXITCODE -ne 0){throw 'O alinhamento do APK falhou.'}
    $outputInfo=Get-Content -LiteralPath (Join-Path (Split-Path $apk) 'output-metadata.json') -Raw | ConvertFrom-Json
    if($outputInfo.applicationId -ne 'com.onebytelabs.zlplay.mobile'){throw 'Application ID inesperado.'}
    $version=$outputInfo.elements[0].versionName
    $output=Join-Path $repoRoot 'dist/android'
    New-Item -ItemType Directory -Force -Path $output | Out-Null
    $name='ZLPlay-Mobile-'+$version+'.apk'
    $published=Join-Path $output $name
    Copy-Item -LiteralPath $apk -Destination $published -Force
    $hash=(Get-FileHash -LiteralPath $published -Algorithm SHA256).Hash.ToLowerInvariant()
    [IO.File]::WriteAllText((Join-Path $output 'SHA256SUMS-Android.txt'),$hash+'  '+$name+"`n",[Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllLines((Join-Path $output 'SIGNATURE-Android.txt'),[string[]]$report,[Text.UTF8Encoding]::new($false))
    Write-Output ('APK assinado e validado: '+$published)
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    $secure.Dispose()
    $env:ZLPLAY_ANDROID_STORE_PASSWORD=$null;$env:ZLPLAY_ANDROID_KEY_PASSWORD=$null
    $env:ZLPLAY_ANDROID_KEYSTORE=$null;$env:ZLPLAY_ANDROID_ALIAS=$null
}
